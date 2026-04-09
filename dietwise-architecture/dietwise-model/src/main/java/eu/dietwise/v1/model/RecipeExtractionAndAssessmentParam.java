package eu.dietwise.v1.model;

import java.util.Optional;

import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.Viewport;
import org.immutables.value.Value;

@Value.Immutable
public interface RecipeExtractionAndAssessmentParam {
	String getUrl();

	Optional<Viewport> getViewport();

	RecipeLanguage getLang();
}
