package eu.dietwise.dao.impl.suggestions;

import java.util.List;
import java.util.Optional;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.Root;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientEntity;
import eu.dietwise.dao.suggestions.TriggerIngredientDao;
import eu.dietwise.services.model.suggestions.ImmutableTriggerIngredient;
import eu.dietwise.services.model.suggestions.TriggerIngredient;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class TriggerIngredientDaoImpl implements TriggerIngredientDao {
	@Override
	public Uni<List<TriggerIngredient>> findAll(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(TriggerIngredientEntity.class);
		Root<TriggerIngredientEntity> triggerIngredient = q.from(TriggerIngredientEntity.class);
		q.select(triggerIngredient);
		return em.createQuery(q).getResultList().map(TriggerIngredientDaoImpl::toTriggerIngredientList);
	}

	private static List<TriggerIngredient> toTriggerIngredientList(List<TriggerIngredientEntity> list) {
		return list.stream().map(TriggerIngredientDaoImpl::toTriggerIngredient).toList();
	}

	private static TriggerIngredient toTriggerIngredient(TriggerIngredientEntity e) {
		return ImmutableTriggerIngredient.builder()
				.id(new UuidTriggerIngredientId(e.getId()))
				.name(e.getName())
				.explanationForLlm(Optional.ofNullable(e.getExplanationForLlm()))
				.build();
	}
}
