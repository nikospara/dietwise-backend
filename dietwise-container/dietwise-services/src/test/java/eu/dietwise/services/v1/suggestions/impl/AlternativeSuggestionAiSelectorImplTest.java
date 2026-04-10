package eu.dietwise.services.v1.suggestions.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import eu.dietwise.services.v1.suggestions.AlternativeSuggestionAiService;
import eu.dietwise.services.v1.suggestions.AlternativeSuggestionElAiService;
import eu.dietwise.services.v1.suggestions.AlternativeSuggestionLtAiService;
import eu.dietwise.services.v1.suggestions.AlternativeSuggestionNlAiService;
import eu.dietwise.v1.types.RecipeLanguage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlternativeSuggestionAiSelectorImplTest {
	@Mock
	private AlternativeSuggestionAiService aiServiceEn;
	@Mock
	private AlternativeSuggestionNlAiService aiServiceNl;
	@Mock
	private AlternativeSuggestionElAiService aiServiceEl;
	@Mock
	private AlternativeSuggestionLtAiService aiServiceLt;

	@Test
	void selectsGreekService() {
		var sut = new AlternativeSuggestionAiSelectorImpl(aiServiceEn, aiServiceNl, aiServiceEl, aiServiceLt);
		when(aiServiceEl.suggestAlternatives("ingredient", "role", "alternatives")).thenReturn("answer");

		String result = sut.suggestAlternatives(RecipeLanguage.EL, "ingredient", "role", "alternatives");

		assertThat(result).isEqualTo("answer");
		verify(aiServiceEl).suggestAlternatives("ingredient", "role", "alternatives");
		verifyNoInteractions(aiServiceEn, aiServiceNl, aiServiceLt);
	}
}
