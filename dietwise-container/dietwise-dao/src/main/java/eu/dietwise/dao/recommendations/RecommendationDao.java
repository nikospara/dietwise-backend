package eu.dietwise.dao.recommendations;

import java.math.BigDecimal;
import java.util.Map;

import eu.dietwise.v1.types.BiologicalGender;
import eu.dietwise.v1.types.Recommendation;
import io.smallrye.mutiny.Uni;

public interface RecommendationDao {
	Uni<Map<Recommendation, BigDecimal>> findRecommendations(int age, BiologicalGender gender);
}
