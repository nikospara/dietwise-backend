package eu.dietwise.dao.impl.suggestions;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientEntity_;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientSeasonalityEntity;
import eu.dietwise.dao.suggestions.AlternativeIngredientDao;
import eu.dietwise.services.model.suggestions.AlternativeIngredient;
import eu.dietwise.services.model.suggestions.ImmutableAlternativeIngredient;
import eu.dietwise.v1.types.Country;
import eu.dietwise.v1.types.ImmutableSeasonality;
import eu.dietwise.v1.types.Seasonality;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class AlternativeIngredientDaoImpl implements AlternativeIngredientDao {
	@Override
	public Uni<List<AlternativeIngredient>> findAll(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(AlternativeIngredientEntity.class);
		Root<AlternativeIngredientEntity> alternativeIngredient = q.from(AlternativeIngredientEntity.class);
		alternativeIngredient.fetch(AlternativeIngredientEntity_.seasonalityByCountry, JoinType.LEFT);
		q.select(alternativeIngredient);
		return em.createQuery(q).getResultList().map(AlternativeIngredientDaoImpl::toAlternativeIngredientList);
	}

	private static List<AlternativeIngredient> toAlternativeIngredientList(List<AlternativeIngredientEntity> list) {
		return list.stream().map(AlternativeIngredientDaoImpl::toAlternativeIngredient).toList();
	}

	private static AlternativeIngredient toAlternativeIngredient(AlternativeIngredientEntity e) {
		return ImmutableAlternativeIngredient.builder()
				.id(new UuidAlternativeIngredientId(e.getId()))
				.name(e.getName())
				.explanationForLlm(Optional.ofNullable(e.getExplanationForLlm()))
				.seasonalityByCountry(toSeasonalityByCountry(e))
				.build();
	}

	private static Optional<Map<Country, Seasonality>> toSeasonalityByCountry(AlternativeIngredientEntity e) {
		if (e.getSeasonalityByCountry() == null || e.getSeasonalityByCountry().isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(e.getSeasonalityByCountry().stream().collect(Collectors.toUnmodifiableMap(
				AlternativeIngredientSeasonalityEntity::getCountry,
				s -> ImmutableSeasonality.builder().monthFrom(s.getMonthFrom()).monthTo(s.getMonthTo()).build()
		)));
	}
}
