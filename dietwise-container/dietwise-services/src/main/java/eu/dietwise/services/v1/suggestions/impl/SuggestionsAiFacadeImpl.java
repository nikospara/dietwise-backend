package eu.dietwise.services.v1.suggestions.impl;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.dao.suggestions.AlternativeIngredientDao;
import eu.dietwise.dao.suggestions.RoleOrTechniqueDao;
import eu.dietwise.dao.suggestions.TriggerIngredientDao;
import eu.dietwise.services.model.suggestions.AlternativeIngredient;
import eu.dietwise.services.model.suggestions.RoleOrTechnique;
import eu.dietwise.services.model.suggestions.TriggerIngredient;
import eu.dietwise.services.v1.suggestions.SuggestionsAiFacade;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class SuggestionsAiFacadeImpl implements SuggestionsAiFacade {
	private final RoleOrTechniqueDao roleOrTechniqueDao;
	private final TriggerIngredientDao triggerIngredientDao;
	private final AlternativeIngredientDao alternativeIngredientDao;

	private final CachedUniValue<Map<String, RoleOrTechnique>> cachedRoles = new CachedUniValue<>();
	private final CachedUniValue<Map<String, TriggerIngredient>> cachedTriggerIngredients = new CachedUniValue<>();
	private final CachedUniValue<Map<String, AlternativeIngredient>> cachedAlternatives = new CachedUniValue<>();

	public SuggestionsAiFacadeImpl(RoleOrTechniqueDao roleOrTechniqueDao, TriggerIngredientDao triggerIngredientDao, AlternativeIngredientDao alternativeIngredientDao) {
		this.roleOrTechniqueDao = roleOrTechniqueDao;
		this.triggerIngredientDao = triggerIngredientDao;
		this.alternativeIngredientDao = alternativeIngredientDao;
	}

	@Override
	public Uni<Map<String, RoleOrTechnique>> retrieveAllRolesKeyedByNormalizedName(ReactivePersistenceContext em) {
		return cachedRoles.getOrLoad(() -> roleOrTechniqueDao.findAll(em)
				.map(list -> list.stream().collect(Collectors.toMap(
						x -> normalizeRoleName(x.getName()),
						Function.identity(),
						(existing, _) -> existing,
						LinkedHashMap::new)))
				.map(Map::copyOf));
	}

	@Override
	public String normalizeRoleName(String roleName) {
		return roleName == null ? "" : roleName.trim().toLowerCase();
	}

	@Override
	public String convertRolesToMarkdownList(Collection<RoleOrTechnique> rolesOrTechniques) {
		return rolesOrTechniques.stream().map(r -> "- " + r.getName() + (r.getExplanationForLlm().map(e -> " (" + e + ")").orElse("")))
				.collect(Collectors.joining("\n"));
	}

	@Override
	public String convertInstructionsToMarkdownList(List<String> instructions) {
		return instructions.stream().map(i -> "- " + i).collect(Collectors.joining("\n"));
	}

	@Override
	public Uni<Map<String, TriggerIngredient>> retrieveAllTriggerIngredientsKeyedByNormalizedName(ReactivePersistenceContext em) {
		return cachedTriggerIngredients.getOrLoad(() -> triggerIngredientDao.findAll(em)
				.map(list -> list.stream().collect(Collectors.toMap(
						x -> normalizeRoleName(x.getName()),
						Function.identity(),
						(existing, _) -> existing,
						LinkedHashMap::new)))
				.map(Map::copyOf));
	}

	@Override
	public String normalizeTriggerIngredientName(String triggerIngredientName) {
		return triggerIngredientName == null ? "" : triggerIngredientName.trim().toLowerCase();
	}

	@Override
	public String convertTriggerIngredientsToMarkdownList(Collection<TriggerIngredient> triggerIngredients) {
		return triggerIngredients.stream().map(ti -> "- " + ti.getName() + (ti.getExplanationForLlm().map(e -> " (" + e + ")").orElse("")))
				.collect(Collectors.joining("\n"));
	}

	@Override
	public Uni<Map<String, AlternativeIngredient>> retrieveAllAlternativesKeyedByNormalizedName(ReactivePersistenceContext em) {
		return cachedAlternatives.getOrLoad(() -> alternativeIngredientDao.findAll(em)
				.map(list -> list.stream().collect(Collectors.toMap(
						x -> normalizeRoleName(x.getName()),
						Function.identity(),
						(existing, _) -> existing,
						LinkedHashMap::new)))
				.map(Map::copyOf));
	}

	@Override
	public Uni<String> assessIngredientRole(String availableRolesAsMarkdownList, String ingredientNameInRecipe, String instructionsAsMarkdownList) {
		// TODO Dummy for now, implement
		return Uni.createFrom().item(Math.random() > 0.5 ? "minced in sauce" : "cubes stew");
	}

	@Override
	public Uni<String> matchIngredientToTrigger(String availableTriggerIngredientsAsMarkdownList, String ingredientNameInRecipe, RoleOrTechnique role) {
		// TODO Dummy for now, implement
		var rnd = Math.random();
		return Uni.createFrom().item(rnd > 0.75 ? "Beef" : (rnd < 0.25 ? "Pork" : ""));
	}

	@Override
	public Uni<String> suggestAlternatives(String availableAlternativesAsMarkdownList, String recipeName, String ingredientNameInRecipe, String ingredientRoleOrTechnique) {
		// TODO Implement
		return null;
	}
}
