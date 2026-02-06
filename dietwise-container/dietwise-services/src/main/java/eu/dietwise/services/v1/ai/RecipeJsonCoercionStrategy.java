package eu.dietwise.services.v1.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public interface RecipeJsonCoercionStrategy {
	String coerce(JsonNode node, ObjectMapper objectMapper);
}
