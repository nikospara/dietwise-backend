package eu.dietwise.jaxrs.v1;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import eu.dietwise.services.v1.types.AlternativeIngredientRecommendationGrid;
import eu.dietwise.services.v1.types.RecommendationColumn;
import eu.dietwise.services.v1.types.StagedAlternativeIngredient;
import eu.dietwise.services.v1.types.TranslationState;
import eu.dietwise.v1.types.RecipeLanguage;

/**
 * The substitution-value grid as returned to the backoffice: the Recommendation {@code columns} (in display
 * order) and one {@code ingredients} row per Alternative Ingredient (sorted by name). Each row carries its effective
 * name, per-language translation completeness (language name to state name) and its links to the columns as two id
 * lists — the published-master links and the links carrying a pending change in the Working Copy.
 */
public record AlternativeIngredientGridResponse(
		List<Column> columns,
		List<Ingredient> ingredients
) {
	/**
	 * One grid column: a Recommendation's id, the component for scoring shown in its header and its weight
	 * ({@code ENCOURAGED} or {@code LIMITED}).
	 */
	public record Column(String id, String componentForScoring, String weight) {
		static Column from(RecommendationColumn column) {
			return new Column(column.id().toString(), column.componentForScoring(), column.weight().name());
		}
	}

	/**
	 * One grid row: an Alternative Ingredient's id, effective name, whether a published master row exists, the Working
	 * Copy version a subsequent name/explanation edit must be based on, its per-language translation completeness, and its
	 * master and staged links to the grid columns.
	 */
	public record Ingredient(
			String id,
			String name,
			boolean published,
			long version,
			Map<String, String> translations,
			List<String> linkedRecommendationIds,
			List<String> stagedRecommendationIds
	) {
		static Ingredient from(StagedAlternativeIngredient ingredient) {
			return new Ingredient(
					ingredient.id().toString(),
					ingredient.name(),
					ingredient.published(),
					ingredient.version(),
					toStateNames(ingredient.translations()),
					toIdStrings(ingredient.linkedRecommendationIds()),
					toIdStrings(ingredient.stagedRecommendationIds()));
		}
	}

	public static AlternativeIngredientGridResponse from(AlternativeIngredientRecommendationGrid grid) {
		return new AlternativeIngredientGridResponse(
				grid.columns().stream().map(Column::from).toList(),
				grid.ingredients().stream().map(Ingredient::from).toList());
	}

	private static Map<String, String> toStateNames(Map<RecipeLanguage, TranslationState> states) {
		return states.entrySet().stream().collect(Collectors.toMap(entry -> entry.getKey().name(), entry -> entry.getValue().name()));
	}

	private static List<String> toIdStrings(Set<UUID> ids) {
		return ids.stream().map(UUID::toString).toList();
	}
}
