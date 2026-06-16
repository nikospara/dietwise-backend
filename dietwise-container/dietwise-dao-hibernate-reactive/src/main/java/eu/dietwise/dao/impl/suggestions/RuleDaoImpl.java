package eu.dietwise.dao.impl.suggestions;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import static eu.dietwise.common.utils.UniComprehensions.forcm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.SingularAttribute;

import eu.dietwise.common.dao.EntityNotFoundException;
import eu.dietwise.common.dao.StaleVersionException;
import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.dao.jpa.suggestions.RuleEntity;
import eu.dietwise.dao.jpa.suggestions.RuleEntity_;
import eu.dietwise.dao.jpa.suggestions.RuleTranslationEntity;
import eu.dietwise.dao.jpa.suggestions.RuleTranslationEntity_;
import eu.dietwise.dao.jpa.suggestions.RuleWcEntity;
import eu.dietwise.dao.jpa.suggestions.RuleWcEntity_;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientEntity_;
import eu.dietwise.dao.suggestions.RuleDao;
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
