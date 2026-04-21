package eu.dietwise.v1.model;

import java.util.Optional;

import eu.dietwise.common.types.Nullable;
import eu.dietwise.v1.types.RecipeLanguage;
import org.immutables.value.Value;

@Value.Immutable
public interface RecipeAssessmentParam {
	String getUrl();

	String getPageContent();

	@Nullable
	String getJsonLdContent();

	RecipeLanguage getLang();
}
