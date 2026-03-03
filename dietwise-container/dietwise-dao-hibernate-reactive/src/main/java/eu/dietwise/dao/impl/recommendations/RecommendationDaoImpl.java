package eu.dietwise.dao.impl.recommendations;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.Path;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.dao.jpa.recommendations.AgeGroupEntity_;
import eu.dietwise.dao.jpa.recommendations.RecommendationEntity_;
import eu.dietwise.dao.jpa.recommendations.RecommendationValueEntity;
import eu.dietwise.dao.jpa.recommendations.RecommendationValueEntity_;
import eu.dietwise.dao.recommendations.RecommendationDao;
import eu.dietwise.v1.types.BiologicalGender;
import eu.dietwise.v1.types.Recommendation;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import io.smallrye.mutiny.Uni;

/**
 * Implementation of the {@link RecommendationDao} with Hibernate Reactive.
 */
@ApplicationScoped
public class RecommendationDaoImpl implements RecommendationDao {
	private final ReactivePersistenceContextFactory persistenceContextFactory;

	public RecommendationDaoImpl(ReactivePersistenceContextFactory persistenceContextFactory) {
		this.persistenceContextFactory = persistenceContextFactory;
	}

	@Override
	public Uni<Map<Recommendation, BigDecimal>> findRecommendations(int age, BiologicalGender gender) {
		return persistenceContextFactory.withoutTransaction(em -> {
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
		});
	}

	private Recommendation toRecommendation(Tuple tuple) {
		return new RecommendationImpl(tuple.get(0, String.class));
	}
}
