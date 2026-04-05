package eu.dietwise.services.v1.suggestions;

import jakarta.enterprise.context.RequestScoped;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(
		modelName = "findBestRule",
		chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class
)
@RequestScoped
public interface FindBestRuleAiService {
	@SystemMessage(fromResource = "eu/dietwise/services/v1/ai/findBestRule-system.md")
	@UserMessage(fromResource = "eu/dietwise/services/v1/ai/findBestRule-user.md")
	String findBestRule(String ingredientNameInRecipe, String ingredientRoleOrTechnique, String triggerIngredient, String dietaryComponentsMarkdownList, String filteredRulesMarkdownList);
}
