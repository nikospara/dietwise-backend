package eu.dietwise.services.model.recommendations;

import java.util.Optional;

import eu.dietwise.v1.types.Recommendation;
import eu.dietwise.v1.types.RecommendationComponentName;
import eu.dietwise.v1.types.TypeOfRecommendation;
import org.immutables.value.Value;

@Value.Immutable
public interface RecommendationComponent {
	Recommendation getRecommendation();

	RecommendationComponentName getComponentForScoring();

	TypeOfRecommendation getTypeOfRecommendation();

	Optional<String> getExplanationForLlm();

	Optional<String> getHumanFriendlyDisplay();
}
