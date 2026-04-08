package eu.dietwise.v1.model;

import java.util.Optional;
import java.util.Set;

import eu.dietwise.common.types.Nullable;
import eu.dietwise.v1.types.Cost;
import eu.dietwise.v1.types.Recommendation;
import eu.dietwise.v1.types.RecommendationComponentName;
import eu.dietwise.v1.types.RuleId;
import eu.dietwise.v1.types.Seasonality;
import eu.dietwise.v1.types.SuggestionStats;
import org.immutables.value.Value;

@Value.Immutable
public interface Suggestion extends SuggestionTemplate {
	AppliesTo getTarget();

	RuleId getRuleId();

	Recommendation getRecommendation();

	Optional<Seasonality> getSeasonality();

	Optional<Cost> getCost();

	Optional<String> getRationale();

	Set<RecommendationComponentName> getAlternativeComponentNames();

	@Nullable
	SuggestionStats getTotalSuggestionStats();

	@Nullable
	SuggestionStats getUserSuggestionStats();

	@Nullable
	String getText();
}
