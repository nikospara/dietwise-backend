package eu.dietwise.v1.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import eu.dietwise.v1.json.ObjectMapperModelUtils;
import eu.dietwise.v1.types.IngredientId;
import eu.dietwise.v1.types.RecommendationComponentName;
import eu.dietwise.v1.types.TypeOfRecommendation;
import eu.dietwise.v1.types.impl.GenericIngredientId;
import eu.dietwise.v1.types.impl.RecommendationComponentNameImpl;
import org.junit.jupiter.api.Test;

public class ScoringDataTest {
	@Test
	void testSerialization() throws Exception {
		Map<RecommendationComponentName, RecommendationSpecialWeight> recommendationWeights = Map.of(
				new RecommendationComponentNameImpl("rec1"),
				ImmutableRecommendationSpecialWeight.builder().typeOfRecommendation(TypeOfRecommendation.LIMITED).weight(new BigDecimal("0.35")).build());
		Map<RecommendationComponentName, String> humanFriendlyDisplays = Map.of(new RecommendationComponentNameImpl("rec1"), "Eat less processed meat");
		Map<IngredientId, Set<RecommendationComponentName>> recommendationsPerIngredient = Map.of(new GenericIngredientId("id"), Set.of(new RecommendationComponentNameImpl("rec2")));
		var recipe = ImmutableScoringData.builder()
				.totalNumberOfRecomendations(15)
				.recommendationWeights(recommendationWeights)
				.humanFriendlyDisplays(humanFriendlyDisplays)
				.recommendationsPerIngredient(recommendationsPerIngredient)
				.build();
		var om = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(new ObjectMapper());
		om.registerModule(new Jdk8Module()); // at runtime Quarkus provides this
		var result = om.writeValueAsString(recipe);
		assertThat(result).doesNotContain("RecommendationComponentNameImpl", "GenericIngredientId");
		assertThat(result).contains("Eat less processed meat");
		assertThat(result).contains("LIMITED", "0.35");
	}
}
