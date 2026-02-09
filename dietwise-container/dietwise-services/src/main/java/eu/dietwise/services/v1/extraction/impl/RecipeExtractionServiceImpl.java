package eu.dietwise.services.v1.extraction.impl;

import java.util.ArrayDeque;
import java.util.Deque;
import jakarta.enterprise.context.ApplicationScoped;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.dietwise.services.v1.extraction.RecipeExtractionAiService;
import eu.dietwise.services.v1.extraction.RecipeExtractionService;
import eu.dietwise.services.v1.extraction.RecipeJsonNormalizer;
import eu.dietwise.v1.model.Recipe;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class RecipeExtractionServiceImpl implements RecipeExtractionService {
	private static final Logger LOG = LoggerFactory.getLogger(RecipeExtractionServiceImpl.class);

	private final RecipeExtractionAiService extractionAiService;
	private final ObjectMapper objectMapper;
	private final RecipeJsonNormalizer jsonNormalizer;

	public RecipeExtractionServiceImpl(RecipeExtractionAiService extractionAiService, ObjectMapper objectMapper, RecipeJsonNormalizer jsonNormalizer) {
		this.extractionAiService = extractionAiService;
		this.objectMapper = objectMapper;
		this.jsonNormalizer = jsonNormalizer;
	}

	@Override
	public Uni<Recipe> extractRecipeFromMarkdown(String markdown) {
		return Uni.createFrom().item(() -> extractionAiService.extractRecipeFromMarkdown(markdown))
				.runSubscriptionOn(Infrastructure.getDefaultExecutor())
				.map(this::parseRecipeWithRepair);
	}

	@Override
	public Uni<Recipe> extractRecipeFromHtml(String html) {
		return Uni.createFrom().item(() -> extractionAiService.extractRecipeFromHtml(html))
				.runSubscriptionOn(Infrastructure.getDefaultExecutor())
				.map(this::parseRecipeWithRepair);
	}

	private Recipe parseRecipeWithRepair(String response) {
		try {
			return parseRecipe(jsonNormalizer.normalize(response));
		} catch (JsonProcessingException e) {
			String repaired = repairJson(response);
			if (!repaired.equals(response)) {
				try {
					LOG.debug("Repaired malformed recipe JSON from LLM");
					return parseRecipe(jsonNormalizer.normalize(repaired));
				} catch (JsonProcessingException ignored) {
					// fall through to throw the original exception
				}
			}
			throw new IllegalArgumentException("Invalid recipe JSON from LLM", e);
		}
	}

	private Recipe parseRecipe(String json) throws JsonProcessingException {
		return objectMapper.readValue(json, Recipe.class);
	}

	private String repairJson(String text) {
		if (text == null) return "";
		String trimmed = stripMarkdownFence(text.trim());
		String candidate = extractJsonObject(trimmed);
		return appendMissingClosers(candidate);
	}

	private String stripMarkdownFence(String text) {
		if (!text.startsWith("```")) return text;
		int firstNewline = text.indexOf('\n');
		if (firstNewline == -1) return text;
		String withoutFirst = text.substring(firstNewline + 1);
		int lastFence = withoutFirst.lastIndexOf("```");
		return lastFence >= 0 ? withoutFirst.substring(0, lastFence).trim() : withoutFirst.trim();
	}

	private String extractJsonObject(String text) {
		int start = text.indexOf('{');
		if (start < 0) return text;
		int end = findFirstCompleteJsonObjectEnd(text, start);
		if (end >= start) return text.substring(start, end + 1);
		return text.substring(start);
	}

	private int findFirstCompleteJsonObjectEnd(String text, int start) {
		Deque<Character> stack = new ArrayDeque<>();
		boolean inString = false;
		boolean escaping = false;

		for (int i = start; i < text.length(); i++) {
			char c = text.charAt(i);
			if (escaping) {
				escaping = false;
				continue;
			}
			if (c == '\\') {
				escaping = true;
				continue;
			}
			if (c == '"') {
				inString = !inString;
				continue;
			}
			if (inString) continue;

			switch (c) {
				case '{' -> stack.push('}');
				case '[' -> stack.push(']');
				case '}', ']' -> {
					if (!stack.isEmpty() && stack.peek() == c) {
						stack.pop();
						if (stack.isEmpty()) return i;
					}
				}
				default -> {
				}
			}
		}
		return -1;
	}

	private String appendMissingClosers(String text) {
		Deque<Character> stack = new ArrayDeque<>();
		boolean inString = false;
		boolean escaping = false;

		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (escaping) {
				escaping = false;
				continue;
			}
			if (c == '\\') {
				escaping = true;
				continue;
			}
			if (c == '"') {
				inString = !inString;
				continue;
			}
			if (inString) continue;

			switch (c) {
				case '{' -> stack.push('}');
				case '[' -> stack.push(']');
				case '}', ']' -> {
					if (!stack.isEmpty() && stack.peek() == c) {
						stack.pop();
					}
				}
				default -> {
				}
			}
		}

		if (stack.isEmpty()) return text;
		StringBuilder sb = new StringBuilder(text);
		while (!stack.isEmpty()) sb.append(stack.pop());
		return sb.toString();
	}
}
