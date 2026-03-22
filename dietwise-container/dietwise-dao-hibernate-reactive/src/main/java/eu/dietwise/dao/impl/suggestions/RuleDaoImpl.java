package eu.dietwise.dao.impl.suggestions;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.Root;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.dao.jpa.suggestions.RuleEntity;
import eu.dietwise.dao.jpa.suggestions.RuleEntity_;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientEntity_;
import eu.dietwise.dao.suggestions.RuleDao;
import eu.dietwise.services.types.suggestions.HasTriggerIngredientId;
import eu.dietwise.v1.model.ImmutableRule;
import eu.dietwise.v1.model.Rule;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import eu.dietwise.v1.types.impl.RoleOrTechniqueImpl;
import eu.dietwise.v1.types.impl.TriggerIngredientImpl;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class RuleDaoImpl implements RuleDao {
	@Override
	public Uni<List<Rule>> findByTriggerIngredient(ReactivePersistenceContext em, HasTriggerIngredientId triggerIngredientId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(RuleEntity.class);
		Root<RuleEntity> rule = q.from(RuleEntity.class);
		rule.fetch(RuleEntity_.recommendation);
		rule.fetch(RuleEntity_.triggerIngredient);
		rule.fetch(RuleEntity_.roleOrTechnique);
		q.select(rule).where(
				cb.equal(rule.get(RuleEntity_.triggerIngredient).get(TriggerIngredientEntity_.id), triggerIngredientId.getId().asUuid())
		);
		return em.createQuery(q).getResultList().map(RuleDaoImpl::toRuleList);
	}

	private static List<Rule> toRuleList(List<RuleEntity> list) {
		return list.stream().map(RuleDaoImpl::toRule).toList();
	}

	private static Rule toRule(RuleEntity e) {
		return ImmutableRule.builder()
				.id(new UuidRuleId(e.getId()))
				.recommendation(new RecommendationImpl(e.getRecommendation().getName()))
				.triggerIngredient(new TriggerIngredientImpl(e.getTriggerIngredient().getName()))
				.roleOrTechnique(new RoleOrTechniqueImpl(e.getRoleOrTechnique().getName()))
				.cuisineContext(e.getCuisine())
				.build();
	}
}
