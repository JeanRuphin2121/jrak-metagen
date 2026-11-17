package io.metagen.engine.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

/** YAML / JSON (de)serialisation shared by the importer and the exporter. */
public final class MetaYamlCodec {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Strict reader: unknown keys fail the import. */
    public static final YAMLMapper YAML = YAMLMapper.builder()
        .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
        .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
        .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)
        .build();

    private MetaYamlCodec() {}

    /** Parses the JSON stored in a text column; returns null for null/blank. */
    public static JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return JSON.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored configuration is not valid JSON: " + e.getOriginalMessage(), e);
        }
    }

    /** Serialises a node for storage in a text column; returns null for null/empty. */
    public static String toJson(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        try {
            return JSON.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialise condition: " + e.getOriginalMessage(), e);
        }
    }
}
