package eu.dietwise.services.v1.recommendations.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.dao.recommendations.RecommendationDao;
import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.services.nondomain.CachedUniValue;
import eu.dietwise.services.nondomain.DateTimeService;
import eu.dietwise.services.v1.recommendations.RecommendationService;
import eu.dietwise.v1.model.PersonalInfo;
import eu.dietwise.v1.types.BiologicalGender;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.Recommendation;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class RecommendationServiceImpl implements RecommendationService {
	/**
	 * Nothing validates the year of birth a user gives, so an age past this one is taken as typed but not remembered:
	 * keeping it would let a user grow the cache without bound.
	 */
	private static final int OLDEST_REMEMBERED_AGE = 150;

	/** The age group and biological gender a set of weights was read for; any of the two may be unknown. */
	private record Profile(Integer age, BiologicalGender gender) {
	}

	private final RecommendationDao recommendationDao;
	private final DateTimeService dateTimeService;
	private final ConcurrentMap<Profile, CachedUniValue<Map<Recommendation, BigDecimal>>> weightsPerProfile = new ConcurrentHashMap<>();
	private final ConcurrentMap<RecipeLanguage, CachedUniValue<List<RecommendationComponent>>> componentsPerLanguage = new ConcurrentHashMap<>();

	public RecommendationServiceImpl(RecommendationDao recommendationDao, DateTimeService dateTimeService) {
		this.recommendationDao = recommendationDao;
		this.dateTimeService = dateTimeService;
	}

	@Override
	public Uni<Map<Recommendation, BigDecimal>> findRecommendationWeights(ReactivePersistenceContext em, PersonalInfo personalInfo) {
		Integer age = Optional.ofNullable(personalInfo).map(PersonalInfo::getYearOfBirth).map(yob -> dateTimeService.getNow().getYear() - yob).orElse(null);
		BiologicalGender gender = Optional.ofNullable(personalInfo).map(PersonalInfo::getGender).orElse(null);
		var profile = new Profile(age, gender);
		if (age != null && (age < 0 || age > OLDEST_REMEMBERED_AGE)) {
			return readWeights(em, profile);
		}
		return weightsPerProfile.computeIfAbsent(profile, _ -> new CachedUniValue<>()).getOrLoad(() -> readWeights(em, profile));
	}

	@Override
	public Uni<List<RecommendationComponent>> listComponentsForScoring(ReactivePersistenceContext em, RecipeLanguage lang) {
		return componentsPerLanguage.computeIfAbsent(lang, _ -> new CachedUniValue<>())
				.getOrLoad(() -> recommendationDao.listAllRecommendationsForScoring(em, lang).map(List::copyOf));
	}

	private Uni<Map<Recommendation, BigDecimal>> readWeights(ReactivePersistenceContext em, Profile profile) {
		Integer age = profile.age();
		BiologicalGender gender = profile.gender();
		Uni<Map<Recommendation, BigDecimal>> weights;
		if (age != null && gender != null) {
			weights = recommendationDao.findRecommendations(em, age, gender);
		} else if (age != null) {
			weights = recommendationDao.findRecommendations(em, age);
		} else if (gender != null) {
			weights = recommendationDao.findRecommendations(em, gender);
		} else {
			weights = recommendationDao.findRecommendations(em);
		}
		return weights.map(Map::copyOf);
	}
}
