package io.metagen.engine.expr;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConditionTest {

    /** Minimal resolver: amount (decimal), title (text), created_by (text). */
    private static final FieldResolver RESOLVER = new FieldResolver() {
        @Override public boolean exists(String f) { return Set.of("amount", "title", "created_by").contains(f); }
        @Override public Object coerce(String f, Object raw) {
            if (f.equals("amount")) return new BigDecimal(raw.toString());
            return raw.toString();
        }
    };

    private static Map<String, Object> vars() { return Map.of("user", "alice"); }

    private String sql(Condition c, MapSqlParameterSource params) {
        return c.toSql(new Condition.SqlContext(params, RESOLVER, vars(), "f"));
    }

    @Test
    void parsesNullAndBlankAsNoCondition() {
        assertThat(Condition.parse((String) null)).isNull();
        assertThat(Condition.parse("  ")).isNull();
    }

    @Test
    void compilesToParameterisedSql_neverInliningValues() {
        Condition c = Condition.parse("{\"field\":\"title\",\"op\":\"eq\",\"value\":\"x' OR '1'='1\"}");
        var params = new MapSqlParameterSource();
        String sql = sql(c, params);
        assertThat(sql).isEqualTo("(\"title\" = :f_0)");
        assertThat(params.getValue("f_0")).isEqualTo("x' OR '1'='1");
    }

    @Test
    void resolvesUserVariable() {
        Condition c = Condition.parse("{\"field\":\"created_by\",\"op\":\"eq\",\"value\":\"$user\"}");
        var params = new MapSqlParameterSource();
        sql(c, params);
        assertThat(params.getValue("f_0")).isEqualTo("alice");
    }

    @Test
    void combinesAndOrNot() {
        Condition c = Condition.parse("""
            {"and":[{"field":"amount","op":"gte","value":10},
                    {"or":[{"field":"title","op":"eq","value":"a"},
                           {"not":{"field":"title","op":"is_null"}}]}]}""");
        String sql = sql(c, new MapSqlParameterSource());
        assertThat(sql).contains("AND").contains("OR").contains("NOT").contains("IS NULL");
    }

    @Test
    void inOperatorBindsEachValue() {
        Condition c = Condition.parse("{\"field\":\"title\",\"op\":\"in\",\"value\":[\"a\",\"b\"]}");
        var params = new MapSqlParameterSource();
        assertThat(sql(c, params)).isEqualTo("(\"title\" IN (:f_0, :f_1))");
    }

    @Test
    void evaluatesInMemory() {
        Condition c = Condition.parse("{\"field\":\"amount\",\"op\":\"lte\",\"value\":5000}");
        assertThat(c.test(Map.of("amount", new BigDecimal("4999.99")), RESOLVER, vars())).isTrue();
        assertThat(c.test(Map.of("amount", new BigDecimal("5000.01")), RESOLVER, vars())).isFalse();
    }

    @Test
    void comparisonWithNullIsNotTrue_likeSql() {
        Condition ne = Condition.parse("{\"field\":\"title\",\"op\":\"ne\",\"value\":\"a\"}");
        Map<String, Object> rec = new HashMap<>();
        rec.put("title", null);
        assertThat(ne.test(rec, RESOLVER, vars())).isFalse();
        Condition isNull = Condition.parse("{\"field\":\"title\",\"op\":\"is_null\"}");
        assertThat(isNull.test(rec, RESOLVER, vars())).isTrue();
    }

    @Test
    void containsIsCaseInsensitive() {
        Condition c = Condition.parse("{\"field\":\"title\",\"op\":\"contains\",\"value\":\"URGENT\"}");
        assertThat(c.test(Map.of("title", "an urgent task"), RESOLVER, vars())).isTrue();
    }

    @Test
    void validateRejectsUnknownFieldAndBadLiteral() {
        assertThatThrownBy(() -> Condition.parse("{\"field\":\"nope\",\"op\":\"eq\",\"value\":1}").validate(RESOLVER))
            .isInstanceOf(ConditionException.class).hasMessageContaining("nope");
        assertThatThrownBy(() -> Condition.parse("{\"field\":\"amount\",\"op\":\"eq\",\"value\":\"abc\"}").validate(RESOLVER))
            .isInstanceOf(ConditionException.class);
        assertThatThrownBy(() -> Condition.parse("{\"field\":\"title\",\"op\":\"eq\",\"value\":\"$hacker\"}").validate(RESOLVER))
            .isInstanceOf(ConditionException.class).hasMessageContaining("$hacker");
    }

    @Test
    void parseRejectsMalformedInput() {
        assertThatThrownBy(() -> Condition.parse("{not json")).isInstanceOf(ConditionException.class);
        assertThatThrownBy(() -> Condition.parse("{\"field\":\"a b\",\"op\":\"eq\",\"value\":1}"))
            .isInstanceOf(ConditionException.class);
        assertThatThrownBy(() -> Condition.parse("{\"field\":\"title\",\"op\":\"drop\",\"value\":1}"))
            .isInstanceOf(ConditionException.class);
        assertThatThrownBy(() -> Condition.parse("{\"field\":\"title\",\"op\":\"eq\"}"))
            .isInstanceOf(ConditionException.class);
        assertThatThrownBy(() -> Condition.parse("{\"and\":[]}")).isInstanceOf(ConditionException.class);
    }

    @Test
    void parseRejectsExcessiveNesting() {
        String json = "{\"field\":\"title\",\"op\":\"eq\",\"value\":\"a\"}";
        for (int i = 0; i < 12; i++) json = "{\"not\":" + json + "}";
        String deep = json;
        assertThatThrownBy(() -> Condition.parse(deep)).isInstanceOf(ConditionException.class);
    }

    @Test
    void collectsReferencedFields() {
        Condition c = Condition.parse("{\"or\":[{\"field\":\"title\",\"op\":\"is_null\"},{\"field\":\"amount\",\"op\":\"gt\",\"value\":1}]}");
        Set<String> fields = new java.util.TreeSet<>();
        c.collectFields(fields);
        assertThat(fields).containsExactly("amount", "title");
    }
}
