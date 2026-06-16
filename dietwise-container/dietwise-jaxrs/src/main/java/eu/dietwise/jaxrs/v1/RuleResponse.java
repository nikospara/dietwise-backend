package eu.dietwise.jaxrs.v1;

import java.util.List;

import eu.dietwise.v1.model.Rule;
import eu.dietwise.v1.types.RoleOrTechnique;

/**
 * A single Rule as shown in the backoffice grid: the English names of its business key plus its rationale.
 * {@code roleOrTechnique} and {@code rationale} may be {@code null}.
 */
public record RuleResponse(
		String id,
		String recommendation,
		String triggerIngredient,
		String roleOrTechnique,
		String rationale
) {
	public static RuleResponse from(Rule rule) {
		RoleOrTechnique roleOrTechnique = rule.getRoleOrTechnique();
		return new RuleResponse(
				rule.getId().asString(),
				rule.getRecommendation().asString(),
				rule.getTriggerIngredient().asString(),
				roleOrTechnique == null ? null : roleOrTechnique.asString(),
				rule.getRationale()
		);
	}

	public static List<RuleResponse> fromAll(List<Rule> rules) {
		return rules.stream().map(RuleResponse::from).toList();
	}
}
