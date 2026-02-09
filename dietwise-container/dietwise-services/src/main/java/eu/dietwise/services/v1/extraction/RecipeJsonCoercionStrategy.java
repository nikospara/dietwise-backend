package eu.dietwise.services.v1.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public interface RecipeJsonCoercionStrategy {
	String coerce(JsonNode node, ObjectMapper objectMapper);
}
