package io.metagen.engine.sql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlIdentifiersTest {

    @ParameterizedTest
    @ValueSource(strings = {"name", "data_person", "a1", "created_at"})
    void acceptsSafeIdentifiers(String id) {
        assertThat(SqlIdentifiers.isSafe(id)).isTrue();
        assertThat(SqlIdentifiers.quote(id)).isEqualTo("\"" + id + "\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1abc", "Name", "a-b", "a b", "a\"b", "a;drop", "x--", "é"})
    void rejectsUnsafeIdentifiers(String id) {
        assertThat(SqlIdentifiers.isSafe(id)).isFalse();
        assertThatThrownBy(() -> SqlIdentifiers.quote(id)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullAndTooLong() {
        assertThat(SqlIdentifiers.isSafe(null)).isFalse();
        assertThat(SqlIdentifiers.isSafe("a".repeat(64))).isFalse();
        assertThat(SqlIdentifiers.isSafe("a".repeat(63))).isTrue();
    }

    @Test
    void knowsSystemColumns() {
        assertThat(SqlIdentifiers.isSystemColumn("state")).isTrue();
        assertThat(SqlIdentifiers.isSystemColumn("deleted_at")).isTrue();
        assertThat(SqlIdentifiers.isSystemColumn("amount")).isFalse();
    }
}
