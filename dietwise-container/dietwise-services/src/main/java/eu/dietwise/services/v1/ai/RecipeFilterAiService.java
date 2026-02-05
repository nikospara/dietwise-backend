package eu.dietwise.services.v1.ai;

import jakarta.enterprise.context.RequestScoped;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(modelName = "filter")
@RequestScoped
public interface RecipeFilterAiService {
	@SystemMessage(fromResource = "eu/dietwise/services/v1/ai/extract-recipe-filter-block.md")
	@UserMessage("{block}")
	String filterRecipeBlock(String block);
}
