package eu.dietwise.dao.impl.suggestions;

import java.util.List;
import java.util.UUID;

import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

/**
 * Inserts Rule + Suggestion Template rows for DAO tests that need templates with explicit {@code active} flags.
 * <p>
 * The rows are written with native SQL because {@code DW_SUGGESTION_TEMPLATE.alternative_order} is {@code NOT NULL}
 * and is owned by the {@code @OrderColumn} on {@code RuleEntity.alternatives}: persisting the entity graph inserts a
 * template without that column and then updates it, which trips the not-null constraint. Native SQL sets the order
 * directly. Each Rule is given its own Role or Technique so the (recommendation, trigger, role) business key stays
 * unique.
 */
final class SuggestionTemplateFixtures {
	private SuggestionTemplateFixtures() {
	}

	/**
	 * A Suggestion Template to insert: its id, the seeded AlternativeIngredient it suggests, its position within the
	 * Rule, its English restriction, and whether it is active.
	 */
	record Template(UUID id, UUID alternativeIngredientId, int order, String restriction, boolean active) {
	}

	/**
	 * Insert a Rule (with a dedicated Role or Technique) and its templates, reusing the seeded recommendation and
	 * trigger ingredient. Statements run in order so foreign keys resolve; run this inside a transaction.
	 */
	static Uni<Void> insertRuleWithTemplates(
			Mutiny.Session session,
			UUID ruleId,
			UUID recommendationId,
			UUID triggerIngredientId,
			String roleName,
			List<Template> templates
	) {
		UUID roleId = ruleId;
		Uni<Void> chain = session.createNativeQuery("insert into DW_ROLE_OR_TECHNIQUE (id, name) values (:id, :name)")
				.setParameter("id", roleId)
				.setParameter("name", roleName)
				.executeUpdate()
				.chain(() -> session.createNativeQuery(
								"insert into DW_RULE (id, recommendation_id, trigger_ingredient_id, role_or_technique_id) "
										+ "values (:id, :recommendation, :trigger, :role)")
						.setParameter("id", ruleId)
						.setParameter("recommendation", recommendationId)
						.setParameter("trigger", triggerIngredientId)
						.setParameter("role", roleId)
						.executeUpdate())
				.replaceWithVoid();
		for (var template : templates) {
			chain = chain.chain(() -> session.createNativeQuery(
							"insert into DW_SUGGESTION_TEMPLATE "
									+ "(id, rule_id, alternative_ingredient_id, alternative_order, restriction, active) "
									+ "values (:id, :rule, :alternative, :ord, :restriction, :active)")
					.setParameter("id", template.id())
					.setParameter("rule", ruleId)
					.setParameter("alternative", template.alternativeIngredientId())
					.setParameter("ord", template.order())
					.setParameter("restriction", template.restriction())
					.setParameter("active", template.active())
					.executeUpdate()
					.replaceWithVoid());
		}
		return chain;
	}
}
