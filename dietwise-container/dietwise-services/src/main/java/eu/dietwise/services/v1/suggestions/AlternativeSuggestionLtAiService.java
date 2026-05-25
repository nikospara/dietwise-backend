package eu.dietwise.services.v1.suggestions;

import jakarta.enterprise.context.RequestScoped;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(
		modelName = "suggestAlternativesLt",
		chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class
)
@RequestScoped
public interface AlternativeSuggestionLtAiService {
	@SystemMessage(fromResource = "eu/dietwise/services/v1/ai/lt/suggestAlternatives-system.md")
	@UserMessage(fromResource = "eu/dietwise/services/v1/ai/lt/suggestAlternatives-user.md")
	String suggestAlternatives(String ingredientNameInRecipe, String ingredientRoleOrTechnique, String alternativesAsMarkdownList);
}
