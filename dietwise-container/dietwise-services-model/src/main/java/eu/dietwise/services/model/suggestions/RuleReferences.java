package eu.dietwise.services.model.suggestions;

import java.util.UUID;

/**
 * The reference-data ids a Rule points at: its Trigger Ingredient and its Role or Technique (the latter {@code null}
 * when the Rule has none). Used to overlay effective names and flag the affected grid cells without resolving the
 * shared entities to names at the DAO layer.
 */
public record RuleReferences(UUID triggerIngredientId, UUID roleOrTechniqueId) {
}
