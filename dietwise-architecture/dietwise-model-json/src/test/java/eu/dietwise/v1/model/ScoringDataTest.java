package eu.dietwise.v1.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import eu.dietwise.v1.json.ObjectMapperModelUtils;
import eu.dietwise.v1.types.IngredientId;
import eu.dietwise.v1.types.RecommendationComponentName;
import eu.dietwise.v1.types.RecommendationWeight;
import eu.dietwise.v1.types.impl.GenericIngredientId;
import eu.dietwise.v1.types.impl.RecommendationComponentNameImpl;
import org.junit.jupiter.api.Test;

public class ScoringDataTest {
	@Test
	void testSerialization() throws Exception {
		Map<RecommendationComponentName, RecommendationWeight> recommendationWeights = Map.of(new RecommendationComponentNameImpl("rec1"), RecommendationWeight.LIMITED);
		Map<IngredientId, Set<RecommendationComponentName>> recommendationsPerIngredient = Map.of(new GenericIngredientId("id"), Set.of(new RecommendationComponentNameImpl("rec2")));
		var recipe = ImmutableScoringData.builder()
				.totalNumberOfRecomendations(15)
				.recommendationWeights(recommendationWeights)
				.recommendationsPerIngredient(recommendationsPerIngredient)
				.build();
		var om = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(new ObjectMapper());
		om.registerModule(new Jdk8Module()); // at runtime Quarkus provides this
		var result = om.writeValueAsString(recipe);
		assertThat(result).doesNotContain("RecommendationComponentNameImpl", "GenericIngredientId");
	}
}
