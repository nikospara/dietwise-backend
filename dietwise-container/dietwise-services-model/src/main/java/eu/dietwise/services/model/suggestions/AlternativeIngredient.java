package eu.dietwise.services.model.suggestions;

import java.util.Optional;

import eu.dietwise.services.types.suggestions.HasAlternativeIngredientId;
import org.immutables.value.Value;

@Value.Immutable
public interface AlternativeIngredient extends HasAlternativeIngredientId {
	String getName();

	Optional<String> getExplanationForLlm();
}
