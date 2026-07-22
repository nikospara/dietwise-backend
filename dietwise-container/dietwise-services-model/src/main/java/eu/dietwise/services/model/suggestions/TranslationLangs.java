package eu.dietwise.services.model.suggestions;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eu.dietwise.v1.types.RecipeLanguage;

/**
 * How complete a translatable thing (a Rule's rationale, or a Trigger Ingredient or Role or Technique) is in each
 * language, measured over the effective value of its translatable fields: {@code full} are the languages whose every
 * field carries text, {@code partial} those with some (but not all) fields translated, and {@code staged} those with a
 * pending change in the Working Copy. A language in neither {@code full} nor {@code partial} is empty (missing). Used to
 * derive the per-language completeness state shown on the grid without exposing the translation text.
 */
public record TranslationLangs(Set<RecipeLanguage> full, Set<RecipeLanguage> partial, Set<RecipeLanguage> staged) {

	/**
	 * Classifies each language by how many of its translatable fields carry text in the effective translation — the
	 * Working Copy field values when a change is staged for that language, otherwise the published master field values.
	 * A language whose fields are all empty lands in neither {@code full} nor {@code partial} (it is missing); all
	 * fields filled makes it {@code full}, some makes it {@code partial}. {@code staged} lists the languages with a
	 * pending change.
	 */
	public static TranslationLangs classify(
			Map<RecipeLanguage, List<String>> master,
			Map<RecipeLanguage, List<String>> wc,
			Set<RecipeLanguage> staged) {
		Set<RecipeLanguage> full = EnumSet.noneOf(RecipeLanguage.class);
		Set<RecipeLanguage> partial = EnumSet.noneOf(RecipeLanguage.class);
		Set<RecipeLanguage> langs = EnumSet.noneOf(RecipeLanguage.class);
		langs.addAll(master.keySet());
		langs.addAll(wc.keySet());
		for (RecipeLanguage lang : langs) {
			List<String> effective = wc.containsKey(lang) ? wc.get(lang) : master.get(lang);
			long filled = effective.stream().filter(value -> value != null && !value.isBlank()).count();
			if (filled == 0) {
				continue;
			}
			(filled == effective.size() ? full : partial).add(lang);
		}
		Set<RecipeLanguage> stagedLangs = EnumSet.noneOf(RecipeLanguage.class);
		stagedLangs.addAll(staged);
		return new TranslationLangs(full, partial, stagedLangs);
	}
}
