package eu.dietwise.dao.impl.suggestions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.Root;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.dao.jpa.suggestions.RuleEntity_;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateEntity;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateEntity_;
import eu.dietwise.dao.suggestions.SuggestionTemplateDao;
import eu.dietwise.v1.model.ImmutableSuggestionTemplate;
import eu.dietwise.v1.model.SuggestionTemplate;
import eu.dietwise.v1.types.impl.AlternativeIngredientImpl;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class SuggestionTemplateDaoImpl implements SuggestionTemplateDao {
	@Override
	public Uni<List<SuggestionTemplate>> findByRule(ReactivePersistenceContext em, UUID ruleId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(SuggestionTemplateEntity.class);
		Root<SuggestionTemplateEntity> suggestionTemplate = q.from(SuggestionTemplateEntity.class);
		suggestionTemplate.fetch(SuggestionTemplateEntity_.alternativeIngredient);
		q.select(suggestionTemplate)
				.where(cb.equal(suggestionTemplate.get(SuggestionTemplateEntity_.rule).get(RuleEntity_.id), ruleId))
				.orderBy(cb.asc(suggestionTemplate.get(SuggestionTemplateEntity_.alternativeOrder)));
		return em.createQuery(q).getResultList().map(SuggestionTemplateDaoImpl::toSuggestionTemplates);
	}

	private static List<SuggestionTemplate> toSuggestionTemplates(List<SuggestionTemplateEntity> entities) {
		return entities.stream().map(SuggestionTemplateDaoImpl::toSuggestionTemplate).toList();
	}

	private static SuggestionTemplate toSuggestionTemplate(SuggestionTemplateEntity e) {
		return ImmutableSuggestionTemplate.builder()
				.id(new GenericSuggestionTemplateId(e.getId().toString()))
				.alternative(new AlternativeIngredientImpl(e.getAlternativeIngredient().getName()))
				.restriction(Optional.ofNullable(e.getRestriction()))
				.equivalence(Optional.ofNullable(e.getEquivalence()))
				.techniqueNotes(Optional.ofNullable(e.getTechniqueNotes()))
				.build();
	}
}
