package eu.dietwise.services.v1.suggestions.impl;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.services.v1.recommendations.RecommendationService;
import eu.dietwise.services.v1.suggestions.SuggestionPrioritizer;
import eu.dietwise.v1.model.PersonalInfo;
import eu.dietwise.v1.model.Suggestion;
import eu.dietwise.v1.types.Recommendation;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class SuggestionPrioritizerImpl implements SuggestionPrioritizer {
	private final RecommendationService recommendationService;

	public SuggestionPrioritizerImpl(RecommendationService recommendationService) {
		this.recommendationService = recommendationService;
	}

	@Override
	public Uni<List<Suggestion>> prioritizeSuggestions(ReactivePersistenceContext em, PersonalInfo personalInfo, List<Suggestion> suggestions) {
		return recommendationService.findRecommendationWeights(em, personalInfo).map(weights -> orderSuggestionsAccordingToWeights(weights, suggestions));
	}

	private List<Suggestion> orderSuggestionsAccordingToWeights(Map<Recommendation, BigDecimal> weights, List<Suggestion> suggestions) {
		Comparator<Suggestion> comparator = Comparator.comparing(s -> weights.getOrDefault(s.getRecommendation(), BigDecimal.ZERO));
		return suggestions.stream().sorted(comparator.reversed()).toList();
	}
}
