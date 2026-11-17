package io.metagen.engine.expr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.metagen.engine.sql.SqlIdentifiers;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Declarative boolean condition over a record — the only "business rule
 * language" stored in metadata.
 *
 * <p>The same condition has two back-ends:
 * <ul>
 *   <li>{@link #toSql} compiles it to a parameterised WHERE fragment
 *       (row-level security, list filters);</li>
 *   <li>{@link #test} evaluates it in memory against a loaded record
 *       (workflow transition guards).</li>
 * </ul>
 *
 * <p>JSON syntax:
 * <pre>
 *   {"field": "amount", "op": "lte", "value": 5000}
 *   {"field": "created_by", "op": "eq", "value": "$user"}
 *   {"and": [ ... ]}    {"or": [ ... ]}    {"not": { ... }}
 * </pre>
 * Operators: eq, ne, gt, gte, lt, lte, in, contains, is_null, not_null.
 * Variables: {@code $user} (current username), {@code $today} (current date).
 *
 * <p>There is deliberately no scripting: a condition can only compare fields
 * with literals, so it cannot execute code or inject SQL.
 */
public abstract class Condition {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_DEPTH = 8;

    public enum Op {
        EQ, NE, GT, GTE, LT, LTE, IN, CONTAINS, IS_NULL, NOT_NULL;

        static Op parse(String raw) {
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException e) {
                throw new ConditionException("Unknown operator '" + raw
                    + "' (allowed: eq, ne, gt, gte, lt, lte, in, contains, is_null, not_null)");
            }
        }
    }

    /** Mutable state shared while compiling one SQL statement. */
    public static final class SqlContext {
        private final MapSqlParameterSource params;
        private final FieldResolver resolver;
        private final Map<String, Object> variables;
        private final String prefix;
        private int counter = 0;

        public SqlContext(MapSqlParameterSource params, FieldResolver resolver,
                          Map<String, Object> variables, String prefix) {
            this.params = params;
            this.resolver = resolver;
            this.variables = variables;
            this.prefix = SqlIdentifiers.require(prefix);
        }

        String bind(Object value) {
            String name = prefix + "_" + (counter++);
            params.addValue(name, value);
            return ":" + name;
        }
    }

    // ── Parsing ───────────────────────────────────────────────────────────

    /** Parses a JSON string; returns {@code null} for a null/blank input (= no condition). */
    public static Condition parse(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return parse(JSON.readTree(json));
        } catch (JsonProcessingException e) {
            throw new ConditionException("Condition is not valid JSON: " + e.getOriginalMessage());
        }
    }

    public static Condition parse(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        return parse(node, 0);
    }

    private static Condition parse(JsonNode node, int depth) {
        if (depth > MAX_DEPTH) throw new ConditionException("Condition is nested too deeply");
        if (!node.isObject()) throw new ConditionException("A condition must be a JSON object");

        if (node.has("and")) return new Group(true, parseList(node.get("and"), depth));
        if (node.has("or"))  return new Group(false, parseList(node.get("or"), depth));
        if (node.has("not")) return new Not(parse(node.get("not"), depth + 1));

        if (!node.hasNonNull("field") || !node.hasNonNull("op")) {
            throw new ConditionException(
                "A condition needs \"field\" and \"op\", or one of \"and\" / \"or\" / \"not\"");
        }
        String field = node.get("field").asText();
        if (!SqlIdentifiers.isSafe(field)) {
            throw new ConditionException("Invalid field name '" + field + "'");
        }
        Op op = Op.parse(node.get("op").asText());
        JsonNode value = node.get("value");

        if (op == Op.IS_NULL || op == Op.NOT_NULL) {
            return new Comparison(field, op, null);
        }
        if (value == null || value.isNull()) {
            throw new ConditionException("Operator '" + op.name().toLowerCase(Locale.ROOT)
                + "' on '" + field + "' needs a \"value\" (use is_null / not_null to test for null)");
        }
        if (op == Op.IN) {
            if (!value.isArray() || value.isEmpty()) {
                throw new ConditionException("Operator 'in' on '" + field + "' needs a non-empty array");
            }
            List<Object> values = new ArrayList<>();
            value.forEach(v -> values.add(scalar(v, field)));
            return new Comparison(field, op, values);
        }
        return new Comparison(field, op, scalar(value, field));
    }

    private static List<Condition> parseList(JsonNode array, int depth) {
        if (array == null || !array.isArray() || array.isEmpty()) {
            throw new ConditionException("\"and\" / \"or\" need a non-empty array of conditions");
        }
        List<Condition> out = new ArrayList<>();
        array.forEach(n -> out.add(parse(n, depth + 1)));
        return out;
    }

    private static Object scalar(JsonNode v, String field) {
        if (v.isTextual()) return v.asText();
        if (v.isBoolean()) return v.asBoolean();
        if (v.isIntegralNumber()) return v.asLong();
        if (v.isNumber()) return v.decimalValue();
        throw new ConditionException("Value for '" + field + "' must be a string, number or boolean");
    }

    /** Combines conditions with AND, ignoring nulls; returns null when nothing is left. */
    public static Condition allOf(List<Condition> conditions) {
        List<Condition> nonNull = conditions.stream().filter(c -> c != null).toList();
        if (nonNull.isEmpty()) return null;
        return nonNull.size() == 1 ? nonNull.get(0) : new Group(true, nonNull);
    }

    /** Combines conditions with OR. */
    public static Condition anyOf(List<Condition> conditions) {
        if (conditions.isEmpty()) return null;
        return conditions.size() == 1 ? conditions.get(0) : new Group(false, conditions);
    }

    public static Condition eq(String field, Object value) {
        return new Comparison(SqlIdentifiers.require(field), Op.EQ, value);
    }

    // ── Contract ──────────────────────────────────────────────────────────

    /** Checks every referenced field exists and every literal converts to its column type. */
    public abstract void validate(FieldResolver resolver);

    /** Compiles to a parenthesised SQL fragment using bind parameters only. */
    public abstract String toSql(SqlContext ctx);

    /** Evaluates against an in-memory record (column name → JDBC value). */
    public abstract boolean test(Map<String, Object> record, FieldResolver resolver,
                                 Map<String, Object> variables);

    /** Field names referenced anywhere in this condition. */
    public abstract void collectFields(Set<String> into);

    // ── Variables ─────────────────────────────────────────────────────────

    static Object resolveVariable(Object raw, Map<String, Object> variables) {
        if (raw instanceof String s && s.startsWith("$")) {
            String name = s.substring(1);
            if ("today".equals(name)) return LocalDate.now();
            if (variables == null || !variables.containsKey(name)) {
                throw new ConditionException("Unknown variable '" + s + "' (allowed: $user, $today)");
            }
            return variables.get(name);
        }
        return raw;
    }

    private static boolean isVariable(Object raw) {
        return raw instanceof String s && s.startsWith("$");
    }

    // ── Implementations ───────────────────────────────────────────────────

    static final class Group extends Condition {
        private final boolean and;
        private final List<Condition> children;

        Group(boolean and, List<Condition> children) {
            this.and = and;
            this.children = List.copyOf(children);
        }

        @Override public void validate(FieldResolver resolver) {
            children.forEach(c -> c.validate(resolver));
        }

        @Override public String toSql(SqlContext ctx) {
            List<String> parts = children.stream().map(c -> c.toSql(ctx)).toList();
            return "(" + String.join(and ? " AND " : " OR ", parts) + ")";
        }

        @Override public boolean test(Map<String, Object> record, FieldResolver resolver,
                                      Map<String, Object> variables) {
            return and
                ? children.stream().allMatch(c -> c.test(record, resolver, variables))
                : children.stream().anyMatch(c -> c.test(record, resolver, variables));
        }

        @Override public void collectFields(Set<String> into) {
            children.forEach(c -> c.collectFields(into));
        }
    }

    static final class Not extends Condition {
        private final Condition inner;

        Not(Condition inner) { this.inner = inner; }

        @Override public void validate(FieldResolver resolver) { inner.validate(resolver); }

        @Override public String toSql(SqlContext ctx) {
            // COALESCE: a NULL comparison result counts as "false" before negation,
            // so SQL and in-memory evaluation agree.
            return "(NOT COALESCE(" + inner.toSql(ctx) + ", FALSE))";
        }

        @Override public boolean test(Map<String, Object> record, FieldResolver resolver,
                                      Map<String, Object> variables) {
            return !inner.test(record, resolver, variables);
        }

        @Override public void collectFields(Set<String> into) { inner.collectFields(into); }
    }

    static final class Comparison extends Condition {
        private final String field;
        private final Op op;
        private final Object value;   // scalar, List<Object> for IN, null for IS_NULL / NOT_NULL

        Comparison(String field, Op op, Object value) {
            this.field = field;
            this.op = op;
            this.value = value;
        }

        @Override public void validate(FieldResolver resolver) {
            if (!resolver.exists(field)) {
                throw new ConditionException("Unknown field '" + field + "' in condition");
            }
            if (op == Op.IS_NULL || op == Op.NOT_NULL || op == Op.CONTAINS) return;
            for (Object v : values()) {
                if (isVariable(v)) {
                    String name = ((String) v).substring(1);
                    if (!name.equals("user") && !name.equals("today")) {
                        throw new ConditionException(
                            "Unknown variable '" + v + "' (allowed: $user, $today)");
                    }
                    continue;
                }
                try {
                    resolver.coerce(field, v);
                } catch (IllegalArgumentException e) {
                    throw new ConditionException(
                        "Value '" + v + "' is not valid for field '" + field + "': " + e.getMessage());
                }
            }
        }

        private List<?> values() {
            return value instanceof List<?> list ? list : List.of(value);
        }

        private Object typed(Object raw, FieldResolver resolver, Map<String, Object> variables) {
            Object resolved = resolveVariable(raw, variables);
            return resolver.coerce(field, resolved);
        }

        @Override public String toSql(SqlContext ctx) {
            String col = SqlIdentifiers.quote(field);
            switch (op) {
                case IS_NULL:  return "(" + col + " IS NULL)";
                case NOT_NULL: return "(" + col + " IS NOT NULL)";
                case CONTAINS: {
                    String needle = String.valueOf(resolveVariable(value, ctx.variables));
                    return "(CAST(" + col + " AS TEXT) ILIKE " + ctx.bind("%" + escapeLike(needle) + "%") + ")";
                }
                case IN: {
                    List<String> binds = new ArrayList<>();
                    for (Object v : values()) {
                        binds.add(ctx.bind(typed(v, ctx.resolver, ctx.variables)));
                    }
                    return "(" + col + " IN (" + String.join(", ", binds) + "))";
                }
                default: {
                    String bind = ctx.bind(typed(value, ctx.resolver, ctx.variables));
                    return "(" + col + " " + sqlOperator() + " " + bind + ")";
                }
            }
        }

        private String sqlOperator() {
            switch (op) {
                case EQ:  return "=";
                case NE:  return "<>";
                case GT:  return ">";
                case GTE: return ">=";
                case LT:  return "<";
                case LTE: return "<=";
                default:  throw new IllegalStateException("No SQL operator for " + op);
            }
        }

        @Override public boolean test(Map<String, Object> record, FieldResolver resolver,
                                      Map<String, Object> variables) {
            Object actual = normalize(record.get(field));
            switch (op) {
                case IS_NULL:  return actual == null;
                case NOT_NULL: return actual != null;
                default: break;
            }
            // SQL semantics: any comparison with NULL is not true.
            if (actual == null) return false;

            if (op == Op.CONTAINS) {
                String needle = String.valueOf(resolveVariable(value, variables));
                return actual.toString().toLowerCase(Locale.ROOT)
                             .contains(needle.toLowerCase(Locale.ROOT));
            }
            if (op == Op.IN) {
                for (Object v : values()) {
                    if (compare(actual, normalize(typed(v, resolver, variables))) == 0) return true;
                }
                return false;
            }
            int cmp = compare(actual, normalize(typed(value, resolver, variables)));
            switch (op) {
                case EQ:  return cmp == 0;
                case NE:  return cmp != 0;
                case GT:  return cmp > 0;
                case GTE: return cmp >= 0;
                case LT:  return cmp < 0;
                case LTE: return cmp <= 0;
                default:  throw new IllegalStateException("Unhandled operator " + op);
            }
        }

        @Override public void collectFields(Set<String> into) { into.add(field); }
    }

    // ── In-memory comparison helpers ──────────────────────────────────────

    /** Brings JDBC values and coerced literals to a small set of comparable types. */
    static Object normalize(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal) return v;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        if (v instanceof java.sql.Date d) return d.toLocalDate();
        if (v instanceof java.sql.Time t) return t.toLocalTime();
        if (v instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (v instanceof OffsetDateTime odt) return odt.toInstant();
        if (v instanceof java.util.Date d) return d.toInstant();
        if (v instanceof UUID u) return u.toString();
        if (v instanceof Instant || v instanceof LocalDate
            || v instanceof java.time.LocalTime || v instanceof Boolean) return v;
        return v.toString();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static int compare(Object a, Object b) {
        if (b == null) return 1;
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) return x.compareTo(y);
        if (a.getClass() == b.getClass() && a instanceof Comparable c) return c.compareTo(b);
        return a.toString().compareTo(b.toString());
    }

    static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
