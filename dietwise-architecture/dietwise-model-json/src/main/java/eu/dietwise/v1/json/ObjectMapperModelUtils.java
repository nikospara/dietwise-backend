package eu.dietwise.v1.json;

import com.fasterxml.jackson.databind.ObjectMapper;

public interface ObjectMapperModelUtils {
	static ObjectMapper applyDefaultObjectMapperConfiguration(ObjectMapper om) {
		om.registerModule(new DietwiseJacksonModule());
		return om;
	}
}
