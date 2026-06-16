package eu.dietwise.jaxrs.v1;

import java.util.List;

import eu.dietwise.services.v1.StagedRule;
import eu.dietwise.v1.model.Rule;
import eu.dietwise.v1.types.RoleOrTechnique;

/**
 * A single Rule as shown in the backoffice grid: the English names of its business key plus its rationale, its
 * change state relative to published master, and the Working Copy version a subsequent edit must be based on.
 * {@code roleOrTechnique} and {@code rationale} may be {@code null}.
 */
public record RuleResponse(
		String id,
		String recommendation,
		String triggerIngredient,
		String roleOrTechnique,
		String rationale,
		String changeState,
		long version
) {
	public static RuleResponse from(StagedRule staged) {
		Rule rule = staged.rule();
		RoleOrTechnique roleOrTechnique = rule.getRoleOrTechnique();
		return new RuleResponse(
				rule.getId().asString(),
				rule.getRecommendation().asString(),
				rule.getTriggerIngredient().asString(),
				roleOrTechnique == null ? null : roleOrTechnique.asString(),
				rule.getRationale(),
				staged.changeState().name(),
				staged.version()
		);
	}

	public static List<RuleResponse> fromAll(List<StagedRule> rules) {
		return rules.stream().map(RuleResponse::from).toList();
	}
}
