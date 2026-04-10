package eu.dietwise.services.v1.suggestions.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import eu.dietwise.services.v1.suggestions.FindBestRuleAiService;
import eu.dietwise.services.v1.suggestions.FindBestRuleElAiService;
import eu.dietwise.services.v1.suggestions.FindBestRuleLtAiService;
import eu.dietwise.services.v1.suggestions.FindBestRuleNlAiService;
import eu.dietwise.v1.types.RecipeLanguage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FindBestRuleAiSelectorImplTest {
	@Mock
	private FindBestRuleAiService aiServiceEn;
	@Mock
	private FindBestRuleNlAiService aiServiceNl;
	@Mock
	private FindBestRuleElAiService aiServiceEl;
	@Mock
	private FindBestRuleLtAiService aiServiceLt;

	@Test
	void selectsDutchService() {
		var sut = new FindBestRuleAiSelectorImpl(aiServiceEn, aiServiceNl, aiServiceEl, aiServiceLt);
		when(aiServiceNl.findBestRule("ingredient", "role", "trigger", "components", "rules")).thenReturn("1");

		String result = sut.findBestRule(RecipeLanguage.NL, "ingredient", "role", "trigger", "components", "rules");

		assertThat(result).isEqualTo("1");
		verify(aiServiceNl).findBestRule("ingredient", "role", "trigger", "components", "rules");
		verifyNoInteractions(aiServiceEn, aiServiceEl, aiServiceLt);
	}
}
