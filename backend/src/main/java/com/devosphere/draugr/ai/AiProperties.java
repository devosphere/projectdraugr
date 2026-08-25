package com.devosphere.draugr.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for the AI layer. The design is a <b>master switch over per-agent switches</b>:
 * {@link #enabled} is the master, and every agent (narrator, interpreter, architect, auditor, QA)
 * has its own on/off flag. <b>The master defaults OFF</b> — a present API key is necessary but not
 * sufficient, so the AI never activates (and never spends tokens or bypasses launch security) until
 * the master is deliberately switched on. Once it is, every agent comes on together: the per-agent
 * flags all default on, so flipping the single master ({@code enabled}) lights up the whole pipeline
 * with zero further toggling. Set any per-agent flag to {@code false} to silence just that agent.
 * With the master off, or no key, the game runs on deterministic prose alone and never touches the network.
 *
 * <p>Effective rule for any agent X: it runs iff {@code isUsable()} (master on AND key present) AND
 * that agent's own flag — see {@code isNarrationActive()}, {@code isInterpreterActive()},
 * {@code isAuthoringActive()}, {@code isAuditorActive()}, {@code isQaActive()}.
 *
 * <p>The API key comes only from configuration/environment and is never committed or logged.
 * See docs/architecture/ai-integration.md for the runtime contract.
 */
@ConfigurationProperties(prefix = "draugr.ai")
public class AiProperties {

    /** MASTER switch, default OFF for security. Turn it on and — with a key present — every agent whose own flag is on runs. Off ⇒ the entire AI layer is inert. */
    private boolean enabled = false;

    /** Anthropic API key. Supplied via env (DRAUGR_AI_API_KEY / ANTHROPIC_API_KEY); never committed. */
    private String apiKey = "";

    // Per-agent switches — all default on, so the master + a key is all it takes. Set one false to silence just that agent.
    private boolean narrationEnabled = true;
    private boolean interpreterEnabled = true;
    private boolean auditorEnabled = true;
    private boolean qaEnabled = true;

    /**
     * Per-agent model ids. Each of the three agents runs on the model best suited to its job:
     * the Simulation Agent's narration is a high-frequency, low-stakes single sentence (fast/cheap
     * Haiku); the Architect proposes schema/data at authoring time and wants deep reasoning (Opus);
     * the Auditor summarizes consistency findings (Sonnet).
     */
    // Model ids are pinned to current, valid Anthropic model identifiers (#244): stale ids do not error at build
    // time and, with AI off by default, do not error in CI either — but the moment the pipeline is enabled with a key
    // they make every call fail silently (an unknown model looks identical to "nothing happened"). Keep these current.
    private String narrationModel = "claude-haiku-4-5-20251001";
    private String architectModel = "claude-opus-4-8";
    private String auditorModel = "claude-sonnet-5";
    /** The Procedure Interpreter (DR-0021): maps a miss to a sequence of existing processes. Mid-tier, read-only reasoning. */
    private String interpreterModel = "claude-sonnet-5";
    /** Independent QA critic for runtime-authored mechanics — deliberately a different model from the Architect. */
    private String qaModel = "claude-opus-4-8";
    /** The Architect's per-agent switch — runtime authoring of NEW scoped mechanics. Default on; set false to allow interpretation of existing processes without letting the Architect create new ones. */
    private boolean authoringEnabled = true;
    /** Author↔critic loop cap (DR-0021). */
    private int qaMaxRounds = 2;

    /** Output cap. Generous so a one- or two-sentence reply is never truncated. */
    private long maxTokens = 1024;

    /** Per-call wall-clock bound. A model that hangs must not hang the action loop. */
    private Duration timeout = Duration.ofSeconds(20);

    /** True only when the master is on AND a non-blank key is present. The base gate every agent builds on. */
    public boolean isUsable() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    // Per-agent activation = master usable AND that agent's own switch. With every per-agent flag
    // defaulting on, flipping the single master turns all of these true at once.
    public boolean isNarrationActive()   { return isUsable() && narrationEnabled; }
    public boolean isInterpreterActive() { return isUsable() && interpreterEnabled; }
    public boolean isAuthoringActive()   { return isUsable() && authoringEnabled; }
    public boolean isAuditorActive()     { return isUsable() && auditorEnabled; }
    public boolean isQaActive()          { return isUsable() && qaEnabled; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isNarrationEnabled() { return narrationEnabled; }
    public void setNarrationEnabled(boolean narrationEnabled) { this.narrationEnabled = narrationEnabled; }
    public boolean isInterpreterEnabled() { return interpreterEnabled; }
    public void setInterpreterEnabled(boolean interpreterEnabled) { this.interpreterEnabled = interpreterEnabled; }
    public boolean isAuditorEnabled() { return auditorEnabled; }
    public void setAuditorEnabled(boolean auditorEnabled) { this.auditorEnabled = auditorEnabled; }
    public boolean isQaEnabled() { return qaEnabled; }
    public void setQaEnabled(boolean qaEnabled) { this.qaEnabled = qaEnabled; }
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
