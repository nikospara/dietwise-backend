package eu.dietwise.services.v1.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CompactJsonCoercionStrategy implements RecipeJsonCoercionStrategy {
	@Override
	public String coerce(JsonNode node, ObjectMapper objectMapper) {
		if (node == null || node.isNull()) return "";
		if (node.isTextual()) return node.textValue();
		if (node.isNumber() || node.isBoolean()) return node.asText();
		try {
			return objectMapper.writeValueAsString(node);
		} catch (JsonProcessingException e) {
			return node.toString();
		}
	}
}
