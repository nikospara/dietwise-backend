package eu.dietwise.dao.impl.suggestions;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientEntity;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientEntity_;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientTranslationEntity;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientTranslationEntity_;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientWcEntity;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientWcEntity_;
import eu.dietwise.dao.suggestions.TriggerIngredientDao;
import eu.dietwise.services.model.suggestions.ImmutableTriggerIngredient;
import eu.dietwise.services.model.suggestions.TriggerIngredient;
import eu.dietwise.v1.types.RecipeLanguage;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class TriggerIngredientDaoImpl implements TriggerIngredientDao {
	@Override
	public Uni<List<TriggerIngredient>> findAll(ReactivePersistenceContext em, RecipeLanguage lang) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(TriggerIngredientEntity.class);
		Root<TriggerIngredientEntity> triggerIngredient = q.from(TriggerIngredientEntity.class);
		q.select(triggerIngredient);
		return em.createQuery(q).getResultList()
				.flatMap(list -> loadTranslationsByTriggerIngredientId(em, lang)
						.map(translationsById -> toTriggerIngredientList(list, translationsById)));
	}

	@Override
	public Uni<List<ReferenceOption>> listOptions(ReactivePersistenceContext em) {
		return masterOptions(em).flatMap(master -> mirrorOptions(em).map(mirror -> mergeOptions(master, mirror)));
	}

	@Override
	public Uni<UUID> createTriggerIngredient(ReactivePersistenceTxContext tx, String name) {
		var entity = new TriggerIngredientWcEntity();
		UUID id = UUID.randomUUID();
		entity.setId(id);
		entity.setName(name);
		entity.setVersion(1L);
		return tx.persist(entity).replaceWith(id);
	}

	private Uni<List<ReferenceOption>> masterOptions(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<TriggerIngredientEntity> root = q.from(TriggerIngredientEntity.class);
		q.select(cb.tuple(root.get(TriggerIngredientEntity_.id), root.get(TriggerIngredientEntity_.name)));
		return em.createQuery(q).getResultList().map(TriggerIngredientDaoImpl::toOptions);
	}

	private Uni<List<ReferenceOption>> mirrorOptions(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<TriggerIngredientWcEntity> root = q.from(TriggerIngredientWcEntity.class);
		q.select(cb.tuple(root.get(TriggerIngredientWcEntity_.id), root.get(TriggerIngredientWcEntity_.name)));
		return em.createQuery(q).getResultList().map(TriggerIngredientDaoImpl::toOptions);
	}

	private static List<ReferenceOption> toOptions(List<Tuple> rows) {
		return rows.stream().map(t -> new ReferenceOption(t.get(0, UUID.class), t.get(1, String.class))).toList();
	}

	private static List<ReferenceOption> mergeOptions(List<ReferenceOption> master, List<ReferenceOption> mirror) {
		Map<UUID, ReferenceOption> byId = new LinkedHashMap<>();
		master.forEach(option -> byId.put(option.id(), option));
		mirror.forEach(option -> byId.put(option.id(), option));
		return byId.values().stream().sorted(Comparator.comparing(ReferenceOption::name)).toList();
	}

	private Uni<Map<UUID, TriggerIngredientTranslationEntity>> loadTranslationsByTriggerIngredientId(
			ReactivePersistenceContext em,
			RecipeLanguage lang
	) {
		if (lang == RecipeLanguage.EN) {
			return Uni.createFrom().item(Map.of());
		}
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(TriggerIngredientTranslationEntity.class);
		var translation = q.from(TriggerIngredientTranslationEntity.class);
		q.select(translation).where(cb.equal(translation.get(TriggerIngredientTranslationEntity_.lang), lang));
		return em.createQuery(q)
				.getResultList()
				.map(list -> list.stream().collect(java.util.stream.Collectors.toMap(
						t -> t.getTriggerIngredient().getId(),
						t -> t
				)));
	}

	private static List<TriggerIngredient> toTriggerIngredientList(
			List<TriggerIngredientEntity> list,
			Map<UUID, TriggerIngredientTranslationEntity> translationsById
	) {
		return list.stream().map(e -> toTriggerIngredient(e, translationsById.get(e.getId()))).toList();
	}

	private static TriggerIngredient toTriggerIngredient(TriggerIngredientEntity e, TriggerIngredientTranslationEntity t) {
		return ImmutableTriggerIngredient.builder()
				.id(new UuidTriggerIngredientId(e.getId()))
				.name(t != null && t.getName() != null ? t.getName() : e.getName())
				.explanationForLlm(Optional.ofNullable(t != null && t.getExplanationForLlm() != null ? t.getExplanationForLlm() : e.getExplanationForLlm()))
				.build();
	}
}
