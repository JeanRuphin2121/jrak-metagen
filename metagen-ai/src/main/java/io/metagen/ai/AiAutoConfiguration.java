package io.metagen.ai;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI configuration for the AI assistant module.
 *
 * <p>The {@link ChatClient} bean is only created when
 * {@code metagen.ai.enabled=true}, ensuring the module is a
 * no-op (zero overhead) when AI is not configured.</p>
 *
 * <h2>Required configuration</h2>
 * <pre>
 * metagen.ai.enabled=true
 * metagen.ai.model=claude-sonnet-4-6   # or claude-opus-4-6
 * ANTHROPIC_API_KEY=sk-ant-...
 * </pre>
 *
 * <p>Spring AI reads {@code ANTHROPIC_API_KEY} automatically via
 * {@code spring.ai.anthropic.api-key} — we map it in application.yml.</p>
 */
@Configuration
@ConditionalOnProperty(name = "metagen.ai.enabled", havingValue = "true")
public class AiAutoConfiguration {

    @Bean
    public ChatClient chatClient(AnthropicChatModel model) {
        return ChatClient.builder(model)
            .defaultOptions(
                org.springframework.ai.anthropic.AnthropicChatOptions.builder()
                    .withMaxTokens(4096)
                    .build()
            )
            .build();
    }
}
