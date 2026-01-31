package eu.dietwise.v1.model;

import java.util.List;
import java.util.Optional;

import org.immutables.value.Value;

@Value.Immutable
public interface Recipe {
	Optional<String> getName();
	Optional<String> getRecipeYield();
	List<String> getRecipeIngredients();
	List<String> getRecipeInstructions(); // TODO Must reference ingredient
	String getText();
}
