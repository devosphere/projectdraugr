package com.devosphere.draugr.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * {@link LanguageModel} backed by the official Anthropic SDK. Constructed only when the feature is
 * usable (enabled + key present) — see {@link AiConfig}.
 *
 * <p>Every call is wrapped so a transport error, timeout, refusal, or blank completion becomes an
 * empty result rather than an exception in the action loop: a model outage degrades voice, never
 * correctness. The API key is never logged.
 */
public class AnthropicLanguageModel implements LanguageModel {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLanguageModel.class);

    private final AnthropicClient client;
    private final AiProperties props;

    public AnthropicLanguageModel(AiProperties props) {
        this.props = props;
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(props.getApiKey())
                .timeout(props.getTimeout())
                .build();
    }

    @Override
    public Optional<String> generate(String model, String system, String user) {
        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(props.getMaxTokens())
                    .system(system)
                    .addUserMessage(user)
                    .build();
            Message response = client.messages().create(params);
            String text = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(t -> t.text())
                    .reduce("", (a, b) -> a + b)
                    .trim();
            return text.isBlank() ? Optional.empty() : Optional.of(text);
        } catch (Exception failure) {
            // Never surface a model problem into the game loop — fall back to deterministic prose. Name the model in
            // the log, though (#244): an unknown/stale model id fails here and otherwise looks identical to a plain
            // "nothing happened", so the model that failed must be visible to whoever is enabling the pipeline.
            log.warn("AI call to model '{}' failed; using deterministic prose instead: {}", model, failure.toString());
            return Optional.empty();
        }
    }
}
