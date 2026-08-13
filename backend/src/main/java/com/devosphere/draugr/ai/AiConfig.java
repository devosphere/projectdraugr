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
            // Master is on and a key is present — report every agent's effective state (ON/OFF + model),
            // so "is the Architect actually running?" is answerable from the boot log, not by reading code.
            log.info("AI master ON — narrator {} ({}), interpreter {} ({}), architect {} ({}), qa {} ({}), auditor {} ({}).",
                    on(props.isNarrationActive()), props.getNarrationModel(),
                    on(props.isInterpreterActive()), props.getInterpreterModel(),
                    on(props.isAuthoringActive()), props.getArchitectModel(),
                    on(props.isQaActive()), props.getQaModel(),
                    on(props.isAuditorActive()), props.getAuditorModel());
            return new AnthropicLanguageModel(props);
        }
        // Master off or no key: a no-op model. The whole AI layer stays inert; narration is deterministic.
        log.info("AI master OFF — deterministic prose only (draugr.ai.enabled={}, key present={}).",
                props.isEnabled(), props.getApiKey() != null && !props.getApiKey().isBlank());
        return (model, system, user) -> Optional.empty();
    }

    private static String on(boolean active) { return active ? "ON" : "OFF"; }
}
