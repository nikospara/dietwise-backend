package eu.dietwise.services.v1.filtering;

import eu.dietwise.v1.types.RecipeLanguage;
import io.smallrye.mutiny.Uni;

public interface RecipeFilterAiFacade {
	Uni<String> filterRecipeBlock(RecipeLanguage lang, String block);
}
