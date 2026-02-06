package eu.dietwise.services.v1.ai;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JoinValuesCoercionStrategy implements RecipeJsonCoercionStrategy {
	@Override
	public String coerce(JsonNode node, ObjectMapper objectMapper) {
		if (node == null || node.isNull()) return "";
		if (node.isTextual()) return node.textValue();
		if (node.isNumber() || node.isBoolean()) return node.asText();
		List<String> parts = new ArrayList<>();
		collectText(node, parts);
		if (parts.isEmpty()) return node.asText();
		return String.join(" ", parts);
	}

	private void collectText(JsonNode node, List<String> parts) {
		if (node == null || node.isNull()) return;
		if (node.isTextual()) {
			parts.add(node.textValue());
			return;
		}
		if (node.isNumber() || node.isBoolean()) {
			parts.add(node.asText());
			return;
		}
		if (node.isArray()) {
			for (JsonNode item : node) collectText(item, parts);
			return;
		}
		if (node.isObject()) {
			node.properties().forEach(entry -> collectText(entry.getValue(), parts));
		}
	}
}
