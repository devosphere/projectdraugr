package com.devosphere.draugr.ai;

import java.util.Optional;

/**
 * A provider-agnostic text model. The whole AI layer talks to this seam, so a provider swap or a
 * disabled build touches nothing downstream.
 *
 * <p>The contract's load-bearing clause is the return type: {@link #generate} <b>never throws</b>
 * into the caller and returns {@link Optional#empty()} on anything less than a clean success —
 * disabled, missing key, timeout, transport error, refusal, or a blank completion. Callers always
 * have a deterministic fallback ready and use it whenever the result is empty.
 */
public interface LanguageModel {

    /**
     * @param system the system prompt (role + hard constraints)
     * @param user   the user turn (the concrete request)
     * @return the model's text, or empty if the call did not cleanly produce usable text
     */
    Optional<String> generate(String system, String user);
}
