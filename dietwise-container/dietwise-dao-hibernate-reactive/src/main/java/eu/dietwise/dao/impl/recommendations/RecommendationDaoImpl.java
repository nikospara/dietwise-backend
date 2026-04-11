package eu.dietwise.dao.impl.recommendations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.dao.jpa.recommendations.AgeGroupEntity_;
import eu.dietwise.dao.jpa.recommendations.RecommendationEntity;
import eu.dietwise.dao.jpa.recommendations.RecommendationEntity_;
import eu.dietwise.dao.jpa.recommendations.RecommendationTranslationEntity;
import eu.dietwise.dao.jpa.recommendations.RecommendationTranslationEntity_;
import eu.dietwise.dao.jpa.recommendations.RecommendationValueEntity;
import eu.dietwise.dao.jpa.recommendations.RecommendationValueEntity_;
import eu.dietwise.dao.recommendations.RecommendationDao;
import eu.dietwise.services.model.recommendations.ImmutableRecommendationComponent;
import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.v1.types.BiologicalGender;
import eu.dietwise.v1.types.Recommendation;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.impl.RecommendationComponentNameImpl;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import io.smallrye.mutiny.Uni;

/**
 * Implementation of the {@link RecommendationDao} with Hibernate Reactive.
 */
@ApplicationScoped
public class RecommendationDaoImpl implements RecommendationDao {
	@Override
	public Uni<Map<Recommendation, BigDecimal>> findRecommendations(ReactivePersistenceContext em, int age, BiologicalGender gender) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var recommendationValue = q.from(RecommendationValueEntity.class);
		var recommendation = recommendationValue.join(RecommendationValueEntity_.recommendation);
		var ageGroup = recommendationValue.join(RecommendationValueEntity_.ageGroup);
		Path<BigDecimal> value = recommendationValue.get(RecommendationValueEntity_.value);

		q.select(cb.tuple(recommendation.get(RecommendationEntity_.name), value));
		q.where(
				cb.and(
						cb.lessThanOrEqualTo(ageGroup.get(AgeGroupEntity_.min), age),
						cb.greaterThanOrEqualTo(ageGroup.get(AgeGroupEntity_.max), age),
						cb.equal(recommendationValue.get(RecommendationValueEntity_.gender), gender)
				)
		);

		return em.createQuery(q).getResultList()
				.map(values -> values.stream()
						.collect(Collectors.toMap(this::toRecommendation, tuple -> tuple.get(value))));
	}

	@Override
	public Uni<Map<Recommendation, BigDecimal>> findRecommendations(ReactivePersistenceContext em, BiologicalGender gender) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var recommendationValue = q.from(RecommendationValueEntity.class);
		var recommendation = recommendationValue.join(RecommendationValueEntity_.recommendation);
		Path<BigDecimal> value = recommendationValue.get(RecommendationValueEntity_.value);
		Expression<Double> average = cb.avg(value);

		q.select(cb.tuple(recommendation.get(RecommendationEntity_.name), average));
		q.where(cb.equal(recommendationValue.get(RecommendationValueEntity_.gender), gender))
				.groupBy(recommendation);

		return em.createQuery(q).getResultList()
				.map(values -> values.stream()
						.collect(Collectors.toMap(this::toRecommendation, tuple -> BigDecimal.valueOf(tuple.get(average)))));
	}

	@Override
	public Uni<Map<Recommendation, BigDecimal>> findRecommendations(ReactivePersistenceContext em, int age) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var recommendationValue = q.from(RecommendationValueEntity.class);
		var recommendation = recommendationValue.join(RecommendationValueEntity_.recommendation);
		var ageGroup = recommendationValue.join(RecommendationValueEntity_.ageGroup);
		Path<BigDecimal> value = recommendationValue.get(RecommendationValueEntity_.value);
		Expression<Double> average = cb.avg(value);

		q.select(cb.tuple(recommendation.get(RecommendationEntity_.name), average));
		q.where(
				cb.and(
						cb.lessThanOrEqualTo(ageGroup.get(AgeGroupEntity_.min), age),
						cb.greaterThanOrEqualTo(ageGroup.get(AgeGroupEntity_.max), age)
				)
		);
		q.groupBy(recommendation);

		return em.createQuery(q).getResultList()
				.map(values -> values.stream()
						.collect(Collectors.toMap(this::toRecommendation, tuple -> BigDecimal.valueOf(tuple.get(average)))));
	}

	@Override
	public Uni<Map<Recommendation, BigDecimal>> findRecommendations(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var recommendationValue = q.from(RecommendationValueEntity.class);
		var recommendation = recommendationValue.join(RecommendationValueEntity_.recommendation);
		Path<BigDecimal> value = recommendationValue.get(RecommendationValueEntity_.value);
		Expression<Double> average = cb.avg(value);

		q.select(cb.tuple(recommendation.get(RecommendationEntity_.name), average));
		q.groupBy(recommendation);

		return em.createQuery(q).getResultList()
				.map(values -> values.stream()
						.collect(Collectors.toMap(this::toRecommendation, tuple -> BigDecimal.valueOf(tuple.get(average)))));
	}

	@Override
	public Uni<List<RecommendationComponent>> listAllRecommendationsForScoring(ReactivePersistenceContext em, RecipeLanguage lang) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(RecommendationEntity.class);
		q.from(RecommendationEntity.class);
		return em.createQuery(q).getResultList()
				.flatMap(values -> loadTranslationsByRecommendationId(em, lang)
						.map(translationsById -> values.stream()
								.map(value -> toRecommendationComponent(value, translationsById.get(value.getId())))
								.toList()));
	}

	private Uni<Map<UUID, RecommendationTranslationEntity>> loadTranslationsByRecommendationId(
			ReactivePersistenceContext em,
			RecipeLanguage lang
	) {
		if (lang == RecipeLanguage.EN) {
			return Uni.createFrom().item(Map.of());
		}
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(RecommendationTranslationEntity.class);
		var translation = q.from(RecommendationTranslationEntity.class);
		q.select(translation).where(cb.equal(translation.get(RecommendationTranslationEntity_.lang), lang));
		return em.createQuery(q).getResultList()
				.map(values -> values.stream().collect(Collectors.toMap(
						t -> t.getRecommendation().getId(),
						t -> t
				)));
	}

	private Recommendation toRecommendation(Tuple tuple) {
		return new RecommendationImpl(tuple.get(0, String.class));
	}

	private RecommendationComponent toRecommendationComponent(RecommendationEntity e, RecommendationTranslationEntity t) {
		return ImmutableRecommendationComponent.builder()
				.recommendation(new RecommendationImpl(t != null && t.getName() != null ? t.getName() : e.getName()))
				.componentForScoring(new RecommendationComponentNameImpl(
						t != null && t.getComponentForScoring() != null ? t.getComponentForScoring() : e.getComponentForScoring()))
				.weight(e.getWeight())
				.explanationForLlm(Optional.ofNullable(t != null && t.getExplanationForLlm() != null ? t.getExplanationForLlm() : e.getExplanationForLlm()))
				.build();
	}
}
