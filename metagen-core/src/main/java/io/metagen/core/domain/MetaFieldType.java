package io.metagen.core.domain;

/**
 * Supported field types in the Metagen metadata model.
 *
 * <p>Each type maps to a PostgreSQL column type and drives the UI component
 * rendered in the auto-generated forms.</p>
 *
 * <pre>
 * Type hierarchy:
 *
 *  Text ──── STRING, TEXT, RICH_TEXT, EMAIL, URL, PHONE
 *  Number ── INTEGER, LONG, DECIMAL
 *  Temporal  DATE, TIME, DATETIME
 *  Boolean   BOOLEAN
 *  Rich ──── FILE, IMAGE, JSON
 *  Selection ENUM, MULTI_ENUM
 *  Relation  REFERENCE
 * </pre>
 */
public enum MetaFieldType {

    // ── Text ────────────────────────────────────────────────────────
    /** Short text, maps to VARCHAR(255). UI: single-line input. */
    STRING,

    /** Long text, maps to TEXT. UI: textarea. */
    TEXT,

    /** Rich text (HTML/Markdown), maps to TEXT. UI: rich editor. */
    RICH_TEXT,

    /** Email address with format validation. UI: email input. */
    EMAIL,

    /** URL with format validation. UI: url input. */
    URL,

    /** Phone number. UI: phone input with optional mask. */
    PHONE,

    // ── Numbers ─────────────────────────────────────────────────────
    /** 32-bit integer, maps to INT. */
    INTEGER,

    /** 64-bit integer, maps to BIGINT. */
    LONG,

    /**
     * Fixed-point decimal, maps to NUMERIC.
     * Precision and scale are stored in {@code MetaField#options}.
     */
    DECIMAL,

    // ── Boolean ─────────────────────────────────────────────────────
    /** Boolean flag, maps to BOOLEAN. UI: toggle / checkbox. */
    BOOLEAN,

    // ── Temporal ────────────────────────────────────────────────────
    /** Calendar date (no time), maps to DATE. */
    DATE,

    /** Time of day (no date), maps to TIME. */
    TIME,

    /** Date + time with timezone, maps to TIMESTAMP WITH TIME ZONE. */
    DATETIME,

    // ── Rich types ──────────────────────────────────────────────────
    /** File attachment, stored as reference to file storage. */
    FILE,

    /** Image attachment. UI: image preview + uploader. */
    IMAGE,

    /** Arbitrary JSON object, maps to JSONB. */
    JSON,

    // ── Selection ───────────────────────────────────────────────────
    /**
     * Single value from a predefined list.
     * Allowed values stored in {@code MetaField#options} as a JSON array.
     */
    ENUM,

    /**
     * Multiple values from a predefined list, maps to JSONB array.
     * Allowed values stored in {@code MetaField#options} as a JSON array.
     */
    MULTI_ENUM,

    // ── Relation ────────────────────────────────────────────────────
    /**
     * Foreign-key reference to another {@link MetaEntity}.
     * The target entity is specified in {@code MetaField#options}
     * under the key {@code "targetEntity"}.
     */
    REFERENCE
}
