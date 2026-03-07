package eu.dietwise.services.model.recommendations;

import java.util.Optional;

import eu.dietwise.v1.types.Recommendation;
import eu.dietwise.v1.types.RecommendationWeight;
import org.immutables.value.Value;

@Value.Immutable
public interface RecommendationComponent {
	Recommendation getRecommendation();

	String getComponentForScoring();

	RecommendationWeight getWeight();

	Optional<String> getExplanationForLlm();
}
