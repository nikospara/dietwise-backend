package eu.dietwise.dao.impl.suggestions;

import static eu.dietwise.common.utils.UniComprehensions.forc;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Predicate;
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
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientTranslationEntityId;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientTranslationEntity_;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientTranslationWcEntity;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientTranslationWcEntityId;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientTranslationWcEntity_;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientWcEntity;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientWcEntity_;
import eu.dietwise.dao.suggestions.TriggerIngredientDao;
import eu.dietwise.services.model.suggestions.ImmutableTriggerIngredient;
import eu.dietwise.services.model.suggestions.TranslationLangs;
import eu.dietwise.services.model.suggestions.TriggerIngredient;
import eu.dietwise.v1.types.RecipeLanguage;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class TriggerIngredientDaoImpl implements TriggerIngredientDao {
	private static final List<RecipeLanguage> TRANSLATABLE_LANGUAGES =
			Arrays.stream(RecipeLanguage.values()).filter(lang -> lang != RecipeLanguage.EN).toList();

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
		return em.find(TriggerIngredientWcEntity.class, id).flatMap(mirror ->
				em.find(TriggerIngredientEntity.class, id).map(master -> {
					if (mirror != null) {
						return new ReferenceDetails(mirror.getName(), mirror.getExplanationForLlm(), mirror.getVersion(), master != null);
					}
					if (master == null) {
						throw new EntityNotFoundException(TriggerIngredientEntity.class, id);
					}
					return new ReferenceDetails(master.getName(), master.getExplanationForLlm(), 0L, true);
				}));
	}

	@Override
	public Uni<Void> editTriggerIngredient(ReactivePersistenceTxContext tx, UUID id, String name, String explanationForLlm, long baseVersion) {
		return tx.find(TriggerIngredientWcEntity.class, id).flatMap(existing ->
				tx.find(TriggerIngredientEntity.class, id).flatMap(master -> applyEdit(tx, id, name, explanationForLlm, baseVersion, existing, master)));
	}

	@Override
	public Uni<Void> revertTriggerIngredient(ReactivePersistenceTxContext tx, UUID id, long baseVersion) {
		return tx.find(TriggerIngredientWcEntity.class, id).flatMap(existing -> existing == null
				? Uni.createFrom().voidItem()
				: tx.find(TriggerIngredientEntity.class, id).flatMap(master -> master == null
						? Uni.createFrom().failure(new EntityNotFoundException(TriggerIngredientEntity.class, id, "No published Trigger Ingredient to revert"))
						: deleteStaged(tx, id, baseVersion)));
	}

	@Override
	public Uni<Map<UUID, TranslationLangs>> findTranslationLangs(ReactivePersistenceContext em) {
		return masterTranslationLangs(em).flatMap(master -> stagedTranslationLangs(em).map(staged -> mergeTranslationLangs(master, staged)));
	}

	private Uni<Map<UUID, Set<RecipeLanguage>>> masterTranslationLangs(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<TriggerIngredientTranslationEntity> t = q.from(TriggerIngredientTranslationEntity.class);
		q.select(cb.tuple(t.get(TriggerIngredientTranslationEntity_.triggerIngredient).get(TriggerIngredientEntity_.id), t.get(TriggerIngredientTranslationEntity_.lang)))
				.where(cb.isNotNull(t.get(TriggerIngredientTranslationEntity_.name)));
		return em.createQuery(q).getResultList().map(TriggerIngredientDaoImpl::toLangsById);
	}

	private Uni<Map<UUID, Set<RecipeLanguage>>> stagedTranslationLangs(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<TriggerIngredientTranslationWcEntity> t = q.from(TriggerIngredientTranslationWcEntity.class);
		q.select(cb.tuple(t.get(TriggerIngredientTranslationWcEntity_.triggerIngredientId), t.get(TriggerIngredientTranslationWcEntity_.lang)));
		return em.createQuery(q).getResultList().map(TriggerIngredientDaoImpl::toLangsById);
	}

	private static Map<UUID, Set<RecipeLanguage>> toLangsById(List<Tuple> rows) {
		Map<UUID, Set<RecipeLanguage>> byId = new HashMap<>();
		for (Tuple row : rows) {
			byId.computeIfAbsent(row.get(0, UUID.class), _ -> EnumSet.noneOf(RecipeLanguage.class))
					.add(row.get(1, RecipeLanguage.class));
		}
		return byId;
	}

	private static Map<UUID, TranslationLangs> mergeTranslationLangs(Map<UUID, Set<RecipeLanguage>> master, Map<UUID, Set<RecipeLanguage>> staged) {
		Set<UUID> ids = new HashSet<>(master.keySet());
		ids.addAll(staged.keySet());
		Map<UUID, TranslationLangs> result = new HashMap<>();
		for (UUID id : ids) {
			result.put(id, new TranslationLangs(
					master.getOrDefault(id, EnumSet.noneOf(RecipeLanguage.class)),
					staged.getOrDefault(id, EnumSet.noneOf(RecipeLanguage.class))));
		}
		return result;
	}

	@Override
	public Uni<Map<RecipeLanguage, ReferenceDetails>> findTranslationsForEdit(ReactivePersistenceContext em, UUID id) {
		return masterTranslationsForEntity(em, id).flatMap(master ->
				stagedTranslationsForEntity(em, id).map(staged -> toEditableTranslations(master, staged)));
	}

	private Uni<Map<RecipeLanguage, TriggerIngredientTranslationEntity>> masterTranslationsForEntity(ReactivePersistenceContext em, UUID id) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(TriggerIngredientTranslationEntity.class);
		Root<TriggerIngredientTranslationEntity> t = q.from(TriggerIngredientTranslationEntity.class);
		q.select(t).where(cb.equal(t.get(TriggerIngredientTranslationEntity_.triggerIngredient).get(TriggerIngredientEntity_.id), id));
		return em.createQuery(q).getResultList().map(list -> list.stream()
				.collect(java.util.stream.Collectors.toMap(TriggerIngredientTranslationEntity::getLang, t2 -> t2)));
	}

	private Uni<Map<RecipeLanguage, TriggerIngredientTranslationWcEntity>> stagedTranslationsForEntity(ReactivePersistenceContext em, UUID id) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(TriggerIngredientTranslationWcEntity.class);
		Root<TriggerIngredientTranslationWcEntity> t = q.from(TriggerIngredientTranslationWcEntity.class);
		q.select(t).where(cb.equal(t.get(TriggerIngredientTranslationWcEntity_.triggerIngredientId), id));
		return em.createQuery(q).getResultList().map(list -> list.stream()
				.collect(java.util.stream.Collectors.toMap(TriggerIngredientTranslationWcEntity::getLang, t2 -> t2)));
	}

	private static Map<RecipeLanguage, ReferenceDetails> toEditableTranslations(
			Map<RecipeLanguage, TriggerIngredientTranslationEntity> master,
			Map<RecipeLanguage, TriggerIngredientTranslationWcEntity> staged
	) {
		Map<RecipeLanguage, ReferenceDetails> result = new EnumMap<>(RecipeLanguage.class);
		for (RecipeLanguage lang : TRANSLATABLE_LANGUAGES) {
			TriggerIngredientTranslationEntity m = master.get(lang);
			TriggerIngredientTranslationWcEntity wc = staged.get(lang);
			if (wc != null) {
				result.put(lang, new ReferenceDetails(wc.getName(), wc.getExplanationForLlm(), wc.getVersion(), m != null));
			} else {
				result.put(lang, m != null
						? new ReferenceDetails(m.getName(), m.getExplanationForLlm(), 0L, true)
						: new ReferenceDetails(null, null, 0L, false));
			}
		}
		return result;
	}

	@Override
	public Uni<Void> stageTranslation(ReactivePersistenceTxContext tx, UUID id, RecipeLanguage lang, String name, String explanationForLlm, long baseVersion) {
		return forc(
				tx.find(TriggerIngredientTranslationWcEntity.class, new TriggerIngredientTranslationWcEntityId(id, lang)),
				_ -> tx.find(TriggerIngredientTranslationEntity.class, new TriggerIngredientTranslationEntityId(id, lang)),
				(existing, master) -> applyTranslationEdit(tx, id, lang, name, explanationForLlm, baseVersion, existing, master)
		);
	}

	private Uni<Void> applyTranslationEdit(ReactivePersistenceTxContext tx, UUID id, RecipeLanguage lang, String name, String explanationForLlm, long baseVersion, TriggerIngredientTranslationWcEntity existing, TriggerIngredientTranslationEntity master) {
		String masterName = master == null ? null : master.getName();
		String masterExplanation = master == null ? null : master.getExplanationForLlm();
		boolean matchesMaster = Objects.equals(name, masterName) && Objects.equals(explanationForLlm, masterExplanation);
		if (existing == null) {
			if (baseVersion != 0L) {
				return Uni.createFrom().failure(new StaleVersionException(TriggerIngredientTranslationEntity.class, id));
			}
			return matchesMaster ? Uni.createFrom().voidItem() : seedStagedTranslation(tx, id, lang, name, explanationForLlm);
		}
		return matchesMaster
				? deleteStagedTranslation(tx, id, lang, baseVersion)
				: bumpStagedTranslation(tx, id, lang, name, explanationForLlm, baseVersion);
	}

	private Uni<Void> seedStagedTranslation(ReactivePersistenceTxContext tx, UUID id, RecipeLanguage lang, String name, String explanationForLlm) {
		var entity = new TriggerIngredientTranslationWcEntity();
		entity.setTriggerIngredientId(id);
		entity.setLang(lang);
		entity.setName(name);
		entity.setExplanationForLlm(explanationForLlm);
		entity.setVersion(1L);
		return tx.persist(entity).replaceWithVoid();
	}

	private Uni<Void> bumpStagedTranslation(ReactivePersistenceTxContext tx, UUID id, RecipeLanguage lang, String name, String explanationForLlm, long baseVersion) {
		var cb = tx.getCriteriaBuilder();
		CriteriaUpdate<TriggerIngredientTranslationWcEntity> cu = cb.createCriteriaUpdate(TriggerIngredientTranslationWcEntity.class);
		Root<TriggerIngredientTranslationWcEntity> wc = cu.getRoot();
		cu.set(wc.get(TriggerIngredientTranslationWcEntity_.name), name);
		cu.set(wc.get(TriggerIngredientTranslationWcEntity_.explanationForLlm), explanationForLlm);
		cu.set(wc.get(TriggerIngredientTranslationWcEntity_.version), cb.sum(wc.get(TriggerIngredientTranslationWcEntity_.version), 1L));
		cu.where(translationRowAt(cb, wc, id, lang, baseVersion));
		return tx.createUpdate(cu).execute().flatMap(rows -> rows == 1
				? Uni.createFrom().voidItem()
				: Uni.createFrom().failure(new StaleVersionException(TriggerIngredientTranslationEntity.class, id)));
	}

	@Override
	public Uni<Void> revertTranslation(ReactivePersistenceTxContext tx, UUID id, RecipeLanguage lang, long baseVersion) {
		return tx.find(TriggerIngredientTranslationWcEntity.class, new TriggerIngredientTranslationWcEntityId(id, lang)).flatMap(existing -> existing == null
				? Uni.createFrom().voidItem()
				: deleteStagedTranslation(tx, id, lang, baseVersion));
	}

	private Uni<Void> deleteStagedTranslation(ReactivePersistenceTxContext tx, UUID id, RecipeLanguage lang, long baseVersion) {
		var cb = tx.getCriteriaBuilder();
		CriteriaDelete<TriggerIngredientTranslationWcEntity> cd = cb.createCriteriaDelete(TriggerIngredientTranslationWcEntity.class);
		Root<TriggerIngredientTranslationWcEntity> wc = cd.getRoot();
		cd.where(translationRowAt(cb, wc, id, lang, baseVersion));
		return tx.createDelete(cd).execute().flatMap(rows -> rows == 1
				? Uni.createFrom().voidItem()
				: Uni.createFrom().failure(new StaleVersionException(TriggerIngredientTranslationEntity.class, id)));
	}

	private static Predicate translationRowAt(CriteriaBuilder cb, Root<TriggerIngredientTranslationWcEntity> wc, UUID id, RecipeLanguage lang, long baseVersion) {
		return cb.and(
				cb.equal(wc.get(TriggerIngredientTranslationWcEntity_.triggerIngredientId), id),
				cb.equal(wc.get(TriggerIngredientTranslationWcEntity_.lang), lang),
				cb.equal(wc.get(TriggerIngredientTranslationWcEntity_.version), baseVersion));
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
