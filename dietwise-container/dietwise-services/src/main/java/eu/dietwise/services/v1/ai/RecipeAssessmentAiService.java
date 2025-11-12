package eu.dietwise.services.v1.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService
@SystemMessage(fromResource = "eu/dietwise/services/v1/ai/extract-recipe-from-html.md")
@ApplicationScoped
public interface RecipeAssessmentAiService {
	@UserMessage("{html}")
	String extractRecipeFromHtml(String html);
}
