package eu.dietwise.services.v1.extraction.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import eu.dietwise.services.v1.extraction.RecipeExtractionAiService;
import eu.dietwise.services.v1.extraction.RecipeExtractionElAiService;
import eu.dietwise.services.v1.extraction.RecipeExtractionLtAiService;
import eu.dietwise.services.v1.extraction.RecipeExtractionNlAiService;
import eu.dietwise.v1.types.RecipeLanguage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecipeExtractionAiSelectorImplTest {
	private static final String MARKDOWN = "recipe markdown";

	@Mock
	private RecipeExtractionAiService aiServiceEn;

	@Mock
	private RecipeExtractionNlAiService aiServiceNl;

	@Mock
	private RecipeExtractionElAiService aiServiceEl;

	@Mock
	private RecipeExtractionLtAiService aiServiceLt;

	@Test
	void selectsDutchService() {
		var sut = new RecipeExtractionAiSelectorImpl(aiServiceEn, aiServiceNl, aiServiceEl, aiServiceLt);
		when(aiServiceNl.extractRecipeFromMarkdown(MARKDOWN)).thenReturn("nl");

		String result = sut.extractRecipeFromMarkdown(RecipeLanguage.NL, MARKDOWN);

		assertThat(result).isEqualTo("nl");
		verify(aiServiceNl).extractRecipeFromMarkdown(MARKDOWN);
		verifyNoInteractions(aiServiceEn, aiServiceEl, aiServiceLt);
	}

	@Test
	void selectsGreekService() {
		var sut = new RecipeExtractionAiSelectorImpl(aiServiceEn, aiServiceNl, aiServiceEl, aiServiceLt);
		when(aiServiceEl.extractRecipeFromMarkdown(MARKDOWN)).thenReturn("el");

		String result = sut.extractRecipeFromMarkdown(RecipeLanguage.EL, MARKDOWN);

		assertThat(result).isEqualTo("el");
		verify(aiServiceEl).extractRecipeFromMarkdown(MARKDOWN);
		verifyNoInteractions(aiServiceEn, aiServiceNl, aiServiceLt);
	}
}
