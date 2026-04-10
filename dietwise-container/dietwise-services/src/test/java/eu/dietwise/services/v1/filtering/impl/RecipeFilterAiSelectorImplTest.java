package eu.dietwise.services.v1.filtering.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import eu.dietwise.services.v1.filtering.RecipeFilterAiService;
import eu.dietwise.services.v1.filtering.RecipeFilterElAiService;
import eu.dietwise.services.v1.filtering.RecipeFilterLtAiService;
import eu.dietwise.services.v1.filtering.RecipeFilterNlAiService;
import eu.dietwise.v1.types.RecipeLanguage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecipeFilterAiSelectorImplTest {
	private static final String BLOCK = "markdown block";

	@Mock
	private RecipeFilterAiService aiServiceEn;
	@Mock
	private RecipeFilterNlAiService aiServiceNl;
	@Mock
	private RecipeFilterElAiService aiServiceEl;
	@Mock
	private RecipeFilterLtAiService aiServiceLt;

	@Test
	void selectsGreekService() {
		var sut = new RecipeFilterAiSelectorImpl(aiServiceEn, aiServiceNl, aiServiceEl, aiServiceLt);
		when(aiServiceEl.filterRecipeBlock(BLOCK)).thenReturn("KEEP");

		String result = sut.filterRecipeBlock(RecipeLanguage.EL, BLOCK);

		assertThat(result).isEqualTo("KEEP");
		verify(aiServiceEl).filterRecipeBlock(BLOCK);
		verifyNoInteractions(aiServiceEn, aiServiceNl, aiServiceLt);
	}
}
