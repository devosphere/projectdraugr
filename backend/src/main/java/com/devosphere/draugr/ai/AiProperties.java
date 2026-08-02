package com.devosphere.draugr.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for the optional AI layer (the Simulation Agent's narration voice, and later
 * the Architect and Auditor). Everything here is off by default: with {@code enabled=false} or
 * no API key, the game runs on deterministic prose alone and never touches the network.
 *
 * <p>The API key comes only from configuration/environment and is never committed or logged.
 * See docs/architecture/three-ai-integration.md for the runtime contract.
 */
@ConfigurationProperties(prefix = "draugr.ai")
public class AiProperties {

    /** Master switch. Off ⇒ pure deterministic narration; no model client is constructed. */
    private boolean enabled = false;

    /** Anthropic API key. Supplied via env (DRAUGR_AI_API_KEY / ANTHROPIC_API_KEY); never committed. */
    private String apiKey = "";

    /**
     * Model id. Defaults to Anthropic's standing recommendation; we do not silently downgrade.
     * For high-frequency, low-stakes narration refinement, an operator may prefer
     * {@code claude-haiku-4-5} for latency/cost — set DRAUGR_AI_MODEL to choose.
     */
    private String model = "claude-opus-5";

    /** Output cap. Generous so a one- or two-sentence reply is never truncated. */
    private long maxTokens = 1024;

    /** Per-call wall-clock bound. A model that hangs must not hang the action loop. */
    private Duration timeout = Duration.ofSeconds(20);

    /** True only when the feature is switched on AND a non-blank key is present. */
    public boolean isUsable() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public long getMaxTokens() { return maxTokens; }
    public void setMaxTokens(long maxTokens) { this.maxTokens = maxTokens; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) { this.timeout = timeout; }
}
