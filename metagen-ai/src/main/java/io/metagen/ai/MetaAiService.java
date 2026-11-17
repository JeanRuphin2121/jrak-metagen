package io.metagen.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * AI assistant service — generates metadata proposals from natural language.
 *
 * <p>Only active when {@code metagen.ai.enabled=true} and
 * {@code ANTHROPIC_API_KEY} is set.</p>
 *
 * <h2>How it works</h2>
 * <ol>
 *   <li>User sends a plain-text description of their business domain</li>
 *   <li>We send it to Claude with a detailed system prompt that constrains
 *       the output to a specific JSON schema</li>
 *   <li>Claude returns a JSON object matching {@link MetaProposal}</li>
 *   <li>We deserialise and return the proposal — nothing is persisted yet</li>
 *   <li>The user reviews the proposal in the Studio UI and confirms</li>
 *   <li>On confirmation, the Studio API creates the metadata normally</li>
 * </ol>
 *
 * <h2>Prompt engineering</h2>
 * The system prompt:
 * <ul>
 *   <li>Gives Claude the exact JSON schema it must produce</li>
 *   <li>Lists naming conventions (snake_case, UPPER_SNAKE_CASE)</li>
 *   <li>Instructs Claude to propose a workflow only when there is a clear lifecycle</li>
 *   <li>Forbids markdown — Claude must return raw JSON only</li>
 * </ul>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "metagen.ai.enabled", havingValue = "true")
@RequiredArgsConstructor
public class MetaAiService {

    private final ChatClient    chatClient;
    private final ObjectMapper  objectMapper;

    // ── System prompt ─────────────────────────────────────────────────

    private static final String SYSTEM_PROMPT = """
        You are a metadata schema designer for Metagen Platform, an open-source low-code platform.

        When the user describes a business domain in any language, you MUST respond with ONLY
        valid raw JSON (no markdown fences, no explanation before or after the JSON).

        Use EXACTLY this structure:

        {
          "entities": [
            {
              "name": "snake_case_name",
              "label": "Human Label (same language as user input)",
              "description": "optional brief description",
              "workflow": "workflow_name_if_applicable",
              "fields": [
                {
                  "name": "field_name",
                  "label": "Field Label",
                  "type": "ONE_OF_ALLOWED_TYPES",
                  "required": true,
                  "unique": false,
                  "searchable": true,
                  "options": {}
                }
              ]
            }
          ],
          "workflows": [
            {
              "name": "workflow_name",
              "label": "Workflow Label",
              "states": [
                { "name": "STATE_NAME", "label": "Label", "initial": true,  "terminal": false, "color": "gray" },
                { "name": "DONE",       "label": "Done",  "initial": false, "terminal": true,  "color": "green" }
              ],
              "transitions": [
                { "from": "STATE1", "to": "STATE2", "label": "Action Label", "requiredRole": null }
              ]
            }
          ],
          "explanation": "Brief explanation in the same language as the user input"
        }

        ALLOWED field types (use exact string):
          STRING, TEXT, RICH_TEXT, EMAIL, URL, PHONE,
          INTEGER, LONG, DECIMAL, BOOLEAN,
          DATE, TIME, DATETIME,
          FILE, IMAGE, JSON,
          ENUM, MULTI_ENUM, REFERENCE

        For REFERENCE fields, add: "options": { "targetEntity": "other_entity_name" }
        For ENUM fields, add: "options": { "values": ["VALUE1", "VALUE2"] }
        For DECIMAL fields, add: "options": { "precision": 10, "scale": 2 }

        NAMING RULES:
          - Entity names: snake_case (e.g. purchase_order)
          - Field names: snake_case (e.g. created_date)
          - State names: UPPER_SNAKE_CASE (e.g. PENDING_APPROVAL)
          - Workflow names: snake_case (e.g. order_lifecycle)

        DESIGN RULES:
          - Only propose a workflow if there is a clear lifecycle with multiple stages
          - Every entity should have at least an "id" equivalent (it is auto-generated — do NOT include it)
          - Include a "name" or "label" field on every entity for human identification
          - Propose REFERENCE fields for FK relationships between entities
          - Keep it pragmatic — no more than 10 fields per entity, no over-engineering
          - Response MUST be valid parseable JSON — no trailing commas, no comments
        """;

    // ── Public API ────────────────────────────────────────────────────

    /**
     * Generates a metadata proposal from a natural language description.
     *
     * @param description user's plain-text description (any language)
     * @return structured proposal ready for the Studio UI to display
     * @throws AiGenerationException if Claude returns invalid JSON or an error occurs
     */
    public MetaProposal generate(String description) {
        log.info("Generating schema proposal for: '{}'",
                 description.length() > 100 ? description.substring(0, 100) + "..." : description);

        String rawJson;
        try {
            rawJson = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(description)
                .call()
                .content();
        } catch (Exception e) {
            throw new AiGenerationException("Claude API call failed: " + e.getMessage(), e);
        }

        // Strip potential markdown fences if Claude added them despite instructions
        rawJson = stripMarkdownFences(rawJson);

        try {
            MetaProposal proposal = objectMapper.readValue(rawJson, MetaProposal.class);
            log.info("Generated proposal: {} entities, {} workflows",
                     proposal.getEntities().size(), proposal.getWorkflows().size());
            return proposal;
        } catch (Exception e) {
            log.error("Failed to parse AI response as JSON:\n{}", rawJson);
            throw new AiGenerationException(
                "AI returned invalid JSON — please retry or rephrase your description", e);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private String stripMarkdownFences(String text) {
        if (text == null) return "{}";
        String trimmed = text.trim();
        if (trimmed.startsWith("```json")) trimmed = trimmed.substring(7);
        else if (trimmed.startsWith("```"))   trimmed = trimmed.substring(3);
        if (trimmed.endsWith("```"))          trimmed = trimmed.substring(0, trimmed.length() - 3);
        return trimmed.trim();
    }
}
