package com.devosphere.draugr.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

/**
 * Wires the single {@link LanguageModel} bean. When the feature is usable (enabled + key present)
 * that is the real Anthropic-backed client; otherwise it is a no-op that always returns empty, so
 * the rest of the app depends on the seam, not on whether AI is switched on.
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    public LanguageModel languageModel(AiProperties props) {
        if (props.isUsable()) {
            log.info("AI narration enabled — model '{}'.", props.getModel());
            return new AnthropicLanguageModel(props);
        }
        // Disabled or unconfigured: a no-op model. Narration stays fully deterministic.
        log.info("AI narration disabled (draugr.ai.enabled={}, key present={}). Using deterministic prose only.",
                props.isEnabled(), props.getApiKey() != null && !props.getApiKey().isBlank());
        return (system, user) -> Optional.empty();
    }
}
