package eu.dietwise.dao.impl.suggestions;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import static eu.dietwise.common.utils.UniComprehensions.forcm;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.metamodel.SingularAttribute;

import eu.dietwise.common.dao.EntityNotFoundException;
import eu.dietwise.common.dao.StaleVersionException;
import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.types.VersionedText;
import eu.dietwise.dao.jpa.recommendations.RecommendationEntity;
import eu.dietwise.dao.jpa.recommendations.RecommendationEntity_;
import eu.dietwise.dao.jpa.suggestions.RoleOrTechniqueEntity;
import eu.dietwise.dao.jpa.suggestions.RoleOrTechniqueEntity_;
import eu.dietwise.dao.jpa.suggestions.RoleOrTechniqueWcEntity;
import eu.dietwise.dao.jpa.suggestions.RoleOrTechniqueWcEntity_;
import eu.dietwise.dao.jpa.suggestions.RuleEntity;
import eu.dietwise.dao.jpa.suggestions.RuleEntity_;
import eu.dietwise.dao.jpa.suggestions.RuleTranslationEntity;
import eu.dietwise.dao.jpa.suggestions.RuleTranslationEntity_;
import eu.dietwise.dao.jpa.suggestions.RuleTranslationEntityId;
import eu.dietwise.dao.jpa.suggestions.RuleTranslationWcEntity;
import eu.dietwise.dao.jpa.suggestions.RuleTranslationWcEntity_;
import eu.dietwise.dao.jpa.suggestions.RuleTranslationWcEntityId;
import eu.dietwise.dao.jpa.suggestions.RuleWcEntity;
import eu.dietwise.dao.jpa.suggestions.RuleWcEntity_;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientEntity;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientEntity_;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientWcEntity;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientWcEntity_;
import eu.dietwise.dao.suggestions.RuleDao;
import eu.dietwise.services.model.suggestions.TranslationLangs;
import eu.dietwise.services.model.suggestions.RuleBusinessKey;
import eu.dietwise.services.model.suggestions.RuleReferences;
import eu.dietwise.services.model.suggestions.StagedNewRule;
import eu.dietwise.services.model.suggestions.StagedRuleOverlay;
import eu.dietwise.services.types.suggestions.HasTriggerIngredientId;
import eu.dietwise.v1.model.ImmutableRule;
import eu.dietwise.v1.model.Rule;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import eu.dietwise.v1.types.impl.RoleOrTechniqueImpl;
import eu.dietwise.v1.types.impl.TriggerIngredientImpl;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class RuleDaoImpl implements RuleDao {
	private static final List<RecipeLanguage> TRANSLATABLE_LANGUAGES =
			Arrays.stream(RecipeLanguage.values()).filter(lang -> lang != RecipeLanguage.EN).toList();

	@Override
	public Uni<List<Rule>> findByTriggerIngredient(ReactivePersistenceContext em, HasTriggerIngredientId triggerIngredientId, RecipeLanguage lang) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(RuleEntity.class);
		Root<RuleEntity> rule = selectRuleWithAssociations(q);
		q.where(cb.and(
				cb.equal(rule.get(RuleEntity_.triggerIngredient).get(TriggerIngredientEntity_.id), triggerIngredientId.getId().asUuid()),
				cb.isTrue(rule.get(RuleEntity_.active))
		));
		return loadRules(em, q, lang);
	}

	@Override
	public Uni<List<Rule>> findAll(ReactivePersistenceContext em, RecipeLanguage lang) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(RuleEntity.class);
		selectRuleWithAssociations(q);
		return loadRules(em, q, lang);
	}

	@Override
	public Uni<Map<UUID, StagedRuleOverlay>> findStagedOverlay(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(RuleWcEntity.class);
		q.select(q.from(RuleWcEntity.class));
		return em.createQuery(q).getResultList().map(RuleDaoImpl::toOverlayById);
	}

	@Override
	public Uni<Long> stageRationale(ReactivePersistenceTxContext tx, UUID ruleId, String rationale, long baseVersion) {
		return tx.find(RuleWcEntity.class, ruleId).flatMap(existing -> {
			if (existing != null) {
				return bumpStagedField(tx, ruleId, RuleWcEntity_.rationale, rationale, baseVersion);
			}
			if (baseVersion != 0L) {
				return Uni.createFrom().failure(new StaleVersionException(RuleEntity.class, ruleId));
			}
			return seedStagedRationale(tx, ruleId, rationale);
		});
	}

	private <T> Uni<Long> bumpStagedField(ReactivePersistenceTxContext tx, UUID ruleId, SingularAttribute<RuleWcEntity, T> field, T value, long baseVersion) {
		var cb = tx.getCriteriaBuilder();
		CriteriaUpdate<RuleWcEntity> cu = cb.createCriteriaUpdate(RuleWcEntity.class);
		Root<RuleWcEntity> wc = cu.getRoot();
		cu.set(wc.get(field), value);
		cu.set(wc.get(RuleWcEntity_.version), cb.sum(wc.get(RuleWcEntity_.version), 1L));
		cu.where(cb.and(
				cb.equal(wc.get(RuleWcEntity_.id), ruleId),
				cb.equal(wc.get(RuleWcEntity_.version), baseVersion)
		));
		return tx.createUpdate(cu).execute().flatMap(rowsAffected -> rowsAffected == 1
				? Uni.createFrom().item(baseVersion + 1)
				: Uni.createFrom().failure(new StaleVersionException(RuleEntity.class, ruleId)));
	}

	@Override
	public Uni<Void> revertRationale(ReactivePersistenceTxContext tx, UUID ruleId, long baseVersion) {
		return tx.find(RuleWcEntity.class, ruleId).flatMap(existing -> {
			if (existing == null) {
				return Uni.createFrom().voidItem();
			}
			return tx.find(RuleEntity.class, ruleId).flatMap(master -> master == null || existing.isActive() == master.isActive()
					? deleteStagedRow(tx, ruleId, baseVersion)
					: bumpStagedField(tx, ruleId, RuleWcEntity_.rationale, master.getRationale(), baseVersion).replaceWithVoid());
		});
	}

	private Uni<Void> deleteStagedRow(ReactivePersistenceTxContext tx, UUID ruleId, long baseVersion) {
		var cb = tx.getCriteriaBuilder();
		CriteriaDelete<RuleWcEntity> cd = cb.createCriteriaDelete(RuleWcEntity.class);
		Root<RuleWcEntity> wc = cd.getRoot();
		cd.where(cb.and(
				cb.equal(wc.get(RuleWcEntity_.id), ruleId),
				cb.equal(wc.get(RuleWcEntity_.version), baseVersion)
		));
		return tx.createDelete(cd).execute().flatMap(rowsAffected -> rowsAffected == 1
				? Uni.createFrom().voidItem()
				: Uni.createFrom().failure(new StaleVersionException(RuleEntity.class, ruleId)));
	}

	@Override
	public Uni<Void> setActive(ReactivePersistenceTxContext tx, UUID ruleId, boolean active, long baseVersion) {
		return tx.find(RuleWcEntity.class, ruleId).flatMap(existing ->
				tx.find(RuleEntity.class, ruleId).flatMap(master -> applySetActive(tx, ruleId, active, baseVersion, existing, master)));
	}

	@Override
	public Uni<UUID> createRule(ReactivePersistenceTxContext tx, UUID recommendationId, UUID triggerIngredientId, UUID roleOrTechniqueId) {
		var wc = new RuleWcEntity();
		UUID id = UUID.randomUUID();
		wc.setId(id);
		wc.setRecommendationId(recommendationId);
		wc.setTriggerIngredientId(triggerIngredientId);
		wc.setRoleOrTechniqueId(roleOrTechniqueId);
		wc.setActive(true);
		wc.setVersion(1L);
		return tx.persist(wc).replaceWith(id);
	}

	@Override
	public Uni<Void> discardNewRule(ReactivePersistenceTxContext tx, UUID ruleId, long baseVersion) {
		return tx.find(RuleWcEntity.class, ruleId).flatMap(existing -> {
			if (existing == null) {
				return Uni.createFrom().voidItem();
			}
			return tx.find(RuleEntity.class, ruleId).flatMap(master -> master == null
					? deleteStagedRow(tx, ruleId, baseVersion)
					: Uni.createFrom().failure(new EntityNotFoundException(RuleEntity.class, ruleId, "No unpublished new Rule to discard")));
		});
	}

	@Override
	public Uni<List<StagedNewRule>> findNewRules(ReactivePersistenceContext em, RecipeLanguage lang) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<RuleWcEntity> q = cb.createQuery(RuleWcEntity.class);
		Root<RuleWcEntity> wc = q.from(RuleWcEntity.class);
		Subquery<UUID> master = q.subquery(UUID.class);
		Root<RuleEntity> masterRule = master.from(RuleEntity.class);
		master.select(masterRule.get(RuleEntity_.id)).where(cb.equal(masterRule.get(RuleEntity_.id), wc.get(RuleWcEntity_.id)));
		q.select(wc).where(cb.not(cb.exists(master)));
		return em.createQuery(q).getResultList().flatMap(rows -> resolveNewRules(em, rows));
	}

	private Uni<List<StagedNewRule>> resolveNewRules(ReactivePersistenceContext em, List<RuleWcEntity> rows) {
		if (rows.isEmpty()) {
			return Uni.createFrom().item(List.of());
		}
		return forcm(
				loadNamesById(em, RecommendationEntity.class, RecommendationEntity_.id, RecommendationEntity_.name),
				_ -> loadNamesById(em, TriggerIngredientEntity.class, TriggerIngredientEntity_.id, TriggerIngredientEntity_.name),
				_ -> loadNamesById(em, TriggerIngredientWcEntity.class, TriggerIngredientWcEntity_.id, TriggerIngredientWcEntity_.name),
				_ -> loadNamesById(em, RoleOrTechniqueEntity.class, RoleOrTechniqueEntity_.id, RoleOrTechniqueEntity_.name),
				_ -> loadNamesById(em, RoleOrTechniqueWcEntity.class, RoleOrTechniqueWcEntity_.id, RoleOrTechniqueWcEntity_.name),
				(recNames, triggerMaster, triggerMirror, roleMaster, roleMirror) -> {
					Map<UUID, String> triggerNames = overlay(triggerMaster, triggerMirror);
					Map<UUID, String> roleNames = overlay(roleMaster, roleMirror);
					return rows.stream().map(row -> toStagedNewRule(row, recNames, triggerNames, roleNames)).toList();
				}
		);
	}

	private static Map<UUID, String> overlay(Map<UUID, String> master, Map<UUID, String> mirror) {
		Map<UUID, String> merged = new HashMap<>(master);
		merged.putAll(mirror);
		return merged;
	}

	private static <E> Uni<Map<UUID, String>> loadNamesById(ReactivePersistenceContext em, Class<E> type, SingularAttribute<E, UUID> idAttr, SingularAttribute<E, String> nameAttr) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<E> root = q.from(type);
		q.select(cb.tuple(root.get(idAttr), root.get(nameAttr)));
		return em.createQuery(q).getResultList().map(rows -> rows.stream()
				.collect(toMap(tuple -> tuple.get(0, UUID.class), tuple -> tuple.get(1, String.class))));
	}

	private static StagedNewRule toStagedNewRule(RuleWcEntity row, Map<UUID, String> recNames, Map<UUID, String> triggerNames, Map<UUID, String> roleNames) {
		Rule rule = ImmutableRule.builder()
				.id(new UuidRuleId(row.getId()))
				.recommendation(new RecommendationImpl(recNames.get(row.getRecommendationId())))
				.triggerIngredient(new TriggerIngredientImpl(triggerNames.get(row.getTriggerIngredientId())))
				.roleOrTechnique(row.getRoleOrTechniqueId() == null ? null : new RoleOrTechniqueImpl(roleNames.get(row.getRoleOrTechniqueId())))
				.rationale(row.getRationale())
				.cuisineContext(row.getCuisine())
				.isActive(row.isActive())
				.build();
		return new StagedNewRule(rule, row.getVersion());
	}

	@Override
	public Uni<Set<RuleBusinessKey>> findBusinessKeys(ReactivePersistenceContext em) {
		return masterBusinessKeys(em).flatMap(masterKeys -> workingCopyBusinessKeys(em).map(workingCopyKeys -> {
			Set<RuleBusinessKey> all = new HashSet<>(masterKeys);
			all.addAll(workingCopyKeys);
			return all;
		}));
	}

	private Uni<List<RuleBusinessKey>> masterBusinessKeys(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<RuleEntity> rule = q.from(RuleEntity.class);
		Join<RuleEntity, RoleOrTechniqueEntity> role = rule.join(RuleEntity_.roleOrTechnique, JoinType.LEFT);
		q.select(cb.tuple(
				rule.get(RuleEntity_.recommendation).get(RecommendationEntity_.id),
				rule.get(RuleEntity_.triggerIngredient).get(TriggerIngredientEntity_.id),
				role.get(RoleOrTechniqueEntity_.id)
		));
		return em.createQuery(q).getResultList().map(RuleDaoImpl::toBusinessKeys);
	}

	private Uni<List<RuleBusinessKey>> workingCopyBusinessKeys(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<RuleWcEntity> wc = q.from(RuleWcEntity.class);
		q.select(cb.tuple(
				wc.get(RuleWcEntity_.recommendationId),
				wc.get(RuleWcEntity_.triggerIngredientId),
				wc.get(RuleWcEntity_.roleOrTechniqueId)
		));
		return em.createQuery(q).getResultList().map(RuleDaoImpl::toBusinessKeys);
	}

	private static List<RuleBusinessKey> toBusinessKeys(List<Tuple> rows) {
		return rows.stream()
				.map(tuple -> new RuleBusinessKey(tuple.get(0, UUID.class), tuple.get(1, UUID.class), tuple.get(2, UUID.class)))
				.toList();
	}

	@Override
	public Uni<Map<UUID, RuleReferences>> findReferenceIds(ReactivePersistenceContext em) {
		return masterReferenceIds(em).flatMap(master -> workingCopyReferenceIds(em).map(workingCopy -> {
			Map<UUID, RuleReferences> merged = new HashMap<>(master);
			merged.putAll(workingCopy);
			return merged;
		}));
	}

	private Uni<Map<UUID, RuleReferences>> masterReferenceIds(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<RuleEntity> rule = q.from(RuleEntity.class);
		Join<RuleEntity, RoleOrTechniqueEntity> role = rule.join(RuleEntity_.roleOrTechnique, JoinType.LEFT);
		q.select(cb.tuple(
				rule.get(RuleEntity_.id),
				rule.get(RuleEntity_.triggerIngredient).get(TriggerIngredientEntity_.id),
				role.get(RoleOrTechniqueEntity_.id)
		));
		return em.createQuery(q).getResultList().map(RuleDaoImpl::toReferenceIds);
	}

	private Uni<Map<UUID, RuleReferences>> workingCopyReferenceIds(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<RuleWcEntity> wc = q.from(RuleWcEntity.class);
		q.select(cb.tuple(
				wc.get(RuleWcEntity_.id),
				wc.get(RuleWcEntity_.triggerIngredientId),
				wc.get(RuleWcEntity_.roleOrTechniqueId)
		));
		return em.createQuery(q).getResultList().map(RuleDaoImpl::toReferenceIds);
	}

	private static Map<UUID, RuleReferences> toReferenceIds(List<Tuple> rows) {
		return rows.stream().collect(toMap(
				tuple -> tuple.get(0, UUID.class),
				tuple -> new RuleReferences(tuple.get(1, UUID.class), tuple.get(2, UUID.class)),
				(existing, ignored) -> existing,
				LinkedHashMap::new
		));
	}

	@Override
	public Uni<Map<UUID, TranslationLangs>> findRationaleTranslationLangs(ReactivePersistenceContext em) {
		return masterTranslationLangs(em).flatMap(master -> stagedTranslationLangs(em).map(staged -> mergeTranslationLangs(master, staged)));
	}

	private Uni<Map<UUID, Set<RecipeLanguage>>> masterTranslationLangs(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<RuleTranslationEntity> t = q.from(RuleTranslationEntity.class);
		q.select(cb.tuple(t.get(RuleTranslationEntity_.rule).get(RuleEntity_.id), t.get(RuleTranslationEntity_.lang)))
				.where(cb.isNotNull(t.get(RuleTranslationEntity_.rationale)));
		return em.createQuery(q).getResultList().map(RuleDaoImpl::toLangsByRuleId);
	}

	private Uni<Map<UUID, Set<RecipeLanguage>>> stagedTranslationLangs(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<RuleTranslationWcEntity> t = q.from(RuleTranslationWcEntity.class);
		q.select(cb.tuple(t.get(RuleTranslationWcEntity_.ruleId), t.get(RuleTranslationWcEntity_.lang)));
		return em.createQuery(q).getResultList().map(RuleDaoImpl::toLangsByRuleId);
	}

	private static Map<UUID, Set<RecipeLanguage>> toLangsByRuleId(List<Tuple> rows) {
		Map<UUID, Set<RecipeLanguage>> byRuleId = new HashMap<>();
		for (Tuple row : rows) {
			byRuleId.computeIfAbsent(row.get(0, UUID.class), _ -> EnumSet.noneOf(RecipeLanguage.class))
					.add(row.get(1, RecipeLanguage.class));
		}
		return byRuleId;
	}

	private static Map<UUID, TranslationLangs> mergeTranslationLangs(Map<UUID, Set<RecipeLanguage>> master, Map<UUID, Set<RecipeLanguage>> staged) {
		Set<UUID> ruleIds = new HashSet<>(master.keySet());
		ruleIds.addAll(staged.keySet());
		Map<UUID, TranslationLangs> result = new HashMap<>();
		for (UUID ruleId : ruleIds) {
			result.put(ruleId, new TranslationLangs(
					master.getOrDefault(ruleId, EnumSet.noneOf(RecipeLanguage.class)),
					staged.getOrDefault(ruleId, EnumSet.noneOf(RecipeLanguage.class))));
		}
		return result;
	}

	@Override
	public Uni<Map<RecipeLanguage, VersionedText>> findRationaleTranslationsForEdit(ReactivePersistenceContext em, UUID ruleId) {
		return masterTranslationsForRule(em, ruleId).flatMap(master ->
				stagedTranslationsForRule(em, ruleId).map(staged -> toEditableTranslations(master, staged)));
	}

	private Uni<Map<RecipeLanguage, String>> masterTranslationsForRule(ReactivePersistenceContext em, UUID ruleId) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<RuleTranslationEntity> t = q.from(RuleTranslationEntity.class);
		q.select(cb.tuple(t.get(RuleTranslationEntity_.lang), t.get(RuleTranslationEntity_.rationale)))
				.where(cb.equal(t.get(RuleTranslationEntity_.rule).get(RuleEntity_.id), ruleId));
		return em.createQuery(q).getResultList().map(rows -> rows.stream()
				.filter(row -> row.get(1, String.class) != null)
				.collect(toMap(row -> row.get(0, RecipeLanguage.class), row -> row.get(1, String.class))));
	}

	private Uni<Map<RecipeLanguage, RuleTranslationWcEntity>> stagedTranslationsForRule(ReactivePersistenceContext em, UUID ruleId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(RuleTranslationWcEntity.class);
		Root<RuleTranslationWcEntity> t = q.from(RuleTranslationWcEntity.class);
		q.select(t).where(cb.equal(t.get(RuleTranslationWcEntity_.ruleId), ruleId));
		return em.createQuery(q).getResultList().map(list -> list.stream()
				.collect(toMap(RuleTranslationWcEntity::getLang, identity())));
	}

	private static Map<RecipeLanguage, VersionedText> toEditableTranslations(Map<RecipeLanguage, String> master, Map<RecipeLanguage, RuleTranslationWcEntity> staged) {
		Map<RecipeLanguage, VersionedText> result = new EnumMap<>(RecipeLanguage.class);
		for (RecipeLanguage lang : TRANSLATABLE_LANGUAGES) {
			RuleTranslationWcEntity wc = staged.get(lang);
			result.put(lang, wc != null
					? new VersionedText(wc.getRationale(), wc.getVersion())
					: new VersionedText(master.get(lang), 0L));
		}
		return result;
	}

	@Override
	public Uni<Void> stageRationaleTranslation(ReactivePersistenceTxContext tx, UUID ruleId, RecipeLanguage lang, String rationale, long baseVersion) {
		return tx.find(RuleTranslationWcEntity.class, new RuleTranslationWcEntityId(ruleId, lang)).flatMap(existing ->
				tx.find(RuleTranslationEntity.class, new RuleTranslationEntityId(ruleId, lang))
						.flatMap(master -> applyTranslationEdit(tx, ruleId, lang, rationale, baseVersion, existing, master)));
	}

	private Uni<Void> applyTranslationEdit(ReactivePersistenceTxContext tx, UUID ruleId, RecipeLanguage lang, String rationale, long baseVersion, RuleTranslationWcEntity existing, RuleTranslationEntity master) {
		boolean matchesMaster = Objects.equals(rationale, master == null ? null : master.getRationale());
		if (existing == null) {
			if (baseVersion != 0L) {
				return Uni.createFrom().failure(new StaleVersionException(RuleTranslationEntity.class, ruleId));
			}
			return matchesMaster ? Uni.createFrom().voidItem() : seedStagedTranslation(tx, ruleId, lang, rationale);
		}
		return matchesMaster
				? deleteStagedTranslation(tx, ruleId, lang, baseVersion)
				: bumpStagedTranslation(tx, ruleId, lang, rationale, baseVersion);
	}

	private Uni<Void> seedStagedTranslation(ReactivePersistenceTxContext tx, UUID ruleId, RecipeLanguage lang, String rationale) {
		var entity = new RuleTranslationWcEntity();
		entity.setRuleId(ruleId);
		entity.setLang(lang);
		entity.setRationale(rationale);
		entity.setVersion(1L);
		return tx.persist(entity).replaceWithVoid();
	}

	private Uni<Void> bumpStagedTranslation(ReactivePersistenceTxContext tx, UUID ruleId, RecipeLanguage lang, String rationale, long baseVersion) {
		var cb = tx.getCriteriaBuilder();
		CriteriaUpdate<RuleTranslationWcEntity> cu = cb.createCriteriaUpdate(RuleTranslationWcEntity.class);
		Root<RuleTranslationWcEntity> wc = cu.getRoot();
		cu.set(wc.get(RuleTranslationWcEntity_.rationale), rationale);
		cu.set(wc.get(RuleTranslationWcEntity_.version), cb.sum(wc.get(RuleTranslationWcEntity_.version), 1L));
		cu.where(translationRowAt(cb, wc, ruleId, lang, baseVersion));
		return tx.createUpdate(cu).execute().flatMap(rows -> rows == 1
				? Uni.createFrom().voidItem()
				: Uni.createFrom().failure(new StaleVersionException(RuleTranslationEntity.class, ruleId)));
	}

	@Override
	public Uni<Void> revertRationaleTranslation(ReactivePersistenceTxContext tx, UUID ruleId, RecipeLanguage lang, long baseVersion) {
		return tx.find(RuleTranslationWcEntity.class, new RuleTranslationWcEntityId(ruleId, lang)).flatMap(existing -> existing == null
				? Uni.createFrom().voidItem()
				: deleteStagedTranslation(tx, ruleId, lang, baseVersion));
	}

	private Uni<Void> deleteStagedTranslation(ReactivePersistenceTxContext tx, UUID ruleId, RecipeLanguage lang, long baseVersion) {
		var cb = tx.getCriteriaBuilder();
		CriteriaDelete<RuleTranslationWcEntity> cd = cb.createCriteriaDelete(RuleTranslationWcEntity.class);
		Root<RuleTranslationWcEntity> wc = cd.getRoot();
		cd.where(translationRowAt(cb, wc, ruleId, lang, baseVersion));
		return tx.createDelete(cd).execute().flatMap(rows -> rows == 1
				? Uni.createFrom().voidItem()
				: Uni.createFrom().failure(new StaleVersionException(RuleTranslationEntity.class, ruleId)));
	}

	private static Predicate translationRowAt(
			CriteriaBuilder cb,
			Root<RuleTranslationWcEntity> wc,
			UUID ruleId,
			RecipeLanguage lang,
			long baseVersion
	) {
		return cb.and(
				cb.equal(wc.get(RuleTranslationWcEntity_.ruleId), ruleId),
				cb.equal(wc.get(RuleTranslationWcEntity_.lang), lang),
				cb.equal(wc.get(RuleTranslationWcEntity_.version), baseVersion)
		);
	}

	private Uni<Void> applySetActive(ReactivePersistenceTxContext tx, UUID ruleId, boolean active, long baseVersion, RuleWcEntity existing, RuleEntity master) {
		if (master == null) {
			return Uni.createFrom().failure(new EntityNotFoundException(RuleEntity.class, ruleId));
		}
		if (existing == null) {
			if (baseVersion != 0L) {
				return Uni.createFrom().failure(new StaleVersionException(RuleEntity.class, ruleId));
			}
			return active == master.isActive()
					? Uni.createFrom().voidItem()
					: seedSnapshot(tx, master, wc -> wc.setActive(active)).replaceWithVoid();
		}
		boolean collapses = active == master.isActive() && Objects.equals(existing.getRationale(), master.getRationale());
		return collapses
				? deleteStagedRow(tx, ruleId, baseVersion)
				: bumpStagedField(tx, ruleId, RuleWcEntity_.active, active, baseVersion).replaceWithVoid();
	}

	private Uni<Long> seedStagedRationale(ReactivePersistenceTxContext tx, UUID ruleId, String rationale) {
		return tx.find(RuleEntity.class, ruleId).flatMap(master -> master == null
				? Uni.createFrom().failure(new EntityNotFoundException(RuleEntity.class, ruleId))
				: seedSnapshot(tx, master, wc -> wc.setRationale(rationale)).replaceWith(1L));
	}

	private Uni<RuleWcEntity> seedSnapshot(ReactivePersistenceTxContext tx, RuleEntity master, Consumer<RuleWcEntity> override) {
		var wc = new RuleWcEntity();
		wc.setId(master.getId());
		wc.setRecommendationId(master.getRecommendation().getId());
		wc.setTriggerIngredientId(master.getTriggerIngredient().getId());
		wc.setRoleOrTechniqueId(master.getRoleOrTechnique() != null ? master.getRoleOrTechnique().getId() : null);
		wc.setCuisine(master.getCuisine());
		wc.setRationale(master.getRationale());
		wc.setActive(master.isActive());
		wc.setVersion(1L);
		override.accept(wc);
		return tx.persist(wc).replaceWith(wc);
	}

	private static Root<RuleEntity> selectRuleWithAssociations(CriteriaQuery<RuleEntity> q) {
		Root<RuleEntity> rule = q.from(RuleEntity.class);
		rule.fetch(RuleEntity_.recommendation);
		rule.fetch(RuleEntity_.triggerIngredient);
		rule.fetch(RuleEntity_.roleOrTechnique, JoinType.LEFT);
		q.select(rule);
		return rule;
	}

	private Uni<List<Rule>> loadRules(ReactivePersistenceContext em, CriteriaQuery<RuleEntity> q, RecipeLanguage lang) {
		return forcm(
				em.createQuery(q).getResultList(),
				_ -> loadTranslationsByRuleId(em, lang),
				RuleDaoImpl::toRuleList
		);
	}

	private Uni<Map<UUID, RuleTranslationEntity>> loadTranslationsByRuleId(ReactivePersistenceContext em, RecipeLanguage lang) {
		if (lang == RecipeLanguage.EN) {
			return Uni.createFrom().item(Map.of());
		}
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(RuleTranslationEntity.class);
		var translation = q.from(RuleTranslationEntity.class);
		q.select(translation).where(cb.equal(translation.get(RuleTranslationEntity_.lang), lang));
		return em.createQuery(q).getResultList().map(list -> list.stream()
				.collect(toMap(x -> x.getRule().getId(), identity(), (existing, _) -> existing, LinkedHashMap::new)));
	}

	private static List<Rule> toRuleList(List<RuleEntity> list, Map<UUID, RuleTranslationEntity> translationsById) {
		return list.stream().map(rule -> toRule(rule, translationsById.get(rule.getId()))).toList();
	}

	private static Map<UUID, StagedRuleOverlay> toOverlayById(List<RuleWcEntity> rows) {
		return rows.stream().collect(toMap(
				RuleWcEntity::getId,
				row -> new StagedRuleOverlay(row.getRationale(), row.isActive(), row.getVersion()),
				(existing, ignored) -> existing,
				LinkedHashMap::new
		));
	}

	private static Rule toRule(RuleEntity e, RuleTranslationEntity t) {
		return ImmutableRule.builder()
				.id(new UuidRuleId(e.getId()))
				.recommendation(new RecommendationImpl(e.getRecommendation().getName()))
				.triggerIngredient(new TriggerIngredientImpl(e.getTriggerIngredient().getName()))
				.roleOrTechnique(e.getRoleOrTechnique() != null ? new RoleOrTechniqueImpl(e.getRoleOrTechnique().getName()) : null)
				.rationale(t != null && t.getRationale() != null ? t.getRationale() : e.getRationale())
				.cuisineContext(e.getCuisine())
				.isActive(e.isActive())
				.build();
	}
}
