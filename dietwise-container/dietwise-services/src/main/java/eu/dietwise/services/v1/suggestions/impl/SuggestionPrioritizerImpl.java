package eu.dietwise.services.v1.suggestions.impl;

import static eu.dietwise.common.utils.UniComprehensions.forcm;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.common.v1.types.HasUserId;
import eu.dietwise.dao.PersonalInfoDao;
import eu.dietwise.dao.recommendations.RecommendationDao;
import eu.dietwise.services.nondomain.DateTimeService;
import eu.dietwise.services.v1.suggestions.SuggestionPrioritizer;
import eu.dietwise.v1.model.PersonalInfo;
import eu.dietwise.v1.model.Suggestion;
import eu.dietwise.v1.types.BiologicalGender;
import eu.dietwise.v1.types.Recommendation;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class SuggestionPrioritizerImpl implements SuggestionPrioritizer {
	private final DateTimeService dateTimeService;
	private final PersonalInfoDao personalInfoDao;
	private final RecommendationDao recommendationDao;

	public SuggestionPrioritizerImpl(DateTimeService dateTimeService, PersonalInfoDao personalInfoDao, RecommendationDao recommendationDao) {
		this.dateTimeService = dateTimeService;
		this.personalInfoDao = personalInfoDao;
		this.recommendationDao = recommendationDao;
	}

	@Override
	public Uni<List<Suggestion>> prioritizeSuggestions(ReactivePersistenceContext em, HasUserId hasUserId, List<Suggestion> suggestions) {
		return forcm(
				personalInfoDao.findByUser(em, hasUserId),
				calculateWeights(em),
				orderSuggestionsAccordingToWeights(suggestions)
		);
	}

	private Function<? super PersonalInfo, Uni<? extends Map<Recommendation, BigDecimal>>> calculateWeights(ReactivePersistenceContext em) {
		return personalInfo -> {
			Integer age = Optional.ofNullable(personalInfo).map(PersonalInfo::getYearOfBirth).map(yob -> dateTimeService.getNow().getYear() - yob).orElse(null);
			BiologicalGender gender = Optional.ofNullable(personalInfo).map(PersonalInfo::getGender).orElse(null);
			// TODO Introduce a RecommendationService that caches
			if (age != null && gender != null) {
				return recommendationDao.findRecommendations(em, age, gender);
			} else if (age != null) {
				return recommendationDao.findRecommendations(em, age);
			} else if (gender != null) {
				return recommendationDao.findRecommendations(em, gender);
			} else {
				return recommendationDao.findRecommendations(em);
			}
		};
	}

	private Function<? super Map<Recommendation, BigDecimal>, ? extends List<Suggestion>> orderSuggestionsAccordingToWeights(List<Suggestion> suggestions) {
		return weights -> {
			Comparator<Suggestion> comparator = Comparator.comparing(s -> weights.getOrDefault(s.getRecommendation(), BigDecimal.ZERO));
			return suggestions.stream().sorted(comparator.reversed()).toList();
		};
	}
}
