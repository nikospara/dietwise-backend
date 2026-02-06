package eu.dietwise.services.v1.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.dietwise.v1.json.ObjectMapperModelUtils;
import org.junit.jupiter.api.Test;

class RecipeJsonNormalizerImplTest {

	@Test
	void normalizesObjectIngredientsAndSingleInstructionIntoStringArrays() throws Exception {
		ObjectMapper objectMapper = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(new ObjectMapper());
		RecipeJsonCoercionStrategy strategy = new CompactJsonCoercionStrategy();
		RecipeJsonNormalizer normalizer = new RecipeJsonNormalizerImpl(objectMapper, strategy);

		String input = """
				{
				  "name": "Test Recipe",
				  "recipeIngredients": [
				    {"amount": "1", "unit": "cup", "item": "flour"},
				    "salt"
				  ],
				  "recipeInstructions": {"step": "Mix"}
				}
				""";

		String normalized = normalizer.normalize(input);
		JsonNode root = objectMapper.readTree(normalized);

		assertThat(root.get("recipeIngredients").isArray()).isTrue();
		assertThat(root.get("recipeIngredients").get(0).asText()).isEqualTo("{\"amount\":\"1\",\"unit\":\"cup\",\"item\":\"flour\"}");
		assertThat(root.get("recipeIngredients").get(1).asText()).isEqualTo("salt");
		assertThat(root.get("recipeInstructions").isArray()).isTrue();
		assertThat(root.get("recipeInstructions").get(0).asText()).isEqualTo("{\"step\":\"Mix\"}");
	}

	@Test
	void leavesInvalidJsonUnchanged() {
		ObjectMapper objectMapper = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(new ObjectMapper());
		RecipeJsonCoercionStrategy strategy = new CompactJsonCoercionStrategy();
		RecipeJsonNormalizer normalizer = new RecipeJsonNormalizerImpl(objectMapper, strategy);

		String invalid = """
				{
				  "recipeIngredients": ["salt"
				""";

		String normalized = normalizer.normalize(invalid);

		assertThat(normalized).isEqualTo(invalid);
	}
}
