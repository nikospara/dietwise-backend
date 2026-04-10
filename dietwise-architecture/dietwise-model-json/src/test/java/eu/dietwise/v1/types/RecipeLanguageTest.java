package eu.dietwise.v1.types;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.dietwise.v1.json.ObjectMapperModelUtils;
import org.junit.jupiter.api.Test;

public class RecipeLanguageTest {
	@Test
	void testSerialization() throws Exception {
		var om = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(new ObjectMapper());
		var result = om.writeValueAsString(RecipeLanguage.EN);
		assertThat(result).isEqualTo("\"en\"");
	}

	@Test
	void testDeserialization() throws Exception {
		var om = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(new ObjectMapper());
		var result = om.readValue("\"en\"", RecipeLanguage.class);
		assertThat(result).isEqualTo(RecipeLanguage.EN);
	}
}
