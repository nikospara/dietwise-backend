package eu.dietwise.dao.impl.suggestions;

import java.util.List;
import java.util.Optional;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.Root;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientEntity;
import eu.dietwise.dao.suggestions.AlternativeIngredientDao;
import eu.dietwise.services.model.suggestions.AlternativeIngredient;
import eu.dietwise.services.model.suggestions.ImmutableAlternativeIngredient;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class AlternativeIngredientDaoImpl implements AlternativeIngredientDao {
	@Override
	public Uni<List<AlternativeIngredient>> findAll(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(AlternativeIngredientEntity.class);
		Root<AlternativeIngredientEntity> alternativeIngredient = q.from(AlternativeIngredientEntity.class);
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
				.build();
	}
}
