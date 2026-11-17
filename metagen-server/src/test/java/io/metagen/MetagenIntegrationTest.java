package io.metagen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end scenario on a real PostgreSQL: Flyway migrations, security,
 * YAML import, table deployment, RBAC with row-level security, guarded
 * workflow transitions and audit history.
 *
 * <p>Database: set {@code METAGEN_TEST_DB_URL} (+ {@code _USER}, {@code _PASSWORD})
 * to use an existing server — the schema is wiped first, so point it at a
 * throw-away database. Without it, a PostgreSQL Testcontainer is started.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MetagenIntegrationTest {

    private static final String EXTERNAL_URL = System.getenv("METAGEN_TEST_DB_URL");
    private static PostgreSQLContainer<?> container;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        if (EXTERNAL_URL != null && !EXTERNAL_URL.isBlank()) {
            registry.add("spring.datasource.url", () -> EXTERNAL_URL);
            registry.add("spring.datasource.username", () -> env("METAGEN_TEST_DB_USER", "metagen"));
            registry.add("spring.datasource.password", () -> env("METAGEN_TEST_DB_PASSWORD", "metagen"));
            registry.add("spring.flyway.clean-disabled", () -> "false");
        } else {
            container = new PostgreSQLContainer<>("postgres:16-alpine");
            container.start();
            registry.add("spring.datasource.url", container::getJdbcUrl);
            registry.add("spring.datasource.username", container::getUsername);
            registry.add("spring.datasource.password", container::getPassword);
        }
        registry.add("metagen.jwt.secret", () -> "integration-test-secret-at-least-32-bytes-long");
        registry.add("spring.security.user.password", () -> "admin-test-pwd");
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? fallback : v;
    }

    /** Wipes the schema before migrating, so a reused external database starts clean. */
    @org.springframework.boot.test.context.TestConfiguration
    static class CleanSchema {
        @org.springframework.context.annotation.Bean
        org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy cleanFirst() {
            return flyway -> { flyway.clean(); flyway.migrate(); };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private static String admin, alice, bob, boss, director;
    private static String aliceRequestId, bobRequestId;

    // ── helpers ───────────────────────────────────────────────────────────

    private String login(String user, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", user, "password", password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private ResultActions send(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(req);
    }

    private JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private void createUser(String name, String... roles) throws Exception {
        send(post("/api/studio/users"), admin,
            Map.of("username", name, "email", name + "@test.io", "password", "password-" + name,
                   "roles", java.util.List.of(roles)))
            .andExpect(status().is2xxSuccessful());
    }

    // ── 1. Authentication & authorization ─────────────────────────────────

    @Test @Order(1) @DisplayName("Admin can log in; wrong password and missing token are refused")
    void authentication() throws Exception {
        admin = login("admin", "admin-test-pwd");
        send(post("/api/auth/login"), null, Map.of("username", "admin", "password", "nope"))
            .andExpect(status().is4xxClientError());
        send(get("/api/studio/entities"), null, null).andExpect(status().isUnauthorized());
        send(get("/api/data/purchase_request"), null, null).andExpect(status().isUnauthorized());
        send(get("/api/me"), admin, null)
            .andExpect(jsonPath("$.username").value("admin")).andExpect(jsonPath("$.admin").value(true));
    }

    // ── 2. Config as code ─────────────────────────────────────────────────

    @Test @Order(2) @DisplayName("YAML dry-run validates without writing anything")
    void importDryRun() throws Exception {
        String yaml = Files.readString(Path.of("..", "config", "demo-purchase-requests.yml"));
        mvc.perform(post("/api/studio/config/import?dryRun=true").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.TEXT_PLAIN).content(yaml))
            .andExpect(status().isOk()).andExpect(jsonPath("$.dryRun").value(true));
        send(get("/api/studio/entities/purchase_request"), admin, null).andExpect(status().isNotFound());
    }

    @Test @Order(3) @DisplayName("YAML import with deploy creates roles, workflow, entities and tables")
    void importAndDeploy() throws Exception {
        String yaml = Files.readString(Path.of("..", "config", "demo-purchase-requests.yml"));
        mvc.perform(post("/api/studio/config/import?deploy=true").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.TEXT_PLAIN).content(yaml))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deployed", hasSize(2)));
        send(get("/api/studio/entities/purchase_request"), admin, null)
            .andExpect(status().isOk()).andExpect(jsonPath("$.workflowName").value("purchase_flow"));
    }

    @Test @Order(4) @DisplayName("Invalid YAML is rejected atomically (unknown field in a row filter)")
    void importInvalid() throws Exception {
        String bad = "entities:\n  - name: broken\n    label: Broken\n    fields:\n      - {name: a, label: A, type: STRING}\n"
                   + "    permissions:\n      - {role: ADMIN, canRead: true, rowFilter: {field: ghost, op: eq, value: 1}}\n";
        mvc.perform(post("/api/studio/config/import").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.TEXT_PLAIN).content(bad))
            .andExpect(status().isBadRequest());
        send(get("/api/studio/entities/broken"), admin, null).andExpect(status().isNotFound());
    }

    @Test @Order(5) @DisplayName("Re-importing the same YAML is idempotent and export round-trips")
    void idempotentAndExport() throws Exception {
        String yaml = Files.readString(Path.of("..", "config", "demo-purchase-requests.yml"));
        mvc.perform(post("/api/studio/config/import").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.TEXT_PLAIN).content(yaml)).andExpect(status().isOk());
        String exported = mvc.perform(get("/api/studio/config/export").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(exported).contains("purchase_flow", "purchase_request", "condition");
        // Exporting the exported file and importing it again must not fail or change anything.
        mvc.perform(post("/api/studio/config/import?dryRun=true").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.TEXT_PLAIN).content(exported)).andExpect(status().isOk());
    }

    // ── 3. Users, studio protection ───────────────────────────────────────

    @Test @Order(6) @DisplayName("Business users are created; non-admins cannot reach the Studio")
    void usersAndStudioProtection() throws Exception {
        createUser("alice", "REQUESTER");
        createUser("bob", "REQUESTER");
        createUser("boss", "MANAGER");
        createUser("dirk", "DIRECTOR");
        alice = login("alice", "password-alice");
        bob = login("bob", "password-bob");
        boss = login("boss", "password-boss");
        director = login("dirk", "password-dirk");

        send(get("/api/studio/entities"), alice, null).andExpect(status().isForbidden());
        mvc.perform(post("/api/studio/config/import").header("Authorization", "Bearer " + alice)
                .contentType(MediaType.TEXT_PLAIN).content("roles: []")).andExpect(status().isForbidden());
        send(get("/api/studio/users"), boss, null).andExpect(status().isForbidden());
    }

    // ── 4. Validation ─────────────────────────────────────────────────────

    @Test @Order(7) @DisplayName("Validation: bad types, missing required fields and unknown fields are 400s")
    void validation() throws Exception {
        send(post("/api/data/purchase_request"), alice, Map.of("amount", 10)).andExpect(status().isBadRequest());           // title required
        send(post("/api/data/purchase_request"), alice, Map.of("title", "x", "amount", "abc")).andExpect(status().isBadRequest());
        send(post("/api/data/purchase_request"), alice, Map.of("title", "x", "amount", 1, "category", "WEAPONS")).andExpect(status().isBadRequest());
        send(post("/api/data/purchase_request"), alice, Map.of("title", "x", "amount", 1, "nope", 1)).andExpect(status().isBadRequest());
        send(get("/api/data/does_not_exist"), alice, null).andExpect(status().isNotFound());
    }

    // ── 5. CRUD + RBAC ────────────────────────────────────────────────────

    @Test @Order(8) @DisplayName("Requesters create requests; records start in the workflow's initial state")
    void create() throws Exception {
        JsonNode a = read(send(post("/api/data/purchase_request"), alice,
            Map.of("title", "Laptop", "amount", 1200, "category", "IT"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.state").value("DRAFT"))
            .andExpect(jsonPath("$.created_by").value("alice")));
        aliceRequestId = a.get("id").asText();
        bobRequestId = read(send(post("/api/data/purchase_request"), bob,
            Map.of("title", "Chairs", "amount", 8000, "category", "OFFICE"))
            .andExpect(status().isCreated())).get("id").asText();
    }

    @Test @Order(9) @DisplayName("Row-level security: a requester sees only their own rows (list, get, update)")
    void rowLevelSecurity() throws Exception {
        send(get("/api/data/purchase_request"), alice, null)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].title").value("Laptop"));
        send(get("/api/data/purchase_request/" + bobRequestId), alice, null).andExpect(status().isNotFound());
        send(put("/api/data/purchase_request/" + bobRequestId), alice, Map.of("title", "hijack")).andExpect(status().isNotFound());
        // Alice may not delete at all, so the permission check answers before any row lookup.
        send(delete("/api/data/purchase_request/" + bobRequestId), alice, null).andExpect(status().isForbidden());
        // Managers and directors have no row filter.
        send(get("/api/data/purchase_request"), boss, null).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test @Order(10) @DisplayName("Field masks: hidden fields are never returned; hidden/readonly fields are ignored on write")
    void fieldMasks() throws Exception {
        // The manager sets the internal note, a field the requester can neither read nor write.
        send(put("/api/data/purchase_request/" + aliceRequestId), boss, Map.of("internal_note", "checked"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.internal_note").value("checked"));
        send(get("/api/data/purchase_request/" + aliceRequestId), alice, null)
            .andExpect(status().isOk()).andExpect(jsonPath("$.internal_note").doesNotExist());
        send(get("/api/data/purchase_request"), alice, null)
            .andExpect(jsonPath("$.content[0].internal_note").doesNotExist());

        // Alice tries to overwrite it, the manager tries to change a readonly field: both are ignored.
        send(put("/api/data/purchase_request/" + aliceRequestId), alice, Map.of("internal_note", "tampered"))
            .andExpect(status().isOk());
        send(put("/api/data/purchase_request/" + aliceRequestId), boss, Map.of("amount", 1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(1200.0));
        send(get("/api/data/purchase_request/" + aliceRequestId), boss, null)
            .andExpect(jsonPath("$.internal_note").value("checked"));

        // A hidden field cannot be used as a filter to probe its content.
        send(get("/api/data/purchase_request?f.internal_note=checked"), alice, null)
            .andExpect(status().isBadRequest());
    }

    @Test @Order(11) @DisplayName("Permissions: a requester cannot delete; supplier is read-only for requesters")
    void permissions() throws Exception {
        send(delete("/api/data/purchase_request/" + aliceRequestId), alice, null).andExpect(status().isForbidden());
        send(post("/api/data/supplier"), alice, Map.of("name", "ACME")).andExpect(status().isForbidden());
        send(post("/api/data/supplier"), boss, Map.of("name", "ACME")).andExpect(status().isCreated());
        send(post("/api/data/supplier"), boss, Map.of("name", "ACME")).andExpect(status().isConflict());      // unique
        send(get("/api/data/supplier"), alice, null).andExpect(status().isOk());
    }

    @Test @Order(12) @DisplayName("Runtime metadata exposes only what the user may see")
    void runtimeMetadata() throws Exception {
        JsonNode meta = read(send(get("/api/meta/entities/purchase_request"), alice, null).andExpect(status().isOk()));
        assertThat(meta.get("fields").findValuesAsText("name")).doesNotContain("internal_note");
        assertThat(meta.get("access").get("canDelete").asBoolean()).isFalse();
        assertThat(meta.get("states")).hasSize(4);
    }

    // ── 6. Workflow ───────────────────────────────────────────────────────

    private JsonNode transitions(String token, String id) throws Exception {
        return read(send(get("/api/data/purchase_request/" + id + "/transitions"), token, null).andExpect(status().isOk()));
    }

    private String transitionId(JsonNode available, String label) {
        for (JsonNode t : available) if (t.get("label").asText().equals(label)) return t.get("id").asText();
        throw new AssertionError("No transition '" + label + "' in " + available);
    }

    @Test @Order(13) @DisplayName("Workflow: submit, guarded approval by amount, role checks, history")
    void workflow() throws Exception {
        // Alice submits her 1 200 request.
        String submit = transitionId(transitions(alice, aliceRequestId), "Submit");
        send(post("/api/data/purchase_request/" + aliceRequestId + "/transition/" + submit), alice, Map.of("comment", "please"))
            .andExpect(status().isOk());

        // A requester cannot approve; the manager sees Approve (manager) for 1 200.
        assertThat(transitions(alice, aliceRequestId)).isEmpty();
        String approve = transitionId(transitions(boss, aliceRequestId), "Approve (manager)");
        send(post("/api/data/purchase_request/" + aliceRequestId + "/transition/" + approve), alice, null)
            .andExpect(status().isForbidden());
        send(post("/api/data/purchase_request/" + aliceRequestId + "/transition/" + approve), boss, null)
            .andExpect(status().isOk());
        send(get("/api/data/purchase_request/" + aliceRequestId), alice, null).andExpect(jsonPath("$.state").value("APPROVED"));

        // Terminal state: nothing more is available, and replaying the transition fails.
        assertThat(transitions(boss, aliceRequestId)).isEmpty();
        send(post("/api/data/purchase_request/" + aliceRequestId + "/transition/" + approve), boss, null)
            .andExpect(status().is4xxClientError());

        // Bob's 8 000 request exceeds the manager's limit: only the director can approve.
        String bobSubmit = transitionId(transitions(bob, bobRequestId), "Submit");
        send(post("/api/data/purchase_request/" + bobRequestId + "/transition/" + bobSubmit), bob, null).andExpect(status().isOk());
        JsonNode forBoss = transitions(boss, bobRequestId);
        assertThat(forBoss.findValuesAsText("label")).doesNotContain("Approve (manager)").contains("Reject (manager)");
        String directorApprove = transitionId(transitions(director, bobRequestId), "Approve (director)");
        send(post("/api/data/purchase_request/" + bobRequestId + "/transition/" + directorApprove), director, null)
            .andExpect(status().isOk());

        // Audit trail.
        JsonNode history = read(send(get("/api/data/purchase_request/" + aliceRequestId + "/history"), alice, null)
            .andExpect(status().isOk()));
        assertThat(history).hasSize(2);
        assertThat(history.findValuesAsText("username")).contains("alice", "boss");
    }

    @Test @Order(14) @DisplayName("Terminal states freeze the record; the state column cannot be forged")
    void terminalStateLocksTheRecord() throws Exception {
        // Approved requests can no longer be edited or deleted, even by the director who approved them…
        send(put("/api/data/purchase_request/" + aliceRequestId), alice, Map.of("title", "tampered"))
            .andExpect(status().isUnprocessableEntity());
        send(put("/api/data/purchase_request/" + bobRequestId), director, Map.of("state", "DRAFT"))
            .andExpect(status().isUnprocessableEntity());
        send(delete("/api/data/purchase_request/" + bobRequestId), director, null)
            .andExpect(status().isUnprocessableEntity());
        send(get("/api/data/purchase_request/" + bobRequestId), director, null)
            .andExpect(jsonPath("$.state").value("APPROVED"));

        // …but ADMIN keeps full control, and even then the state column is not writable.
        send(put("/api/data/purchase_request/" + bobRequestId), admin, Map.of("state", "DRAFT", "justification", "fixed by admin"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("APPROVED"))
            .andExpect(jsonPath("$.justification").value("fixed by admin"));
    }

    // ── 7. Injection attempts ─────────────────────────────────────────────

    @Test @Order(15) @DisplayName("SQL injection through sort, filter and search is neutralised")
    void sqlInjection() throws Exception {
        // An unknown sort column falls back to the default order — it is never put in the SQL.
        send(get("/api/data/purchase_request?sort=title;DROP TABLE data_purchase_request,asc"), director, null)
            .andExpect(status().isOk());
        send(get("/api/data/purchase_request?f.title=' OR '1'='1"), director, null)
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        send(get("/api/data/purchase_request?search=%27;DROP TABLE x;--"), director, null).andExpect(status().isOk());
        send(get("/api/data/purchase_request?f.ghost=1"), director, null).andExpect(status().isBadRequest());
        send(get("/api/data/purchase_request"), director, null)
            .andExpect(jsonPath("$.totalElements").value(2));   // table still there, data intact
    }

    @Test @Order(16) @DisplayName("Studio refuses dangerous or malformed definitions")
    void studioValidation() throws Exception {
        ObjectNode entity = json.createObjectNode().put("name", "x; drop table y").put("label", "Bad");
        send(post("/api/studio/entities"), admin, entity).andExpect(status().isBadRequest());
        entity.put("name", "id_clash");
        send(post("/api/studio/entities"), admin, entity).andExpect(status().isCreated());
        send(post("/api/studio/entities/id_clash/fields"), admin,
            Map.of("name", "state", "label", "State", "type", "STRING")).andExpect(status().isBadRequest());  // reserved
        send(post("/api/studio/entities/id_clash/fields"), admin,
            Map.of("name", "kind", "label", "Kind", "type", "ENUM")).andExpect(status().isBadRequest());      // ENUM without values
        send(post("/api/studio/entities/purchase_request/fields"), admin,
            Map.of("name", "title", "label", "Dup", "type", "STRING")).andExpect(status().isBadRequest());
    }

    @Test @Order(17) @DisplayName("Soft delete hides the row; deploy is idempotent and additive")
    void softDeleteAndRedeploy() throws Exception {
        String id = read(send(post("/api/data/supplier"), director, Map.of("name", "Temp")).andExpect(status().isCreated()))
            .get("id").asText();
        send(delete("/api/data/supplier/" + id), director, null).andExpect(status().isNoContent());
        send(get("/api/data/supplier/" + id), director, null).andExpect(status().isNotFound());

        send(post("/api/studio/entities/supplier/fields"), admin,
            Map.of("name", "phone", "label", "Phone", "type", "PHONE")).andExpect(status().is2xxSuccessful());
        send(post("/api/studio/entities/supplier/deploy"), admin, null)
            .andExpect(status().isOk()).andExpect(jsonPath("$.action").value("MIGRATED"))
            .andExpect(jsonPath("$.addedColumns", hasSize(1)));
        send(post("/api/studio/entities/supplier/deploy"), admin, null)
            .andExpect(status().isOk()).andExpect(jsonPath("$.addedColumns", hasSize(0)));
        UUID.fromString(id);
    }
}
