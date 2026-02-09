package eu.dietwise.services.v1.extraction.impl;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.dietwise.services.v1.extraction.RecipeJsonCoercionStrategy;

public class KeyValuePairsCoercionStrategy implements RecipeJsonCoercionStrategy {
	private final CompactJsonCoercionStrategy compactStrategy = new CompactJsonCoercionStrategy();

	@Override
	public String coerce(JsonNode node, ObjectMapper objectMapper) {
		if (node == null || node.isNull()) return "";
		if (node.isTextual()) return node.textValue();
		if (node.isNumber() || node.isBoolean()) return node.asText();
		if (node.isArray()) {
			List<String> parts = new ArrayList<>();
			for (JsonNode item : node) parts.add(coerce(item, objectMapper));
			return String.join(", ", parts);
		}
		if (node.isObject()) {
			List<String> parts = new ArrayList<>();
			node.properties().forEach(entry -> parts.add(entry.getKey() + ": " + valueAsText(entry.getValue(), objectMapper)));
			return String.join(", ", parts);
		}
		return node.asText();
	}

	private String valueAsText(JsonNode node, ObjectMapper objectMapper) {
		return compactStrategy.coerce(node, objectMapper);
	}
}
