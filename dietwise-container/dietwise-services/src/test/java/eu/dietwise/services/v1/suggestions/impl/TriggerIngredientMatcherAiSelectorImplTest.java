package eu.dietwise.services.v1.suggestions.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import eu.dietwise.services.v1.suggestions.TriggerIngredientMatcherAiService;
import eu.dietwise.services.v1.suggestions.TriggerIngredientMatcherElAiService;
import eu.dietwise.services.v1.suggestions.TriggerIngredientMatcherLtAiService;
import eu.dietwise.services.v1.suggestions.TriggerIngredientMatcherNlAiService;
import eu.dietwise.v1.types.RecipeLanguage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TriggerIngredientMatcherAiSelectorImplTest {
	private static final String AVAILABLE = "- flour";
	private static final String INGREDIENT = "flour";
	private static final String ROLE = "binder";

	@Mock
	private TriggerIngredientMatcherAiService aiServiceEn;
	@Mock
	private TriggerIngredientMatcherNlAiService aiServiceNl;
	@Mock
	private TriggerIngredientMatcherElAiService aiServiceEl;
	@Mock
	private TriggerIngredientMatcherLtAiService aiServiceLt;

	@Test
	void selectsDutchService() {
		var sut = new TriggerIngredientMatcherAiSelectorImpl(aiServiceEn, aiServiceNl, aiServiceEl, aiServiceLt);
		when(aiServiceNl.matchIngredientToTrigger(AVAILABLE, INGREDIENT, ROLE)).thenReturn("meel");

		String result = sut.matchIngredientToTrigger(RecipeLanguage.NL, AVAILABLE, INGREDIENT, ROLE);

		assertThat(result).isEqualTo("meel");
		verify(aiServiceNl).matchIngredientToTrigger(AVAILABLE, INGREDIENT, ROLE);
		verifyNoInteractions(aiServiceEn, aiServiceEl, aiServiceLt);
	}
}
