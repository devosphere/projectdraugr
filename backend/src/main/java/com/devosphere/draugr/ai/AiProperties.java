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
     * Per-agent model ids. Each of the three agents runs on the model best suited to its job:
     * the Simulation Agent's narration is a high-frequency, low-stakes single sentence (fast/cheap
     * Haiku); the Architect proposes schema/data at authoring time and wants deep reasoning (Opus);
     * the Auditor summarizes consistency findings (Sonnet).
     */
    private String narrationModel = "claude-haiku-4-5";
    private String architectModel = "claude-opus-4-8";
    private String auditorModel = "claude-sonnet-4-6";
    /** The Procedure Interpreter (DR-0021): maps a miss to a sequence of existing processes. Mid-tier, read-only reasoning. */
    private String interpreterModel = "claude-sonnet-4-6";
    /** Independent QA critic for runtime-authored mechanics — deliberately a different model from the Architect. */
    private String qaModel = "claude-opus-4-8";
    /** Even with AI on, runtime authoring of NEW scoped mechanics stays off until this is set — interpretation of existing ones does not need it. */
    private boolean authoringEnabled = false;
    /** Author↔critic loop cap (DR-0021). */
    private int qaMaxRounds = 2;

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
    public String getNarrationModel() { return narrationModel; }
    public void setNarrationModel(String narrationModel) { this.narrationModel = narrationModel; }
    public String getArchitectModel() { return architectModel; }
    public void setArchitectModel(String architectModel) { this.architectModel = architectModel; }
    public String getAuditorModel() { return auditorModel; }
    public void setAuditorModel(String auditorModel) { this.auditorModel = auditorModel; }
    public String getInterpreterModel() { return interpreterModel; }
    public void setInterpreterModel(String interpreterModel) { this.interpreterModel = interpreterModel; }
    public String getQaModel() { return qaModel; }
    public void setQaModel(String qaModel) { this.qaModel = qaModel; }
    public boolean isAuthoringEnabled() { return authoringEnabled; }
    public void setAuthoringEnabled(boolean authoringEnabled) { this.authoringEnabled = authoringEnabled; }
    public int getQaMaxRounds() { return qaMaxRounds; }
    public void setQaMaxRounds(int qaMaxRounds) { this.qaMaxRounds = qaMaxRounds; }
    public long getMaxTokens() { return maxTokens; }
    public void setMaxTokens(long maxTokens) { this.maxTokens = maxTokens; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) { this.timeout = timeout; }
}
