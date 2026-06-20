package eu.dietwise.services.v1.impl;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import eu.dietwise.services.model.suggestions.TranslationLangs;
import eu.dietwise.services.v1.types.TranslationState;
import eu.dietwise.v1.types.RecipeLanguage;

/**
 * Shared backoffice translation helpers: the translatable (non-English) languages, the per-language translation state
 * of a translatable thing for the grid, and the English-is-not-a-translation guard the staging operations enforce.
 */
final class BackofficeTranslations {
	static final List<RecipeLanguage> TRANSLATABLE_LANGUAGES =
			Arrays.stream(RecipeLanguage.values()).filter(lang -> lang != RecipeLanguage.EN).toList();

	private BackofficeTranslations() {
	}

	static void requireTranslatableLanguage(RecipeLanguage lang) {
		if (lang == RecipeLanguage.EN) {
			throw new IllegalArgumentException("English is the master value, not a translation");
		}
	}

	static Map<RecipeLanguage, TranslationState> translationStates(UUID id, Map<UUID, TranslationLangs> byId) {
		return translationStates(byId.get(id));
	}

	static Map<RecipeLanguage, TranslationState> translationStates(TranslationLangs langs) {
		Set<RecipeLanguage> present = langs == null ? Set.of() : langs.present();
		Set<RecipeLanguage> staged = langs == null ? Set.of() : langs.staged();
		Map<RecipeLanguage, TranslationState> states = new EnumMap<>(RecipeLanguage.class);
		for (RecipeLanguage lang : TRANSLATABLE_LANGUAGES) {
			states.put(lang, translationState(lang, present, staged));
		}
		return states;
	}

	private static TranslationState translationState(RecipeLanguage lang, Set<RecipeLanguage> present, Set<RecipeLanguage> staged) {
		if (staged.contains(lang)) {
			return TranslationState.STAGED;
		}
		return present.contains(lang) ? TranslationState.PRESENT : TranslationState.MISSING;
	}
}
