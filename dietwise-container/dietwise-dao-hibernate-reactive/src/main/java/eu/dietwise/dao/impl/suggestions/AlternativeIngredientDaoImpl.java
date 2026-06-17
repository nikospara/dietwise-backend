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
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import eu.dietwise.common.dao.EntityNotFoundException;
import eu.dietwise.common.dao.StaleVersionException;
import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.types.ReferenceDetails;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientEntity_;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientSeasonalityEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientTranslationEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientTranslationEntityId;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientTranslationEntity_;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientTranslationWcEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientTranslationWcEntityId;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientTranslationWcEntity_;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientWcEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientWcEntity_;
import eu.dietwise.dao.suggestions.AlternativeIngredientDao;
import eu.dietwise.services.model.suggestions.AlternativeIngredient;
import eu.dietwise.services.model.suggestions.ImmutableAlternativeIngredient;
import eu.dietwise.services.model.suggestions.TranslationLangs;
import eu.dietwise.v1.types.Country;
import eu.dietwise.v1.types.ImmutableSeasonality;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.Seasonality;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class AlternativeIngredientDaoImpl implements AlternativeIngredientDao {
	private static final List<RecipeLanguage> TRANSLATABLE_LANGUAGES =
			Arrays.stream(RecipeLanguage.values()).filter(lang -> lang != RecipeLanguage.EN).toList();

	@Override
	public Uni<List<AlternativeIngredient>> findAll(ReactivePersistenceContext em, RecipeLanguage lang) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(AlternativeIngredientEntity.class);
		Root<AlternativeIngredientEntity> alternativeIngredient = q.from(AlternativeIngredientEntity.class);
		alternativeIngredient.fetch(AlternativeIngredientEntity_.seasonalityByCountry, JoinType.LEFT);
		q.select(alternativeIngredient);
		q.distinct(true);
		return em.createQuery(q).getResultList()
				.flatMap(list -> loadTranslationsByAlternativeIngredientId(em, lang)
						.map(translationsById -> toAlternativeIngredientList(list, translationsById)));
	}

	@Override
	public Uni<List<ReferenceOption>> listOptions(ReactivePersistenceContext em) {
		return masterOptions(em).flatMap(master -> mirrorOptions(em).map(mirror -> mergeOptions(master, mirror)));
	}

	@Override
	public Uni<UUID> createAlternativeIngredient(ReactivePersistenceTxContext tx, String name) {
		var entity = new AlternativeIngredientWcEntity();
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
		Root<AlternativeIngredientWcEntity> root = q.from(AlternativeIngredientWcEntity.class);
		q.select(cb.tuple(root.get(AlternativeIngredientWcEntity_.id), root.get(AlternativeIngredientWcEntity_.name)));
		return em.createQuery(q).getResultList().map(rows -> rows.stream()
				.collect(Collectors.toMap(t -> t.get(0, UUID.class), t -> t.get(1, String.class))));
	}

	@Override
	public Uni<ReferenceDetails> findEditableById(ReactivePersistenceContext em, UUID id) {
		return em.find(AlternativeIngredientWcEntity.class, id).flatMap(mirror ->
				em.find(AlternativeIngredientEntity.class, id).map(master -> {
					if (mirror != null) {
						return new ReferenceDetails(mirror.getName(), mirror.getExplanationForLlm(), mirror.getVersion(), master != null);
					}
					if (master == null) {
						throw new EntityNotFoundException(AlternativeIngredientEntity.class, id);
					}
					return new ReferenceDetails(master.getName(), master.getExplanationForLlm(), 0L, true);
				}));
	}

	@Override
	public Uni<Void> editAlternativeIngredient(ReactivePersistenceTxContext tx, UUID id, String name, String explanationForLlm, long baseVersion) {
		return tx.find(AlternativeIngredientWcEntity.class, id).flatMap(existing ->
				tx.find(AlternativeIngredientEntity.class, id).flatMap(master -> applyEdit(tx, id, name, explanationForLlm, baseVersion, existing, master)));
	}

	@Override
	public Uni<Void> revertAlternativeIngredient(ReactivePersistenceTxContext tx, UUID id, long baseVersion) {
		return tx.find(AlternativeIngredientWcEntity.class, id).flatMap(existing -> existing == null
				? Uni.createFrom().voidItem()
				: tx.find(AlternativeIngredientEntity.class, id).flatMap(master -> master == null
						? Uni.createFrom().failure(new EntityNotFoundException(AlternativeIngredientEntity.class, id, "No published Alternative Ingredient to revert"))
						: deleteStaged(tx, id, baseVersion)));
	}

	@Override
	public Uni<Map<UUID, TranslationLangs>> findTranslationLangs(ReactivePersistenceContext em) {
		return masterTranslationLangs(em).flatMap(master -> stagedTranslationLangs(em).map(staged -> mergeTranslationLangs(master, staged)));
	}

	private Uni<Map<UUID, Set<RecipeLanguage>>> masterTranslationLangs(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<AlternativeIngredientTranslationEntity> t = q.from(AlternativeIngredientTranslationEntity.class);
		q.select(cb.tuple(t.get(AlternativeIngredientTranslationEntity_.alternativeIngredient).get(AlternativeIngredientEntity_.id), t.get(AlternativeIngredientTranslationEntity_.lang)))
				.where(cb.isNotNull(t.get(AlternativeIngredientTranslationEntity_.name)));
		return em.createQuery(q).getResultList().map(AlternativeIngredientDaoImpl::toLangsById);
	}

	private Uni<Map<UUID, Set<RecipeLanguage>>> stagedTranslationLangs(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<AlternativeIngredientTranslationWcEntity> t = q.from(AlternativeIngredientTranslationWcEntity.class);
		q.select(cb.tuple(t.get(AlternativeIngredientTranslationWcEntity_.alternativeIngredientId), t.get(AlternativeIngredientTranslationWcEntity_.lang)));
		return em.createQuery(q).getResultList().map(AlternativeIngredientDaoImpl::toLangsById);
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

	private Uni<Map<RecipeLanguage, AlternativeIngredientTranslationEntity>> masterTranslationsForEntity(ReactivePersistenceContext em, UUID id) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(AlternativeIngredientTranslationEntity.class);
		Root<AlternativeIngredientTranslationEntity> t = q.from(AlternativeIngredientTranslationEntity.class);
		q.select(t).where(cb.equal(t.get(AlternativeIngredientTranslationEntity_.alternativeIngredient).get(AlternativeIngredientEntity_.id), id));
		return em.createQuery(q).getResultList().map(list -> list.stream()
				.collect(Collectors.toMap(AlternativeIngredientTranslationEntity::getLang, t2 -> t2)));
	}

	private Uni<Map<RecipeLanguage, AlternativeIngredientTranslationWcEntity>> stagedTranslationsForEntity(ReactivePersistenceContext em, UUID id) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(AlternativeIngredientTranslationWcEntity.class);
		Root<AlternativeIngredientTranslationWcEntity> t = q.from(AlternativeIngredientTranslationWcEntity.class);
		q.select(t).where(cb.equal(t.get(AlternativeIngredientTranslationWcEntity_.alternativeIngredientId), id));
		return em.createQuery(q).getResultList().map(list -> list.stream()
				.collect(Collectors.toMap(AlternativeIngredientTranslationWcEntity::getLang, t2 -> t2)));
	}

	private static Map<RecipeLanguage, ReferenceDetails> toEditableTranslations(
			Map<RecipeLanguage, AlternativeIngredientTranslationEntity> master,
			Map<RecipeLanguage, AlternativeIngredientTranslationWcEntity> staged
	) {
		Map<RecipeLanguage, ReferenceDetails> result = new EnumMap<>(RecipeLanguage.class);
		for (RecipeLanguage lang : TRANSLATABLE_LANGUAGES) {
			AlternativeIngredientTranslationEntity m = master.get(lang);
			AlternativeIngredientTranslationWcEntity wc = staged.get(lang);
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
				tx.find(AlternativeIngredientTranslationWcEntity.class, new AlternativeIngredientTranslationWcEntityId(id, lang)),
				_ -> tx.find(AlternativeIngredientTranslationEntity.class, new AlternativeIngredientTranslationEntityId(id, lang)),
				(existing, master) -> applyTranslationEdit(tx, id, lang, name, explanationForLlm, baseVersion, existing, master)
		);
	}

	private Uni<Void> applyTranslationEdit(ReactivePersistenceTxContext tx, UUID id, RecipeLanguage lang, String name, String explanationForLlm, long baseVersion, AlternativeIngredientTranslationWcEntity existing, AlternativeIngredientTranslationEntity master) {
		String masterName = master == null ? null : master.getName();
		String masterExplanation = master == null ? null : master.getExplanationForLlm();
		boolean matchesMaster = Objects.equals(name, masterName) && Objects.equals(explanationForLlm, masterExplanation);
		if (existing == null) {
			if (baseVersion != 0L) {
				return Uni.createFrom().failure(new StaleVersionException(AlternativeIngredientTranslationEntity.class, id));
			}
			return matchesMaster ? Uni.createFrom().voidItem() : seedStagedTranslation(tx, id, lang, name, explanationForLlm);
		}
		return matchesMaster
				? deleteStagedTranslation(tx, id, lang, baseVersion)
				: bumpStagedTranslation(tx, id, lang, name, explanationForLlm, baseVersion);
	}

	private Uni<Void> seedStagedTranslation(ReactivePersistenceTxContext tx, UUID id, RecipeLanguage lang, String name, String explanationForLlm) {
		var entity = new AlternativeIngredientTranslationWcEntity();
		entity.setAlternativeIngredientId(id);
		entity.setLang(lang);
		entity.setName(name);
		entity.setExplanationForLlm(explanationForLlm);
		entity.setVersion(1L);
		return tx.persist(entity).replaceWithVoid();
	}

	private Uni<Void> bumpStagedTranslation(ReactivePersistenceTxContext tx, UUID id, RecipeLanguage lang, String name, String explanationForLlm, long baseVersion) {
		var cb = tx.getCriteriaBuilder();
		CriteriaUpdate<AlternativeIngredientTranslationWcEntity> cu = cb.createCriteriaUpdate(AlternativeIngredientTranslationWcEntity.class);
		Root<AlternativeIngredientTranslationWcEntity> wc = cu.getRoot();
		cu.set(wc.get(AlternativeIngredientTranslationWcEntity_.name), name);
		cu.set(wc.get(AlternativeIngredientTranslationWcEntity_.explanationForLlm), explanationForLlm);
		cu.set(wc.get(AlternativeIngredientTranslationWcEntity_.version), cb.sum(wc.get(AlternativeIngredientTranslationWcEntity_.version), 1L));
		cu.where(translationRowAt(cb, wc, id, lang, baseVersion));
		return tx.createUpdate(cu).execute().flatMap(rows -> rows == 1
				? Uni.createFrom().voidItem()
				: Uni.createFrom().failure(new StaleVersionException(AlternativeIngredientTranslationEntity.class, id)));
	}

	@Override
	public Uni<Void> revertTranslation(ReactivePersistenceTxContext tx, UUID id, RecipeLanguage lang, long baseVersion) {
		return tx.find(AlternativeIngredientTranslationWcEntity.class, new AlternativeIngredientTranslationWcEntityId(id, lang)).flatMap(existing -> existing == null
				? Uni.createFrom().voidItem()
				: deleteStagedTranslation(tx, id, lang, baseVersion));
	}

	private Uni<Void> deleteStagedTranslation(ReactivePersistenceTxContext tx, UUID id, RecipeLanguage lang, long baseVersion) {
		var cb = tx.getCriteriaBuilder();
		CriteriaDelete<AlternativeIngredientTranslationWcEntity> cd = cb.createCriteriaDelete(AlternativeIngredientTranslationWcEntity.class);
		Root<AlternativeIngredientTranslationWcEntity> wc = cd.getRoot();
		cd.where(translationRowAt(cb, wc, id, lang, baseVersion));
		return tx.createDelete(cd).execute().flatMap(rows -> rows == 1
				? Uni.createFrom().voidItem()
				: Uni.createFrom().failure(new StaleVersionException(AlternativeIngredientTranslationEntity.class, id)));
	}

	private static Predicate translationRowAt(CriteriaBuilder cb, Root<AlternativeIngredientTranslationWcEntity> wc, UUID id, RecipeLanguage lang, long baseVersion) {
		return cb.and(
				cb.equal(wc.get(AlternativeIngredientTranslationWcEntity_.alternativeIngredientId), id),
				cb.equal(wc.get(AlternativeIngredientTranslationWcEntity_.lang), lang),
				cb.equal(wc.get(AlternativeIngredientTranslationWcEntity_.version), baseVersion));
	}

	private Uni<Void> applyEdit(ReactivePersistenceTxContext tx, UUID id, String name, String explanationForLlm, long baseVersion, AlternativeIngredientWcEntity existing, AlternativeIngredientEntity master) {
		if (existing == null && master == null) {
			return Uni.createFrom().failure(new EntityNotFoundException(AlternativeIngredientEntity.class, id));
		}
		boolean matchesMaster = master != null
				&& Objects.equals(name, master.getName())
				&& Objects.equals(explanationForLlm, master.getExplanationForLlm());
		if (existing == null) {
			if (baseVersion != 0L) {
				return Uni.createFrom().failure(new StaleVersionException(AlternativeIngredientEntity.class, id));
			}
			return matchesMaster ? Uni.createFrom().voidItem() : seedStaged(tx, id, name, explanationForLlm);
		}
		return matchesMaster
				? deleteStaged(tx, id, baseVersion)
				: bumpStaged(tx, id, name, explanationForLlm, baseVersion);
	}

	private Uni<Void> seedStaged(ReactivePersistenceTxContext tx, UUID id, String name, String explanationForLlm) {
		var entity = new AlternativeIngredientWcEntity();
		entity.setId(id);
		entity.setName(name);
		entity.setExplanationForLlm(explanationForLlm);
		entity.setVersion(1L);
		return tx.persist(entity).replaceWithVoid();
	}

	private Uni<Void> bumpStaged(ReactivePersistenceTxContext tx, UUID id, String name, String explanationForLlm, long baseVersion) {
		var cb = tx.getCriteriaBuilder();
		CriteriaUpdate<AlternativeIngredientWcEntity> cu = cb.createCriteriaUpdate(AlternativeIngredientWcEntity.class);
		Root<AlternativeIngredientWcEntity> wc = cu.getRoot();
		cu.set(wc.get(AlternativeIngredientWcEntity_.name), name);
		cu.set(wc.get(AlternativeIngredientWcEntity_.explanationForLlm), explanationForLlm);
		cu.set(wc.get(AlternativeIngredientWcEntity_.version), cb.sum(wc.get(AlternativeIngredientWcEntity_.version), 1L));
		cu.where(cb.and(
				cb.equal(wc.get(AlternativeIngredientWcEntity_.id), id),
				cb.equal(wc.get(AlternativeIngredientWcEntity_.version), baseVersion)
		));
		return tx.createUpdate(cu).execute().flatMap(rowsAffected -> rowsAffected == 1
				? Uni.createFrom().voidItem()
				: Uni.createFrom().failure(new StaleVersionException(AlternativeIngredientEntity.class, id)));
	}

	private Uni<Void> deleteStaged(ReactivePersistenceTxContext tx, UUID id, long baseVersion) {
		var cb = tx.getCriteriaBuilder();
		CriteriaDelete<AlternativeIngredientWcEntity> cd = cb.createCriteriaDelete(AlternativeIngredientWcEntity.class);
		Root<AlternativeIngredientWcEntity> wc = cd.getRoot();
		cd.where(cb.and(
				cb.equal(wc.get(AlternativeIngredientWcEntity_.id), id),
				cb.equal(wc.get(AlternativeIngredientWcEntity_.version), baseVersion)
		));
		return tx.createDelete(cd).execute().flatMap(rowsAffected -> rowsAffected == 1
				? Uni.createFrom().voidItem()
				: Uni.createFrom().failure(new StaleVersionException(AlternativeIngredientEntity.class, id)));
	}

	private Uni<List<ReferenceOption>> masterOptions(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<AlternativeIngredientEntity> root = q.from(AlternativeIngredientEntity.class);
		q.select(cb.tuple(root.get(AlternativeIngredientEntity_.id), root.get(AlternativeIngredientEntity_.name)));
		return em.createQuery(q).getResultList().map(AlternativeIngredientDaoImpl::toOptions);
	}

	private Uni<List<ReferenceOption>> mirrorOptions(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<AlternativeIngredientWcEntity> root = q.from(AlternativeIngredientWcEntity.class);
		q.select(cb.tuple(root.get(AlternativeIngredientWcEntity_.id), root.get(AlternativeIngredientWcEntity_.name)));
		return em.createQuery(q).getResultList().map(AlternativeIngredientDaoImpl::toOptions);
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

	private Uni<Map<UUID, AlternativeIngredientTranslationEntity>> loadTranslationsByAlternativeIngredientId(
			ReactivePersistenceContext em,
			RecipeLanguage lang
	) {
		if (lang == RecipeLanguage.EN) {
			return Uni.createFrom().item(Map.of());
		}
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(AlternativeIngredientTranslationEntity.class);
		var translation = q.from(AlternativeIngredientTranslationEntity.class);
		q.select(translation).where(cb.equal(translation.get(AlternativeIngredientTranslationEntity_.lang), lang));
		return em.createQuery(q)
				.getResultList()
				.map(list -> list.stream().collect(Collectors.toMap(
						t -> t.getAlternativeIngredient().getId(),
						t -> t
				)));
	}

	private static List<AlternativeIngredient> toAlternativeIngredientList(
			List<AlternativeIngredientEntity> list,
			Map<UUID, AlternativeIngredientTranslationEntity> translationsById
	) {
		return list.stream().map(e -> toAlternativeIngredient(e, translationsById.get(e.getId()))).toList();
	}

	private static AlternativeIngredient toAlternativeIngredient(AlternativeIngredientEntity e, AlternativeIngredientTranslationEntity t) {
		return ImmutableAlternativeIngredient.builder()
				.id(new UuidAlternativeIngredientId(e.getId()))
				.name(t != null && t.getName() != null ? t.getName() : e.getName())
				.explanationForLlm(Optional.ofNullable(t != null && t.getExplanationForLlm() != null ? t.getExplanationForLlm() : e.getExplanationForLlm()))
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
