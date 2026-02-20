package eu.dietwise.v1.model;

import static eu.dietwise.v1.types.BiologicalGender.FEMALE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import eu.dietwise.v1.json.ObjectMapperModelUtils;
import eu.dietwise.v1.types.impl.GenericIngredientId;
import org.junit.jupiter.api.Test;

public class RecipeTest {
	@Test
	void testSerialization() throws Exception {
		var recipe = ImmutableRecipe.builder()
				.name("name")
				.addRecipeIngredients(ImmutableIngredient.builder().id(new GenericIngredientId(UUID.randomUUID().toString())).nameInRecipe("ingredient").build())
				.addRecipeInstructions("instruction")
				.build();
		var om = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(new ObjectMapper());
		om.registerModule(new Jdk8Module()); // at runtime Quarkus provides this
		var result = om.writeValueAsString(recipe);
	}

	@Test
	void testDeserialization() throws Exception {
		var om = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(new ObjectMapper());
		var json = """
				{
					"gender": "FEMALE",
					"yearOfBirth": 1979
				}
				""";
		var result = om.readValue(json, PersonalInfo.class);
		assertThat(result.getGender()).isEqualTo(FEMALE);
		assertThat(result.getYearOfBirth()).isEqualTo(1979);
	}
}
