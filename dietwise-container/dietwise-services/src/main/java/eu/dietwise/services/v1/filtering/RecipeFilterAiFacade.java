package eu.dietwise.services.v1.filtering;

import io.smallrye.mutiny.Uni;

public interface RecipeFilterAiFacade {
	Uni<String> filterRecipeBlock(String block);
}
