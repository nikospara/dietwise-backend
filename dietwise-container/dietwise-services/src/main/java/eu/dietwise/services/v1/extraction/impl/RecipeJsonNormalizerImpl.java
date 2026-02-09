package eu.dietwise.services.v1.extraction.impl;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import eu.dietwise.services.v1.extraction.RecipeJsonCoercionStrategy;
import eu.dietwise.services.v1.extraction.RecipeJsonNormalizer;

@ApplicationScoped
public class RecipeJsonNormalizerImpl implements RecipeJsonNormalizer {
	private final ObjectMapper objectMapper;
	private final RecipeJsonCoercionStrategy coercionStrategy;

	public RecipeJsonNormalizerImpl(ObjectMapper objectMapper, RecipeJsonCoercionStrategy coercionStrategy) {
		this.objectMapper = objectMapper;
		this.coercionStrategy = coercionStrategy;
	}

	@Override
	public String normalize(String json) {
		try {
			JsonNode root = objectMapper.readTree(json);
			if (!root.isObject()) return json;
			ObjectNode obj = (ObjectNode) root;
			normalizeArrayField(obj, "recipeIngredients");
			normalizeArrayField(obj, "recipeInstructions");
			return objectMapper.writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			return json;
		}
	}

	private void normalizeArrayField(ObjectNode obj, String fieldName) {
		JsonNode node = obj.get(fieldName);
		if (node == null || node.isNull()) return;

		if (node.isArray()) {
			List<String> normalized = new ArrayList<>();
			for (JsonNode item : node) {
				normalized.add(coerceToString(item));
			}
			ArrayNode array = objectMapper.createArrayNode();
			for (String value : normalized) array.add(value);
			obj.set(fieldName, array);
		} else {
			ArrayNode array = objectMapper.createArrayNode();
			array.add(coerceToString(node));
			obj.set(fieldName, array);
		}
	}

	private String coerceToString(JsonNode node) {
		String value = coercionStrategy.coerce(node, objectMapper);
		return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
	}
}
