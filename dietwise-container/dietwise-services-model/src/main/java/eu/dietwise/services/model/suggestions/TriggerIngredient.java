package eu.dietwise.services.model.suggestions;

import java.util.Optional;

import eu.dietwise.services.types.suggestions.HasTriggerIngredientId;
import org.immutables.value.Value;

@Value.Immutable
public interface TriggerIngredient extends HasTriggerIngredientId {
	String getName();

	Optional<String> getExplanationForLlm();
}
