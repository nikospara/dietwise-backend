package eu.dietwise.services.model.suggestions;

import java.util.Set;

import eu.dietwise.v1.types.RecipeLanguage;

/**
 * Which languages a Rule's rationale is translated into, split by source: {@code present} are the languages with a
 * published master translation, {@code staged} the languages whose translation has a pending change in the Working
 * Copy. Used to derive the per-language completeness state shown on the grid without exposing the translation text.
 */
public record RationaleTranslationLangs(Set<RecipeLanguage> present, Set<RecipeLanguage> staged) {
}
