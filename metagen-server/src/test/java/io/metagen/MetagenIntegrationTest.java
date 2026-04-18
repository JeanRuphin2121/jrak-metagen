package io.metagen;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.metagen.auth.dto.LoginRequest;
import io.metagen.meta.dto.CreateEntityRequest;
import io.metagen.meta.dto.CreateFieldRequest;
import io.metagen.core.domain.MetaFieldType;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration tests that run against a real PostgreSQL instance
 * (managed by Testcontainers) with Flyway migrations applied.
 *
 * <p>Flow tested:</p>
 * <ol>
 *   <li>Admin login → JWT token</li>
 *   <li>Create an entity via Studio API</li>
 *   <li>Add a field</li>
 *   <li>Deploy the table</li>
 *   <li>Insert a record via the dynamic CRUD API</li>
 *   <li>List records and verify the inserted record appears</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MetagenIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("metagen_test")
            .withUsername("metagen")
            .withPassword("metagen");

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc        mvc;
    @Autowired ObjectMapper   json;

    private static String jwt;
    private static final String ENTITY_NAME = "it_product";

    // ── 1. Login ──────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("Admin login returns a JWT token")
    void login_returnsToken() throws Exception {
        var req = new LoginRequest("admin", "changeme");

        String body = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.roles", hasItem("ADMIN")))
            .andReturn()
            .getResponse()
            .getContentAsString();

        jwt = json.readTree(body).get("token").asText();
    }

    // ── 2. Create entity ──────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("Create an entity via Studio API")
    void createEntity() throws Exception {
        var req = new CreateEntityRequest(
            ENTITY_NAME, "IT Product", "Products for IT", true, true, List.of());

        mvc.perform(post("/api/studio/entities")
                .header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value(ENTITY_NAME));
    }

    // ── 3. Add a field ────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("Add a STRING field to the entity")
    void addField() throws Exception {
        var req = new CreateFieldRequest(
            "sku", "SKU", MetaFieldType.STRING, true, true, true, null, null, 0);

        mvc.perform(post("/api/studio/entities/" + ENTITY_NAME + "/fields")
                .header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.fields[?(@.name == 'sku')]").exists());
    }

    // ── 4. Deploy table ───────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("Deploy creates the data table")
    void deployTable() throws Exception {
        mvc.perform(post("/api/studio/entities/" + ENTITY_NAME + "/deploy")
                .header("Authorization", "Bearer " + jwt))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.action").value("CREATED"));
    }

    // ── 5. Insert a record ────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("Insert a record via the dynamic CRUD API")
    void insertRecord() throws Exception {
        var payload = json.createObjectNode();
        payload.put("sku", "LAPTOP-001");

        mvc.perform(post("/api/data/" + ENTITY_NAME)
                .header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(payload)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.sku").value("LAPTOP-001"))
            .andExpect(jsonPath("$.id").isNotEmpty());
    }

    // ── 6. List records ───────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("List records returns the inserted record")
    void listRecords() throws Exception {
        mvc.perform(get("/api/data/" + ENTITY_NAME)
                .header("Authorization", "Bearer " + jwt))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.content[0].sku").value("LAPTOP-001"));
    }
}
