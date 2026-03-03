package eu.dietwise.v1.model;

import java.util.Optional;

import eu.dietwise.common.types.Nullable;
import eu.dietwise.v1.types.IngredientId;
import eu.dietwise.v1.types.Recommendation;
import eu.dietwise.v1.types.RuleId;
import org.immutables.value.Value;

@Value.Immutable
public interface Suggestion extends SuggestionTemplate {
	AppliesTo getTarget();

	RuleId getRuleId();

	Recommendation getRecommendation();

	Optional<String> getRationale();

	@Nullable
	String getText();
}
