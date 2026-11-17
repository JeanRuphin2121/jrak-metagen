package io.metagen.engine.meta;

import io.metagen.core.domain.MetaEntity;
import io.metagen.core.domain.MetaField;
import io.metagen.engine.crud.ValueCoercer;
import io.metagen.engine.expr.FieldResolver;
import io.metagen.engine.sql.SqlIdentifiers;

import java.util.LinkedHashMap;
import java.util.Map;

/** Resolves condition fields against an entity's business fields and system columns. */
public final class EntityFieldResolver implements FieldResolver {

    private final Map<String, MetaField> fields = new LinkedHashMap<>();

    public EntityFieldResolver(MetaEntity entity) {
        entity.getFields().forEach(f -> fields.put(f.getName(), f));
    }

    @Override
    public boolean exists(String field) {
        return fields.containsKey(field) || SqlIdentifiers.isSystemColumn(field);
    }

    @Override
    public Object coerce(String field, Object raw) {
        MetaField meta = fields.get(field);
        if (meta != null) {
            if (ValueCoercer.isJsonType(meta.getType())) {
                throw new IllegalArgumentException("JSON fields cannot be compared");
            }
            return ValueCoercer.coerce(meta, raw);
        }
        if (SqlIdentifiers.isSystemColumn(field)) {
            return ValueCoercer.coerceSystem(field, raw);
        }
        throw new IllegalArgumentException("Unknown field '" + field + "'");
    }
}
