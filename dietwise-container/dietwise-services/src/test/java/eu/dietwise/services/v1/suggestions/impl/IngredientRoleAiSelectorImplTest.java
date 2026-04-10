package eu.dietwise.services.v1.suggestions.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import eu.dietwise.services.v1.suggestions.IngredientRoleAiService;
import eu.dietwise.services.v1.suggestions.IngredientRoleElAiService;
import eu.dietwise.services.v1.suggestions.IngredientRoleLtAiService;
import eu.dietwise.services.v1.suggestions.IngredientRoleNlAiService;
import eu.dietwise.v1.types.RecipeLanguage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngredientRoleAiSelectorImplTest {
	private static final String ROLES = "- binder";
	private static final String INGREDIENT = "bloem";
	private static final String INSTRUCTIONS = "- mix";

	@Mock
	private IngredientRoleAiService aiServiceEn;

	@Mock
	private IngredientRoleNlAiService aiServiceNl;

	@Mock
	private IngredientRoleElAiService aiServiceEl;

	@Mock
	private IngredientRoleLtAiService aiServiceLt;

	@Test
	void selectsDutchService() {
		var sut = new IngredientRoleAiSelectorImpl(aiServiceEn, aiServiceNl, aiServiceEl, aiServiceLt);
		when(aiServiceNl.assessIngredientRole(ROLES, INGREDIENT, INSTRUCTIONS)).thenReturn("binder");

		String result = sut.assessIngredientRole(RecipeLanguage.NL, ROLES, INGREDIENT, INSTRUCTIONS);

		assertThat(result).isEqualTo("binder");
		verify(aiServiceNl).assessIngredientRole(ROLES, INGREDIENT, INSTRUCTIONS);
		verifyNoInteractions(aiServiceEn, aiServiceEl, aiServiceLt);
	}

	@Test
	void selectsLithuanianService() {
		var sut = new IngredientRoleAiSelectorImpl(aiServiceEn, aiServiceNl, aiServiceEl, aiServiceLt);
		when(aiServiceLt.assessIngredientRole(ROLES, INGREDIENT, INSTRUCTIONS)).thenReturn("binder");

		String result = sut.assessIngredientRole(RecipeLanguage.LT, ROLES, INGREDIENT, INSTRUCTIONS);

		assertThat(result).isEqualTo("binder");
		verify(aiServiceLt).assessIngredientRole(ROLES, INGREDIENT, INSTRUCTIONS);
		verifyNoInteractions(aiServiceEn, aiServiceNl, aiServiceEl);
	}
}
