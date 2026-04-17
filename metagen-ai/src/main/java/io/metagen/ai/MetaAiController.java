package io.metagen.ai;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * AI Assistant REST endpoint.
 *
 * <h2>Flow</h2>
 * <pre>
 *   1. POST /api/ai/generate
 *      { "description": "Je veux gérer des fournisseurs avec commandes et paiements" }
 *
 *   2. Claude analyses the description and returns a MetaProposal
 *
 *   3. The Studio UI presents the proposal:
 *      ┌─────────────────────────────────────────┐
 *      │ Proposition IA                          │
 *      │                                         │
 *      │ Entités détectées:                      │
 *      │  ● supplier (Fournisseur) — 4 champs    │
 *      │  ● purchase_order — 5 champs + workflow │
 *      │                                         │
 *      │ Workflow: order_lifecycle               │
 *      │  DRAFT → APPROVED → RECEIVED            │
 *      │                                         │
 *      │ [Modifier]  [Importer tout]             │
 *      └─────────────────────────────────────────┘
 *
 *   4. User clicks "Importer tout" → POST /api/studio/config/import (YAML)
 * </pre>
 *
 * <p>Only active when {@code metagen.ai.enabled=true}.</p>
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "metagen.ai.enabled", havingValue = "true")
@Tag(name = "AI Assistant", description = "Generate metadata proposals from natural language")
public class MetaAiController {

    private final MetaAiService aiService;

    @PostMapping("/generate")
    @Operation(
        summary = "Generate a schema proposal",
        description = """
            Send a natural language description of your business domain.
            The AI returns a structured proposal (entities, fields, workflows).
            Nothing is persisted — you review and confirm in the Studio.
            """)
    public MetaProposal generate(@RequestBody GenerateRequest req) {
        try {
            return aiService.generate(req.description());
        } catch (AiGenerationException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

    /**
     * @param description plain-text description of the business domain (any language, 10–2000 chars)
     */
    record GenerateRequest(
        @NotBlank
        @Size(min = 10, max = 2000, message = "Description must be between 10 and 2000 characters")
        String description
    ) {}
}
