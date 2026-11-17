package io.metagen.engine.crud;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.metagen.core.domain.MetaField;
import io.metagen.core.domain.MetaFieldType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Converts loosely-typed JSON values into the Java type the JDBC driver needs
 * for each {@link MetaFieldType}, validating them on the way.
 *
 * <p>Without this step PostgreSQL rejects, for instance, a string bound to a
 * DATE or UUID column — and business validation (enum values, max length,
 * e-mail format) would be left to database errors.
 */
public final class ValueCoercer {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private ValueCoercer() {}

    /** JSON-typed columns are bound as text and cast to jsonb in SQL. */
    public static boolean isJsonType(MetaFieldType type) {
        return type == MetaFieldType.JSON || type == MetaFieldType.MULTI_ENUM;
    }

    /**
     * @return the typed value, or {@code null} for null / empty-string input
     * @throws IllegalArgumentException with a user-facing message when invalid
     */
    public static Object coerce(MetaField field, Object raw) {
        if (raw == null) return null;
        if (raw instanceof String s && s.isBlank()) return null;

        JsonNode options = field.getOptions();
        switch (field.getType()) {
            case STRING: {
                String s = asText(raw);
                int max = options != null && options.has("maxLength")
                    ? options.get("maxLength").asInt(255) : 255;
                if (s.length() > max) throw invalid("must be at most " + max + " characters");
                return s;
            }
            case EMAIL: {
                String s = bounded(asText(raw), 255);
                if (!EMAIL.matcher(s).matches()) throw invalid("must be a valid e-mail address");
                return s;
            }
            case URL:
            case PHONE:
                return bounded(asText(raw), 255);
            case TEXT:
            case RICH_TEXT:
            case FILE:
            case IMAGE:
                return asText(raw);
            case INTEGER:
                try {
                    return new BigDecimal(raw.toString().trim()).intValueExact();
                } catch (ArithmeticException | NumberFormatException e) {
                    throw invalid("must be a whole number");
                }
            case LONG:
                try {
                    return new BigDecimal(raw.toString().trim()).longValueExact();
                } catch (ArithmeticException | NumberFormatException e) {
                    throw invalid("must be a whole number");
                }
            case DECIMAL:
                try {
                    return new BigDecimal(raw.toString().trim());
                } catch (NumberFormatException e) {
                    throw invalid("must be a number");
                }
            case BOOLEAN:
                if (raw instanceof Boolean b) return b;
                if ("true".equalsIgnoreCase(raw.toString())) return Boolean.TRUE;
                if ("false".equalsIgnoreCase(raw.toString())) return Boolean.FALSE;
                throw invalid("must be true or false");
            case DATE:
                if (raw instanceof LocalDate d) return d;
                try {
                    String s = raw.toString().trim();
                    return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
                } catch (DateTimeParseException e) {
                    throw invalid("must be a date (YYYY-MM-DD)");
                }
            case TIME:
                if (raw instanceof LocalTime t) return t;
                try {
                    return LocalTime.parse(raw.toString().trim());
                } catch (DateTimeParseException e) {
                    throw invalid("must be a time (HH:MM)");
                }
            case DATETIME:
                return dateTime(raw);
            case ENUM: {
                String s = bounded(asText(raw), 100);
                List<String> allowed = allowedValues(options);
                if (!allowed.isEmpty() && !allowed.contains(s)) {
                    throw invalid("must be one of " + allowed);
                }
                return s;
            }
            case MULTI_ENUM: {
                if (!(raw instanceof Collection<?> items)) throw invalid("must be a list of values");
                List<String> allowed = allowedValues(options);
                List<String> out = new ArrayList<>();
                for (Object item : items) {
                    String s = String.valueOf(item);
                    if (!allowed.isEmpty() && !allowed.contains(s)) {
                        throw invalid("'" + s + "' is not one of " + allowed);
                    }
                    out.add(s);
                }
                return toJson(out);
            }
            case JSON:
                if (raw instanceof String s) {
                    try {
                        JSON.readTree(s);
                        return s;
                    } catch (JsonProcessingException e) {
                        throw invalid("must be valid JSON");
                    }
                }
                return toJson(raw);
            case REFERENCE:
                return uuid(raw);
            default:
                throw new IllegalStateException("Unsupported field type " + field.getType());
        }
    }

    /** Typed value for one of the engine-owned columns (used by conditions and filters). */
    public static Object coerceSystem(String column, Object raw) {
        if (raw == null) return null;
        switch (column) {
            case "id":
                return uuid(raw);
            case "created_at":
            case "updated_at":
            case "deleted_at":
                return dateTime(raw);
            default:
                return raw.toString();
        }
    }

    public static List<String> allowedValues(JsonNode options) {
        List<String> out = new ArrayList<>();
        if (options != null && options.has("values") && options.get("values").isArray()) {
            options.get("values").forEach(v -> out.add(v.asText()));
        }
        return out;
    }

    private static Object dateTime(Object raw) {
        if (raw instanceof OffsetDateTime odt) return odt;
        String s = raw.toString().trim();
        try {
            return OffsetDateTime.parse(s);
        } catch (DateTimeParseException ignored) {
            // fall through: accept local date-times and plain dates as UTC
        }
        try {
            return LocalDateTime.parse(s).atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDate.parse(s).atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw invalid("must be an ISO-8601 date-time");
        }
    }

    private static UUID uuid(Object raw) {
        if (raw instanceof UUID u) return u;
        try {
            return UUID.fromString(raw.toString().trim());
        } catch (IllegalArgumentException e) {
            throw invalid("must be a record id (UUID)");
        }
    }

    private static String asText(Object raw) {
        if (raw instanceof Collection || raw instanceof java.util.Map) {
            throw invalid("must be a text value");
        }
        return raw.toString();
    }

    private static String bounded(String s, int max) {
        if (s.length() > max) throw invalid("must be at most " + max + " characters");
        return s;
    }

    private static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw invalid("cannot be serialised to JSON");
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
