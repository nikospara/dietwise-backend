package eu.dietwise.dao.impl.suggestions;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Root;

import eu.dietwise.common.dao.EntityNotFoundException;
import eu.dietwise.common.dao.StaleVersionException;
import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.types.ReferenceDetails;
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

	@Override
	public Uni<Map<UUID, String>> findStagedNames(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<TriggerIngredientWcEntity> root = q.from(TriggerIngredientWcEntity.class);
		q.select(cb.tuple(root.get(TriggerIngredientWcEntity_.id), root.get(TriggerIngredientWcEntity_.name)));
		return em.createQuery(q).getResultList().map(rows -> rows.stream()
				.collect(java.util.stream.Collectors.toMap(t -> t.get(0, UUID.class), t -> t.get(1, String.class))));
	}

	@Override
	public Uni<ReferenceDetails> findEditableById(ReactivePersistenceContext em, UUID id) {
		return em.find(TriggerIngredientWcEntity.class, id).flatMap(mirror -> mirror != null
				? Uni.createFrom().item(new ReferenceDetails(mirror.getName(), mirror.getExplanationForLlm(), mirror.getVersion()))
				: em.find(TriggerIngredientEntity.class, id).map(master -> {
					if (master == null) {
						throw new EntityNotFoundException(TriggerIngredientEntity.class, id);
					}
					return new ReferenceDetails(master.getName(), master.getExplanationForLlm(), 0L);
				}));
	}

	@Override
	public Uni<Void> editTriggerIngredient(ReactivePersistenceTxContext tx, UUID id, String name, String explanationForLlm, long baseVersion) {
		return tx.find(TriggerIngredientWcEntity.class, id).flatMap(existing ->
				tx.find(TriggerIngredientEntity.class, id).flatMap(master -> applyEdit(tx, id, name, explanationForLlm, baseVersion, existing, master)));
	}

	private Uni<Void> applyEdit(ReactivePersistenceTxContext tx, UUID id, String name, String explanationForLlm, long baseVersion, TriggerIngredientWcEntity existing, TriggerIngredientEntity master) {
		if (existing == null && master == null) {
			return Uni.createFrom().failure(new EntityNotFoundException(TriggerIngredientEntity.class, id));
		}
		boolean matchesMaster = master != null
				&& Objects.equals(name, master.getName())
				&& Objects.equals(explanationForLlm, master.getExplanationForLlm());
		if (existing == null) {
			if (baseVersion != 0L) {
				return Uni.createFrom().failure(new StaleVersionException(TriggerIngredientEntity.class, id));
			}
			return matchesMaster ? Uni.createFrom().voidItem() : seedStaged(tx, id, name, explanationForLlm);
		}
		return matchesMaster
				? deleteStaged(tx, id, baseVersion)
				: bumpStaged(tx, id, name, explanationForLlm, baseVersion);
	}

	private Uni<Void> seedStaged(ReactivePersistenceTxContext tx, UUID id, String name, String explanationForLlm) {
		var entity = new TriggerIngredientWcEntity();
		entity.setId(id);
		entity.setName(name);
		entity.setExplanationForLlm(explanationForLlm);
		entity.setVersion(1L);
		return tx.persist(entity).replaceWithVoid();
	}

	private Uni<Void> bumpStaged(ReactivePersistenceTxContext tx, UUID id, String name, String explanationForLlm, long baseVersion) {
		var cb = tx.getCriteriaBuilder();
		CriteriaUpdate<TriggerIngredientWcEntity> cu = cb.createCriteriaUpdate(TriggerIngredientWcEntity.class);
		Root<TriggerIngredientWcEntity> wc = cu.getRoot();
		cu.set(wc.get(TriggerIngredientWcEntity_.name), name);
		cu.set(wc.get(TriggerIngredientWcEntity_.explanationForLlm), explanationForLlm);
		cu.set(wc.get(TriggerIngredientWcEntity_.version), cb.sum(wc.get(TriggerIngredientWcEntity_.version), 1L));
		cu.where(cb.and(
				cb.equal(wc.get(TriggerIngredientWcEntity_.id), id),
				cb.equal(wc.get(TriggerIngredientWcEntity_.version), baseVersion)
		));
		return tx.createUpdate(cu).execute().flatMap(rowsAffected -> rowsAffected == 1
				? Uni.createFrom().voidItem()
				: Uni.createFrom().failure(new StaleVersionException(TriggerIngredientEntity.class, id)));
	}

	private Uni<Void> deleteStaged(ReactivePersistenceTxContext tx, UUID id, long baseVersion) {
		var cb = tx.getCriteriaBuilder();
		CriteriaDelete<TriggerIngredientWcEntity> cd = cb.createCriteriaDelete(TriggerIngredientWcEntity.class);
		Root<TriggerIngredientWcEntity> wc = cd.getRoot();
		cd.where(cb.and(
				cb.equal(wc.get(TriggerIngredientWcEntity_.id), id),
				cb.equal(wc.get(TriggerIngredientWcEntity_.version), baseVersion)
		));
		return tx.createDelete(cd).execute().flatMap(rowsAffected -> rowsAffected == 1
				? Uni.createFrom().voidItem()
				: Uni.createFrom().failure(new StaleVersionException(TriggerIngredientEntity.class, id)));
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
