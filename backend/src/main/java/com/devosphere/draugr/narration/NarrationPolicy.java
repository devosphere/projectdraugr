package com.devosphere.draugr.narration;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Rejects narration that turns the narrator into an internal-state hint system.
 * Immediate, fact-backed sensory perception remains permissible; diagnostic HUD labels and advice do not.
 */
@Component
public class NarrationPolicy {
    private static final List<String> FORBIDDEN_HINTS = List.of(
            "you should", "you need to", "find water", "eat soon", "rest soon",
            "you are thirsty", "you are hungry", "you are exhausted", "you are starving",
            "your bladder", "your bowel", "your hygiene", "your hunger", "your thirst",
            "your energy", "your temperature", "you are dehydrated", "you are fatigued");

    public void validate(String narration) {
        String normalized = narration.toLowerCase(Locale.ROOT);
        for (String forbidden : FORBIDDEN_HINTS) {
            if (normalized.contains(forbidden)) throw new IllegalArgumentException("Narration may not provide body-state advice or HUD reminders.");
        }
    }
}
