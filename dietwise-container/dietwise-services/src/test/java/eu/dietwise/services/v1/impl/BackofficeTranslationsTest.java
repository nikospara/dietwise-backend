package eu.dietwise.services.v1.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import eu.dietwise.services.model.suggestions.TranslationLangs;
import eu.dietwise.services.v1.types.TranslationState;
import eu.dietwise.v1.types.RecipeLanguage;
import org.junit.jupiter.api.Test;

class BackofficeTranslationsTest {

	private static TranslationState stateOf(Set<RecipeLanguage> full, Set<RecipeLanguage> partial, Set<RecipeLanguage> staged) {
		Map<RecipeLanguage, TranslationState> states =
				BackofficeTranslations.translationStates(new TranslationLangs(full, partial, staged));
		return states.get(RecipeLanguage.EL);
	}

	@Test
	void fullyTranslatedAndUnchangedIsPresent() {
		assertThat(stateOf(EnumSet.of(RecipeLanguage.EL), EnumSet.noneOf(RecipeLanguage.class), EnumSet.noneOf(RecipeLanguage.class)))
				.isEqualTo(TranslationState.PRESENT);
	}

	@Test
	void fullyTranslatedWithAPendingChangeIsStaged() {
		assertThat(stateOf(EnumSet.of(RecipeLanguage.EL), EnumSet.noneOf(RecipeLanguage.class), EnumSet.of(RecipeLanguage.EL)))
				.isEqualTo(TranslationState.STAGED);
	}

	@Test
	void partiallyTranslatedAndUnchangedIsPartial() {
		assertThat(stateOf(EnumSet.noneOf(RecipeLanguage.class), EnumSet.of(RecipeLanguage.EL), EnumSet.noneOf(RecipeLanguage.class)))
				.isEqualTo(TranslationState.PARTIAL);
	}

	@Test
	void partiallyTranslatedWithAPendingChangeIsPartialStaged() {
		assertThat(stateOf(EnumSet.noneOf(RecipeLanguage.class), EnumSet.of(RecipeLanguage.EL), EnumSet.of(RecipeLanguage.EL)))
				.isEqualTo(TranslationState.PARTIAL_STAGED);
	}

	@Test
	void everyFieldEmptyIsMissingEvenWhenAChangeIsStaged() {
		assertThat(stateOf(EnumSet.noneOf(RecipeLanguage.class), EnumSet.noneOf(RecipeLanguage.class), EnumSet.of(RecipeLanguage.EL)))
				.isEqualTo(TranslationState.MISSING);
	}

	@Test
	void noCompletenessAtAllIsMissing() {
		assertThat(stateOf(EnumSet.noneOf(RecipeLanguage.class), EnumSet.noneOf(RecipeLanguage.class), EnumSet.noneOf(RecipeLanguage.class)))
				.isEqualTo(TranslationState.MISSING);
	}

	@Test
	void classifyMarksAllFieldsFilledAsFullAndSomeAsPartial() {
		TranslationLangs langs = TranslationLangs.classify(
				Map.of(
						RecipeLanguage.EL, java.util.Arrays.asList("Όνομα", "Εξήγηση"),
						RecipeLanguage.LT, java.util.Arrays.asList("Pavadinimas", null),
						RecipeLanguage.NL, java.util.Arrays.asList(null, null)),
				Map.of(),
				Set.of());

		assertThat(langs.full()).containsExactly(RecipeLanguage.EL);
		assertThat(langs.partial()).containsExactly(RecipeLanguage.LT);
		assertThat(langs.staged()).isEmpty();
	}

	@Test
	void classifyTakesTheWorkingCopyValueAsEffectiveWhenStaged() {
		TranslationLangs langs = TranslationLangs.classify(
				Map.of(RecipeLanguage.EL, java.util.Arrays.asList("Όνομα", "Εξήγηση")),
				Map.of(RecipeLanguage.EL, java.util.Arrays.asList("Όνομα", "  ")),
				Set.of(RecipeLanguage.EL));

		assertThat(langs.full()).isEmpty();
		assertThat(langs.partial()).containsExactly(RecipeLanguage.EL);
		assertThat(langs.staged()).containsExactly(RecipeLanguage.EL);
	}
}
