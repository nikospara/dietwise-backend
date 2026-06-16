package eu.dietwise.jaxrs.v1;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import eu.dietwise.services.v1.types.StagedRule;
import eu.dietwise.services.v1.types.TranslationState;
import eu.dietwise.v1.model.Rule;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.RoleOrTechnique;

/**
 * A single Rule as shown in the backoffice grid: the English names of its business key plus the ids of its shared
 * reference entities, its rationale, its effective active state, its change state relative to published master, the set
 * of cells carrying a pending change, the completeness of its rationale, Trigger Ingredient and Role or Technique
 * translations (language name to state), and the Working Copy version a subsequent edit must be based on. {@code
 * roleOrTechnique}, {@code roleOrTechniqueId} and {@code rationale} may be {@code null}; {@code roleOrTechniqueTranslations}
 * is empty when the Rule has no Role or Technique.
 */
public record RuleResponse(
		String id,
		String recommendation,
		String triggerIngredient,
		String triggerIngredientId,
		String roleOrTechnique,
		String roleOrTechniqueId,
		String rationale,
		boolean active,
		String changeState,
		List<String> changedFields,
		Map<String, String> rationaleTranslations,
		Map<String, String> triggerIngredientTranslations,
		Map<String, String> roleOrTechniqueTranslations,
		long version
) {
	public static RuleResponse from(StagedRule staged) {
		Rule rule = staged.rule();
		RoleOrTechnique roleOrTechnique = rule.getRoleOrTechnique();
		return new RuleResponse(
				rule.getId().asString(),
				rule.getRecommendation().asString(),
				rule.getTriggerIngredient().asString(),
				staged.triggerIngredientId().toString(),
				roleOrTechnique == null ? null : roleOrTechnique.asString(),
				staged.roleOrTechniqueId() == null ? null : staged.roleOrTechniqueId().toString(),
				rule.getRationale(),
				rule.isActive(),
				staged.changeState().name(),
				staged.changedFields().stream().map(Enum::name).toList(),
				toStateNames(staged.rationaleTranslations()),
				toStateNames(staged.triggerIngredientTranslations()),
				toStateNames(staged.roleOrTechniqueTranslations()),
				staged.version()
		);
	}

	private static Map<String, String> toStateNames(Map<RecipeLanguage, TranslationState> states) {
		return states.entrySet().stream().collect(Collectors.toMap(entry -> entry.getKey().name(), entry -> entry.getValue().name()));
	}

	public static List<RuleResponse> fromAll(List<StagedRule> rules) {
		return rules.stream().map(RuleResponse::from).toList();
	}
}
