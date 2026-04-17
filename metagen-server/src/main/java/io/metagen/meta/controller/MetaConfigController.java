package io.metagen.meta.controller;

import io.metagen.engine.config.MetaYamlExporter;
import io.metagen.engine.config.MetaYamlImporter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Studio REST API — export and import the platform configuration as YAML.
 *
 * <h2>Typical CI/CD usage</h2>
 * <pre>
 * # Export current config to a file (versionned in Git)
 * curl -H "Authorization: Bearer $TOKEN" \
 *      https://api.myapp.com/api/studio/config/export \
 *      > config/metagen-config.yml
 *
 * # Import config on a fresh environment
 * curl -X POST -H "Authorization: Bearer $TOKEN" \
 *      -H "Content-Type: text/plain" \
 *      --data-binary @config/metagen-config.yml \
 *      https://api.newenv.com/api/studio/config/import
 * </pre>
 */
@RestController
@RequestMapping("/api/studio/config")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Studio API — Config", description = "Export / import configuration as YAML (ADMIN only)")
public class MetaConfigController {

    private final MetaYamlExporter exporter;
    private final MetaYamlImporter importer;

    @GetMapping(value = "/export", produces = "text/yaml")
    @Operation(
        summary = "Export full configuration as YAML",
        description = "Returns all entities, workflows, roles, and permissions as a versionnable YAML string.")
    public ResponseEntity<String> export() {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/yaml;charset=UTF-8"))
            .body(exporter.exportAll());
    }

    @GetMapping(value = "/export/{entityName}", produces = "text/yaml")
    @Operation(summary = "Export a single entity configuration as YAML")
    public ResponseEntity<String> exportEntity(@PathVariable String entityName) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/yaml;charset=UTF-8"))
            .body(exporter.exportEntity(entityName));
    }

    @PostMapping(value = "/import", consumes = "text/plain")
    @Operation(
        summary = "Import configuration from YAML",
        description = "Upserts entities, workflows, roles, and permissions. Non-destructive — never deletes.")
    public Map<String, Object> importConfig(@RequestBody String yaml) {
        MetaYamlImporter.ImportResult result = importer.importYaml(yaml);
        return result.toMap();
    }
}
