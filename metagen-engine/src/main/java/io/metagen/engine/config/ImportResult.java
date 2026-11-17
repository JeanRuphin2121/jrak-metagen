package io.metagen.engine.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What an import did (or, in dry-run mode, would do). */
public class ImportResult {

    private final Map<String, int[]> counters = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> entities = new ArrayList<>();
    private boolean dryRun;

    public ImportResult() {
        for (String kind : List.of("roles", "workflows", "entities", "fields", "permissions", "views")) {
            counters.put(kind, new int[2]);
        }
    }

    void created(String kind)   { counters.get(kind)[0]++; }
    void updated(String kind)   { counters.get(kind)[1]++; }
    void warn(String message)   { warnings.add(message); }
    void touchedEntity(String n) { if (!entities.contains(n)) entities.add(n); }
    void dryRun(boolean dryRun) { this.dryRun = dryRun; }

    public boolean isDryRun()             { return dryRun; }
    public List<String> getWarnings()     { return List.copyOf(warnings); }
    /** Entities present in the imported file, in file order. */
    public List<String> getEntities()     { return List.copyOf(entities); }
    public int createdCount(String kind)  { return counters.get(kind)[0]; }
    public int updatedCount(String kind)  { return counters.get(kind)[1]; }

    public String summary() {
        var parts = new ArrayList<String>();
        counters.forEach((kind, c) -> {
            if (c[0] + c[1] > 0) parts.add(kind + ": +" + c[0] + " ~" + c[1]);
        });
        return parts.isEmpty() ? "no change" : String.join(" | ", parts);
    }

    public Map<String, Object> toMap() {
        var out = new LinkedHashMap<String, Object>();
        out.put("dryRun", dryRun);
        counters.forEach((kind, c) -> out.put(kind, Map.of("created", c[0], "updated", c[1])));
        out.put("warnings", getWarnings());
        out.put("summary", summary());
        return out;
    }
}
