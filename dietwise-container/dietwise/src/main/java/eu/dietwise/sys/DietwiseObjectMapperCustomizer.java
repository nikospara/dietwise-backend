package eu.dietwise.sys;

import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.dietwise.v1.json.ObjectMapperModelUtils;
import io.quarkus.jackson.ObjectMapperCustomizer;

@Singleton
public class DietwiseObjectMapperCustomizer implements ObjectMapperCustomizer {
	@Override
	public void customize(ObjectMapper objectMapper) {
		ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(objectMapper);
	}
}
