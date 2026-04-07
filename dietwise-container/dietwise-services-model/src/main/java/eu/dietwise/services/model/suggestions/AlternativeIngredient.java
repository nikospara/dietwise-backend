package eu.dietwise.services.model.suggestions;

import java.util.Map;
import java.util.Optional;

import eu.dietwise.services.types.suggestions.HasAlternativeIngredientId;
import eu.dietwise.v1.types.Country;
import eu.dietwise.v1.types.Seasonality;
import org.immutables.value.Value;

@Value.Immutable
public interface AlternativeIngredient extends HasAlternativeIngredientId {
	String getName();

	Optional<String> getExplanationForLlm();

	Optional<Map<Country, Seasonality>> getSeasonalityByCountry();
}
