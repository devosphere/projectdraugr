package com.devosphere.draugr.ai;

import java.util.List;

/**
 * A material process the Runtime Architect drafts from a novel procedure (DR-0021). It is a proposal,
 * not yet real — the deterministic {@link RuntimeProcessGate} must pass it and (with AI) the QA critic
 * must judge it plausible before any scoped row is written. It carries everything needed to insert one
 * {@code material_process} (+ its inputs/output) scoped to the discovering chronicle, plus any brand-new
 * {@code item_definition} rows the recipe introduces (also scoped).
 */
public record ProcessDraft(
    String processKey,
    String category,
    String keywords,          // comma-separated verb/keyword forms
    String subjects,          // comma-separated subject terms
    String toolClass,         // CUTTING / STRIKING / AXE / null
    List<Ingredient> inputs,
    String outputItemKey,
    int outputQty,
    String narration,
    List<NewItem> newItems) {

    /** One input: an item key and how many are consumed. */
    public record Ingredient(String itemKey, int quantity) { }

    /** A brand-new item the recipe introduces, with the mass/volume the mass-balance gate needs. */
    public record NewItem(String itemKey, String displayName, String category, int unitMassGrams, int unitVolumeMl) { }
}
