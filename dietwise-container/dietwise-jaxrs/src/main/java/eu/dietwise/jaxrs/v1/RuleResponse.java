package eu.dietwise.jaxrs.v1;

import java.util.List;

import eu.dietwise.services.v1.types.StagedRule;
import eu.dietwise.v1.model.Rule;
import eu.dietwise.v1.types.RoleOrTechnique;

/**
 * A single Rule as shown in the backoffice grid: the English names of its business key plus the ids of its shared
 * reference entities, its rationale, its effective active state, its change state relative to published master, the set
 * of cells carrying a pending change, and the Working Copy version a subsequent edit must be based on.
 * {@code roleOrTechnique}, {@code roleOrTechniqueId} and {@code rationale} may be {@code null}.
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
				staged.version()
		);
	}

	public static List<RuleResponse> fromAll(List<StagedRule> rules) {
		return rules.stream().map(RuleResponse::from).toList();
	}
}
