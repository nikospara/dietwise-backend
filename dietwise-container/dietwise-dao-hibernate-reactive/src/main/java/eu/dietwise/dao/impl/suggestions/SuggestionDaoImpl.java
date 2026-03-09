package eu.dietwise.dao.impl.suggestions;

import static java.util.stream.Collectors.toSet;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.dao.jpa.recommendations.RecommendationEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientEntity_;
import eu.dietwise.dao.jpa.suggestions.RoleOrTechniqueEntity_;
import eu.dietwise.dao.jpa.suggestions.RuleEntity;
import eu.dietwise.dao.jpa.suggestions.RuleEntity_;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateEntity;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateEntity_;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientEntity_;
import eu.dietwise.dao.suggestions.SuggestionDao;
import eu.dietwise.services.types.suggestions.HasRoleOrTechniqueId;
import eu.dietwise.services.types.suggestions.HasTriggerIngredientId;
import eu.dietwise.v1.model.AppliesTo;
import eu.dietwise.v1.model.ImmutableSuggestion;
import eu.dietwise.v1.model.Ingredient;
import eu.dietwise.v1.model.Suggestion;
import eu.dietwise.v1.types.RecommendationComponentName;
import eu.dietwise.v1.types.impl.AlternativeIngredientImpl;
import eu.dietwise.v1.types.impl.GenericRuleId;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
import eu.dietwise.v1.types.impl.RecommendationComponentNameImpl;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class SuggestionDaoImpl implements SuggestionDao {
	@Override
	public Uni<List<Suggestion>> findByRoleAndTriggerIngredient(
			ReactivePersistenceContext em, HasRoleOrTechniqueId roleId, HasTriggerIngredientId triggerIngredientId, Ingredient ingredient) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(SuggestionTemplateEntity.class);
		Root<SuggestionTemplateEntity> suggestionTemplate = q.from(SuggestionTemplateEntity.class);
		Fetch<SuggestionTemplateEntity, RuleEntity> rule = suggestionTemplate.fetch(SuggestionTemplateEntity_.rule);
		rule.fetch(RuleEntity_.recommendation);
		var alternativeIngredient = suggestionTemplate.fetch(SuggestionTemplateEntity_.alternativeIngredient);
		alternativeIngredient.fetch(AlternativeIngredientEntity_.componentsForScoring, JoinType.LEFT);
		q.select(suggestionTemplate).where(
				cb.equal(suggestionTemplate.get(SuggestionTemplateEntity_.rule).get(RuleEntity_.roleOrTechnique).get(RoleOrTechniqueEntity_.id), roleId.getId().asUuid()),
				cb.equal(suggestionTemplate.get(SuggestionTemplateEntity_.rule).get(RuleEntity_.triggerIngredient).get(TriggerIngredientEntity_.id), triggerIngredientId.getId().asUuid())
		);
		return em.createQuery(q).getResultList().map(list -> toSuggestionList(list, ingredient));
	}

	private static List<Suggestion> toSuggestionList(List<SuggestionTemplateEntity> list, Ingredient ingredient) {
		return list.stream().map(entity -> toSuggestion(entity, ingredient)).toList();
	}

	private static Suggestion toSuggestion(SuggestionTemplateEntity e, Ingredient ingredient) {
		return ImmutableSuggestion.builder()
				.id(new GenericSuggestionTemplateId(e.getId().toString()))
				.alternative(new AlternativeIngredientImpl(e.getAlternativeIngredient().getName()))
				.restriction(Optional.ofNullable(e.getRestriction()))
				.equivalence(Optional.ofNullable(e.getEquivalence()))
				.techniqueNotes(Optional.ofNullable(e.getTechniqueNotes()))
				.target(new AppliesTo.AppliesToIngredient(ingredient.getId()))
				.ruleId(new GenericRuleId(e.getRule().getId().toString()))
				.recommendation(new RecommendationImpl(e.getRule().getRecommendation().getName()))
				.alternativeComponentNames(toRecommendationComponentNames(e))
				.build();
	}

	private static Set<RecommendationComponentName> toRecommendationComponentNames(SuggestionTemplateEntity e) {
		return e.getAlternativeIngredient().getComponentsForScoring().stream()
				.map(RecommendationEntity::getComponentForScoring)
				.map(RecommendationComponentNameImpl::new)
				.collect(toSet());
	}
}
