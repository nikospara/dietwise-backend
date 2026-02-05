package eu.dietwise.v1.model;

import static eu.dietwise.v1.types.BiologicalGender.FEMALE;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.dietwise.v1.json.ObjectMapperModelUtils;
import org.junit.jupiter.api.Test;

public class PersonalInfoTest {
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
