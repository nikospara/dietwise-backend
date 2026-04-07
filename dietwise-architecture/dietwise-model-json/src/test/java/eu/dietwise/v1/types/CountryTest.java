package eu.dietwise.v1.types;

import static eu.dietwise.v1.types.Country.GREECE;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.dietwise.v1.json.ObjectMapperModelUtils;
import org.junit.jupiter.api.Test;

public class CountryTest {
	@Test
	void testSerialization() throws Exception {
		var om = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(new ObjectMapper());
		var result = om.writeValueAsString(GREECE);
		assertThat(result).isEqualTo("\"GR\"");
	}

	@Test
	void testDeserialization() throws Exception {
		var om = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(new ObjectMapper());
		var result = om.readValue("\"GR\"", Country.class);
		assertThat(result).isEqualTo(GREECE);
	}
}
