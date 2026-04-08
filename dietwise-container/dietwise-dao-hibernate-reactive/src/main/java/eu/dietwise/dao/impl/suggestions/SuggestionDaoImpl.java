package eu.dietwise.dao.impl.suggestions;

import static java.util.stream.Collectors.toSet;

import static eu.dietwise.common.utils.UniComprehensions.forcm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaBuilder.In;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.dao.jpa.recommendations.RecommendationEntity_;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientCostEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientCostEntity_;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientEntity_;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientSeasonalityEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientSeasonalityEntity_;
import eu.dietwise.dao.jpa.suggestions.RuleEntity;
import eu.dietwise.dao.jpa.suggestions.RuleEntity_;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateEntity;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateEntity_;
import eu.dietwise.dao.suggestions.SuggestionDao;
import eu.dietwise.v1.model.AppliesTo;
import eu.dietwise.v1.model.ImmutableSuggestion;
import eu.dietwise.v1.model.Ingredient;
import eu.dietwise.v1.model.Suggestion;
import eu.dietwise.v1.types.Cost;
import eu.dietwise.v1.types.Country;
import eu.dietwise.v1.types.HasRuleId;
import eu.dietwise.v1.types.ImmutableSeasonality;
import eu.dietwise.v1.types.RecommendationComponentName;
import eu.dietwise.v1.types.Seasonality;
import eu.dietwise.v1.types.impl.AlternativeIngredientImpl;
import eu.dietwise.v1.types.impl.GenericRuleId;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
import eu.dietwise.v1.types.impl.RecommendationComponentNameImpl;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class SuggestionDaoImpl implements SuggestionDao {
	@Override
	public Uni<List<Suggestion>> retrieveByRule(ReactivePersistenceContext em, HasRuleId ruleId, Country country, Ingredient ingredient) {
		return forcm(
				findSuggestionTemplatesByRule(em, ruleId),
				this::extractAlternativeIngredientIds,
				(_, alternativeIngredientIds) -> findSeasonalityByAlternativeIngredientId(em, alternativeIngredientIds, country),
				(_, alternativeIngredientIds, _) -> findCostByAlternativeIngredientId(em, alternativeIngredientIds, country),
				(_, alternativeIngredientIds, _, _) -> findAlternativeComponentNamesByAlternativeIngredientId(em, alternativeIngredientIds),
				(suggestionTemplates, _, seasonalityByAlternativeIngredientId, costByAlternativeIngredientId, alternativeComponentNamesByAlternativeIngredientId) ->
						toSuggestionList(suggestionTemplates, seasonalityByAlternativeIngredientId, costByAlternativeIngredientId, alternativeComponentNamesByAlternativeIngredientId, ingredient)
		);
	}

	private Uni<List<SuggestionTemplateEntity>> findSuggestionTemplatesByRule(ReactivePersistenceContext em, HasRuleId ruleId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(SuggestionTemplateEntity.class);
		Root<SuggestionTemplateEntity> suggestionTemplate = q.from(SuggestionTemplateEntity.class);
		Fetch<SuggestionTemplateEntity, RuleEntity> rule = suggestionTemplate.fetch(SuggestionTemplateEntity_.rule);
		rule.fetch(RuleEntity_.recommendation);
		suggestionTemplate.fetch(SuggestionTemplateEntity_.alternativeIngredient);
		q.where(cb.equal(suggestionTemplate.get(SuggestionTemplateEntity_.rule).get(RuleEntity_.id), ruleId.getId().asUuid()));
		q.select(suggestionTemplate);
		return em.createQuery(q).getResultList();
	}

	private Uni<Set<UUID>> extractAlternativeIngredientIds(List<SuggestionTemplateEntity> suggestionTemplates) {
		Set<UUID> alternativeIngredientIds = suggestionTemplates.stream()
				.map(suggestionTemplate -> suggestionTemplate.getAlternativeIngredient().getId())
				.collect(toSet());
		return Uni.createFrom().item(alternativeIngredientIds);
	}

	private Uni<Map<UUID, AlternativeIngredientSeasonalityEntity>> findSeasonalityByAlternativeIngredientId(
			ReactivePersistenceContext em,
			Set<UUID> alternativeIngredientIds,
			Country country
	) {
		if (country == null || alternativeIngredientIds.isEmpty()) {
			return Uni.createFrom().item(Collections.emptyMap());
		}
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(AlternativeIngredientSeasonalityEntity.class);
		Root<AlternativeIngredientSeasonalityEntity> seasonality = q.from(AlternativeIngredientSeasonalityEntity.class);
		q.select(seasonality).where(
				cb.and(
						in(cb, seasonality.get(AlternativeIngredientSeasonalityEntity_.alternativeIngredient).get(AlternativeIngredientEntity_.id), alternativeIngredientIds),
						cb.equal(seasonality.get(AlternativeIngredientSeasonalityEntity_.countryCode2), country.getCode2())
				)
		);
		return em.createQuery(q).getResultList().map(list -> list.stream().collect(
				Collectors.toMap(
						seasonalityEntity -> seasonalityEntity.getAlternativeIngredient().getId(),
						seasonalityEntity -> seasonalityEntity,
						(existing, replacement) -> existing,
						LinkedHashMap::new
				)
		));
	}

	private Uni<Map<UUID, Cost>> findCostByAlternativeIngredientId(
			ReactivePersistenceContext em,
			Set<UUID> alternativeIngredientIds,
			Country country
	) {
		if (country == null || alternativeIngredientIds.isEmpty()) {
			return Uni.createFrom().item(Collections.emptyMap());
		}
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(AlternativeIngredientCostEntity.class);
		Root<AlternativeIngredientCostEntity> cost = q.from(AlternativeIngredientCostEntity.class);
		q.select(cost).where(
				cb.and(
						in(cb, cost.get(AlternativeIngredientCostEntity_.alternativeIngredient).get(AlternativeIngredientEntity_.id), alternativeIngredientIds),
						cb.equal(cost.get(AlternativeIngredientCostEntity_.countryCode2), country.getCode2())
				)
		);
		return em.createQuery(q).getResultList().map(list -> list.stream().collect(
				Collectors.toMap(
						costEntity -> costEntity.getAlternativeIngredient().getId(),
						AlternativeIngredientCostEntity::getCost,
						(existing, replacement) -> existing,
						LinkedHashMap::new
				)
		));
	}

	private Uni<Map<UUID, Set<RecommendationComponentName>>> findAlternativeComponentNamesByAlternativeIngredientId(
			ReactivePersistenceContext em,
			Set<UUID> alternativeIngredientIds
	) {
		if (alternativeIngredientIds.isEmpty()) {
			return Uni.createFrom().item(Collections.emptyMap());
		}
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(AlternativeIngredientSeasonalitylessProjection.class);
		Root<AlternativeIngredientEntity> alternativeIngredient = q.from(AlternativeIngredientEntity.class);
		var component = alternativeIngredient.join(AlternativeIngredientEntity_.componentsForScoring, JoinType.LEFT);
		q.select(cb.construct(
				AlternativeIngredientSeasonalitylessProjection.class,
				alternativeIngredient.get(AlternativeIngredientEntity_.id),
				component.get(RecommendationEntity_.componentForScoring)
		)).where(in(cb, alternativeIngredient.get(AlternativeIngredientEntity_.id), alternativeIngredientIds));
		return em.createQuery(q).getResultList().map(rows -> {
			Map<UUID, Set<RecommendationComponentName>> result = new LinkedHashMap<>();
			for (var row : rows) {
				result.computeIfAbsent(row.alternativeIngredientId(), ignored -> new java.util.LinkedHashSet<>());
				if (row.componentForScoring() != null) {
					result.get(row.alternativeIngredientId()).add(new RecommendationComponentNameImpl(row.componentForScoring()));
				}
			}
			for (var alternativeIngredientId : alternativeIngredientIds) {
				result.computeIfAbsent(alternativeIngredientId, ignored -> Collections.emptySet());
			}
			return result;
		});
	}

	private static List<Suggestion> toSuggestionList(
			List<SuggestionTemplateEntity> suggestionTemplates,
			Map<UUID, AlternativeIngredientSeasonalityEntity> seasonalityByAlternativeIngredientId,
			Map<UUID, Cost> costByAlternativeIngredientId,
			Map<UUID, Set<RecommendationComponentName>> alternativeComponentNamesByAlternativeIngredientId,
			Ingredient ingredient
	) {
		return suggestionTemplates.stream()
				.map(suggestionTemplate -> {
					UUID alternativeIngredientId = suggestionTemplate.getAlternativeIngredient().getId();
					return toSuggestion(
							suggestionTemplate,
							seasonalityByAlternativeIngredientId.get(alternativeIngredientId),
							costByAlternativeIngredientId.get(alternativeIngredientId),
							alternativeComponentNamesByAlternativeIngredientId.getOrDefault(alternativeIngredientId, Collections.emptySet()),
							ingredient
					);
				})
				.toList();
	}

	private static Suggestion toSuggestion(
			SuggestionTemplateEntity e,
			AlternativeIngredientSeasonalityEntity seasonality,
			Cost cost,
			Set<RecommendationComponentName> alternativeComponentNames,
			Ingredient ingredient
	) {
		return ImmutableSuggestion.builder()
				.id(new GenericSuggestionTemplateId(e.getId().toString()))
				.alternative(new AlternativeIngredientImpl(e.getAlternativeIngredient().getName()))
				.restriction(Optional.ofNullable(e.getRestriction()))
				.equivalence(Optional.ofNullable(e.getEquivalence()))
				.techniqueNotes(Optional.ofNullable(e.getTechniqueNotes()))
				.target(new AppliesTo.AppliesToIngredient(ingredient.getId()))
				.ruleId(new GenericRuleId(e.getRule().getId().toString()))
				.recommendation(new RecommendationImpl(e.getRule().getRecommendation().getName()))
				.seasonality(Optional.ofNullable(seasonality).map(SuggestionDaoImpl::toSeasonality))
				.cost(Optional.ofNullable(cost))
				.alternativeComponentNames(alternativeComponentNames)
				.build();
	}

	private static Seasonality toSeasonality(AlternativeIngredientSeasonalityEntity seasonality) {
		return ImmutableSeasonality.builder().monthFrom(seasonality.getMonthFrom()).monthTo(seasonality.getMonthTo()).build();
	}

	private static In<UUID> in(CriteriaBuilder cb, Path<UUID> path, Set<UUID> ids) {
		In<UUID> in = cb.in(path);
		ids.forEach(in::value);
		return in;
	}

	private record AlternativeIngredientSeasonalitylessProjection(
			UUID alternativeIngredientId,
			String componentForScoring
	) {
	}
}
