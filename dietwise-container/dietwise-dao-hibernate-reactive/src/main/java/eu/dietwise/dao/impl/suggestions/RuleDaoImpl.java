package eu.dietwise.dao.impl.suggestions;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import static eu.dietwise.common.utils.UniComprehensions.forcm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.Root;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.dao.jpa.suggestions.RuleEntity;
import eu.dietwise.dao.jpa.suggestions.RuleEntity_;
import eu.dietwise.dao.jpa.suggestions.RuleTranslationEntity;
import eu.dietwise.dao.jpa.suggestions.RuleTranslationEntity_;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientEntity_;
import eu.dietwise.dao.suggestions.RuleDao;
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
		Root<RuleEntity> rule = q.from(RuleEntity.class);
		rule.fetch(RuleEntity_.recommendation);
		rule.fetch(RuleEntity_.triggerIngredient);
		rule.fetch(RuleEntity_.roleOrTechnique);
		q.select(rule).where(
				cb.equal(rule.get(RuleEntity_.triggerIngredient).get(TriggerIngredientEntity_.id), triggerIngredientId.getId().asUuid())
		);
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

	private static Rule toRule(RuleEntity e, RuleTranslationEntity t) {
		return ImmutableRule.builder()
				.id(new UuidRuleId(e.getId()))
				.recommendation(new RecommendationImpl(e.getRecommendation().getName()))
				.triggerIngredient(new TriggerIngredientImpl(e.getTriggerIngredient().getName()))
				.roleOrTechnique(new RoleOrTechniqueImpl(e.getRoleOrTechnique().getName()))
				.rationale(t != null && t.getRationale() != null ? t.getRationale() : e.getRationale())
				.cuisineContext(e.getCuisine())
				.build();
	}
}
