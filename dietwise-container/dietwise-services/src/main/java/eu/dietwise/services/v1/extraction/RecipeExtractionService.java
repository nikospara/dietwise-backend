package eu.dietwise.services.v1.extraction;

import java.util.UUID;

import eu.dietwise.services.v1.types.RecipeAssessmentMessage.RecipeExtractionRecipeAssessmentMessage;
import eu.dietwise.v1.model.RecipeExtractionAndAssessmentParam;
import eu.dietwise.v1.types.RecipeLanguage;
import io.smallrye.mutiny.Uni;

/**
 * This is the entry point for the extraction of a recipe from a Markdown text or URL.
 */
public interface RecipeExtractionService {
	Uni<RecipeExtractionRecipeAssessmentMessage> useAiToExtractRecipeFromMarkdown(UUID correlationId, String url, RecipeLanguage lang, String markdown);

	Uni<RecipeExtractionRecipeAssessmentMessage> extractRecipeFromUrl(UUID correlationId, RecipeExtractionAndAssessmentParam param);
}
