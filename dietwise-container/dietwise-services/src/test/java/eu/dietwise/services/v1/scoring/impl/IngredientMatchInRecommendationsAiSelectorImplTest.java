package eu.dietwise.services.v1.scoring.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import eu.dietwise.services.v1.scoring.IngredientMatchInRecommendationsAiService;
import eu.dietwise.services.v1.scoring.IngredientMatchInRecommendationsElAiService;
import eu.dietwise.services.v1.scoring.IngredientMatchInRecommendationsLtAiService;
import eu.dietwise.services.v1.scoring.IngredientMatchInRecommendationsNlAiService;
import eu.dietwise.v1.types.RecipeLanguage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngredientMatchInRecommendationsAiSelectorImplTest {
	private static final String AVAILABLE = "- fiber";
	private static final String INGREDIENT = "oats";

	@Mock
	private IngredientMatchInRecommendationsAiService aiServiceEn;
	@Mock
	private IngredientMatchInRecommendationsNlAiService aiServiceNl;
	@Mock
	private IngredientMatchInRecommendationsElAiService aiServiceEl;
	@Mock
	private IngredientMatchInRecommendationsLtAiService aiServiceLt;

	@Test
	void selectsLithuanianService() {
		var sut = new IngredientMatchInRecommendationsAiSelectorImpl(aiServiceEn, aiServiceNl, aiServiceEl, aiServiceLt);
		when(aiServiceLt.matchIngredientsWithRecommendations(AVAILABLE, INGREDIENT)).thenReturn("fiber");

		String result = sut.matchIngredientsWithRecommendations(RecipeLanguage.LT, AVAILABLE, INGREDIENT);

		assertThat(result).isEqualTo("fiber");
		verify(aiServiceLt).matchIngredientsWithRecommendations(AVAILABLE, INGREDIENT);
		verifyNoInteractions(aiServiceEn, aiServiceNl, aiServiceEl);
	}
}
