package eu.dietwise.services.v1.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class RecipeJsonCoercionStrategyTest {
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void compactJsonCoercionSerializesObjects() throws Exception {
		RecipeJsonCoercionStrategy strategy = new CompactJsonCoercionStrategy();
		JsonNode node = objectMapper.readTree("""
				{
				  "amount": "1 cup",
				  "item": "flour"
				}
				""");

		String result = strategy.coerce(node, objectMapper);

		assertThat(result).isEqualTo("{\"amount\":\"1 cup\",\"item\":\"flour\"}");
	}

	@Test
	void compactJsonCoercionHandlesScalarsAndNull() throws Exception {
		RecipeJsonCoercionStrategy strategy = new CompactJsonCoercionStrategy();

		assertThat(strategy.coerce(null, objectMapper)).isEqualTo("");
		assertThat(strategy.coerce(objectMapper.readTree("5"), objectMapper)).isEqualTo("5");
		assertThat(strategy.coerce(objectMapper.readTree("true"), objectMapper)).isEqualTo("true");
		assertThat(strategy.coerce(objectMapper.readTree("\"salt\""), objectMapper)).isEqualTo("salt");
		assertThat(strategy.coerce(objectMapper.readTree("null"), objectMapper)).isEqualTo("");
	}

	@Test
	void joinValuesCoercionFlattensNestedValues() throws Exception {
		RecipeJsonCoercionStrategy strategy = new JoinValuesCoercionStrategy();
		JsonNode node = objectMapper.readTree("""
				{
				  "amount": "2",
				  "unit": "tbsp",
				  "extra": {"note": "sifted"}
				}
				""");

		String result = strategy.coerce(node, objectMapper);

		assertThat(result).isEqualTo("2 tbsp sifted");
	}

	@Test
	void joinValuesCoercionHandlesMixedArray() throws Exception {
		RecipeJsonCoercionStrategy strategy = new JoinValuesCoercionStrategy();
		JsonNode node = objectMapper.readTree("""
				["1", 2, true, {"unit": "tsp"}]
				""");

		String result = strategy.coerce(node, objectMapper);

		assertThat(result).isEqualTo("1 2 true tsp");
	}

	@Test
	void keyValuePairsCoercionUsesLabels() throws Exception {
		RecipeJsonCoercionStrategy strategy = new KeyValuePairsCoercionStrategy();
		JsonNode node = objectMapper.readTree("""
				{
				  "amount": "3",
				  "unit": "cups",
				  "item": "sugar"
				}
				""");

		String result = strategy.coerce(node, objectMapper);

		assertThat(result).isEqualTo("amount: 3, unit: cups, item: sugar");
	}

	@Test
	void keyValuePairsCoercionHandlesScalarsAndNull() throws Exception {
		RecipeJsonCoercionStrategy strategy = new KeyValuePairsCoercionStrategy();

		assertThat(strategy.coerce(null, objectMapper)).isEqualTo("");
		assertThat(strategy.coerce(objectMapper.readTree("7"), objectMapper)).isEqualTo("7");
		assertThat(strategy.coerce(objectMapper.readTree("false"), objectMapper)).isEqualTo("false");
		assertThat(strategy.coerce(objectMapper.readTree("\"pepper\""), objectMapper)).isEqualTo("pepper");
		assertThat(strategy.coerce(objectMapper.readTree("null"), objectMapper)).isEqualTo("");
	}

	@Test
	void keyValuePairsCoercionFlattensArrayItems() throws Exception {
		RecipeJsonCoercionStrategy strategy = new KeyValuePairsCoercionStrategy();
		JsonNode node = objectMapper.readTree("""
				[
				  {"item": "eggs", "count": 2},
				  {"item": "milk", "amount": "1 cup"}
				]
				""");

		String result = strategy.coerce(node, objectMapper);

		assertThat(result).isEqualTo("item: eggs, count: 2, item: milk, amount: 1 cup");
	}
}
