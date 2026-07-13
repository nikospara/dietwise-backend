package eu.dietwise.tools.publish.schema;

import static eu.dietwise.tools.publish.schema.ColumnSpec.bigint;
import static eu.dietwise.tools.publish.schema.ColumnSpec.bool;
import static eu.dietwise.tools.publish.schema.ColumnSpec.integer;
import static eu.dietwise.tools.publish.schema.ColumnSpec.text;
import static eu.dietwise.tools.publish.schema.ColumnSpec.uuid;

import java.util.List;

/**
 * The schema the publish tool operates on: every master table that has a Working Copy mirror, paired with its mirror
 * and the way the mirror is published. This registry is the single source of schema truth for the reader, the planner
 * and the renderer; it mirrors the tables created in {@code changelogs/20260616_backoffice.xml} and the master tables
 * they shadow. When the database schema of these tables changes, update this registry.
 */
public final class Tables {

	private static final List<MirrorSpec> MIRRORS = List.of(
			// order 0: reference data with no outgoing foreign keys among the mirrored set
			new MirrorSpec(
					new TableSpec("DW_RECOMMENDATION", List.of(
							uuid("id"), text("name"), text("component_for_scoring"), text("weight"),
							text("explanation_for_llm"), text("human_friendly_display")), List.of("id")),
					new TableSpec("DW_RECOMMENDATION_WC", List.of(
							uuid("id"), text("explanation_for_llm"), text("human_friendly_display"), bigint("version")),
							List.of("id")),
					PublishKind.SNAPSHOT, 0),
			new MirrorSpec(
					new TableSpec("DW_TRIGGER_INGREDIENT", List.of(
							uuid("id"), text("name"), text("explanation_for_llm")), List.of("id")),
					new TableSpec("DW_TRIGGER_INGREDIENT_WC", List.of(
							uuid("id"), text("name"), text("explanation_for_llm"), bigint("version")), List.of("id")),
					PublishKind.SNAPSHOT, 0),
			new MirrorSpec(
					new TableSpec("DW_ROLE_OR_TECHNIQUE", List.of(
							uuid("id"), text("name"), text("explanation_for_llm")), List.of("id")),
					new TableSpec("DW_ROLE_OR_TECHNIQUE_WC", List.of(
							uuid("id"), text("name"), text("explanation_for_llm"), bigint("version")), List.of("id")),
					PublishKind.SNAPSHOT, 0),
			new MirrorSpec(
					new TableSpec("DW_ALTERNATIVE_INGREDIENT", List.of(
							uuid("id"), text("name"), text("explanation_for_llm")), List.of("id")),
					new TableSpec("DW_ALTERNATIVE_INGREDIENT_WC", List.of(
							uuid("id"), text("name"), text("explanation_for_llm"), bigint("version")), List.of("id")),
					PublishKind.SNAPSHOT, 0),

			// order 1: rows referencing only order-0 reference data
			new MirrorSpec(
					new TableSpec("DW_RULE", List.of(
							uuid("id"), uuid("recommendation_id"), uuid("trigger_ingredient_id"),
							uuid("role_or_technique_id"), text("cuisine"), text("rationale"), bool("active")),
							List.of("id")),
					new TableSpec("DW_RULE_WC", List.of(
							uuid("id"), uuid("recommendation_id"), uuid("trigger_ingredient_id"),
							uuid("role_or_technique_id"), text("cuisine"), text("rationale"), bool("active"),
							bigint("version")), List.of("id")),
					PublishKind.SNAPSHOT, 1),
			new MirrorSpec(
					new TableSpec("DW_RECOMMENDATION_TRANSLATION", List.of(
							uuid("recommendation_id"), text("lang"), text("name"), text("component_for_scoring"),
							text("explanation_for_llm"), text("human_friendly_display")),
							List.of("recommendation_id", "lang")),
					new TableSpec("DW_RECOMMENDATION_TRANSLATION_WC", List.of(
							uuid("recommendation_id"), text("lang"), text("name"), text("component_for_scoring"),
							text("explanation_for_llm"), text("human_friendly_display"), bigint("version")),
							List.of("recommendation_id", "lang")),
					PublishKind.SNAPSHOT, 1),
			new MirrorSpec(
					new TableSpec("DW_TRIGGER_INGREDIENT_TRANSLATION", List.of(
							uuid("trigger_ingredient_id"), text("lang"), text("name"), text("explanation_for_llm")),
							List.of("trigger_ingredient_id", "lang")),
					new TableSpec("DW_TRIGGER_INGREDIENT_TRANSLATION_WC", List.of(
							uuid("trigger_ingredient_id"), text("lang"), text("name"), text("explanation_for_llm"),
							bigint("version")), List.of("trigger_ingredient_id", "lang")),
					PublishKind.SNAPSHOT, 1),
			new MirrorSpec(
					new TableSpec("DW_ROLE_OR_TECHNIQUE_TRANSLATION", List.of(
							uuid("role_or_technique_id"), text("lang"), text("name"), text("explanation_for_llm")),
							List.of("role_or_technique_id", "lang")),
					new TableSpec("DW_ROLE_OR_TECHNIQUE_TRANSLATION_WC", List.of(
							uuid("role_or_technique_id"), text("lang"), text("name"), text("explanation_for_llm"),
							bigint("version")), List.of("role_or_technique_id", "lang")),
					PublishKind.SNAPSHOT, 1),
			new MirrorSpec(
					new TableSpec("DW_ALTERNATIVE_INGREDIENT_TRANSLATION", List.of(
							uuid("alternative_ingredient_id"), text("lang"), text("name"), text("explanation_for_llm")),
							List.of("alternative_ingredient_id", "lang")),
					new TableSpec("DW_ALTERNATIVE_INGREDIENT_TRANSLATION_WC", List.of(
							uuid("alternative_ingredient_id"), text("lang"), text("name"), text("explanation_for_llm"),
							bigint("version")), List.of("alternative_ingredient_id", "lang")),
					PublishKind.SNAPSHOT, 1),
			new MirrorSpec(
					new TableSpec("DW_ALTERNATIVE_INGREDIENT_COMPONENTS_FOR_SCORING", List.of(
							uuid("alternative_ingredient_id"), uuid("recommendation_id")),
							List.of("alternative_ingredient_id", "recommendation_id")),
					new TableSpec("DW_ALTERNATIVE_INGREDIENT_COMPONENTS_FOR_SCORING_WC", List.of(
							uuid("alternative_ingredient_id"), uuid("recommendation_id"), bool("present")),
							List.of("alternative_ingredient_id", "recommendation_id")),
					PublishKind.LINK_DELTA, 1),
			new MirrorSpec(
					new TableSpec("DW_ALTERNATIVE_INGREDIENT_SEASONALITY", List.of(
							uuid("alternative_ingredient_id"), text("country"), integer("month_from"),
							integer("month_to")), List.of("alternative_ingredient_id", "country")),
					new TableSpec("DW_ALTERNATIVE_INGREDIENT_SEASONALITY_WC", List.of(
							uuid("alternative_ingredient_id"), text("country"), integer("month_from"),
							integer("month_to"), bigint("version")), List.of("alternative_ingredient_id", "country")),
					PublishKind.NULLABLE_PAYLOAD, 1),
			new MirrorSpec(
					new TableSpec("DW_ALTERNATIVE_INGREDIENT_COST", List.of(
							uuid("alternative_ingredient_id"), text("country"), text("cost")),
							List.of("alternative_ingredient_id", "country")),
					new TableSpec("DW_ALTERNATIVE_INGREDIENT_COST_WC", List.of(
							uuid("alternative_ingredient_id"), text("country"), text("cost"), bigint("version")),
							List.of("alternative_ingredient_id", "country")),
					PublishKind.NULLABLE_PAYLOAD, 1),

			// order 2: rows referencing order-1 rows
			new MirrorSpec(
					new TableSpec("DW_SUGGESTION_TEMPLATE", List.of(
							uuid("id"), uuid("rule_id"), uuid("alternative_ingredient_id"), integer("alternative_order"),
							text("restriction"), text("equivalence"), text("technique_notes"), bool("active")),
							List.of("id")),
					new TableSpec("DW_SUGGESTION_TEMPLATE_WC", List.of(
							uuid("id"), uuid("rule_id"), uuid("alternative_ingredient_id"), integer("alternative_order"),
							text("restriction"), text("equivalence"), text("technique_notes"), bool("active"),
							bigint("version")), List.of("id")),
					PublishKind.SNAPSHOT, 2),
			new MirrorSpec(
					new TableSpec("DW_RULE_TRANSLATION", List.of(
							uuid("rule_id"), text("lang"), text("rationale")), List.of("rule_id", "lang")),
					new TableSpec("DW_RULE_TRANSLATION_WC", List.of(
							uuid("rule_id"), text("lang"), text("rationale"), bigint("version")),
							List.of("rule_id", "lang")),
					PublishKind.SNAPSHOT, 2),

			// order 3: rows referencing order-2 rows
			new MirrorSpec(
					new TableSpec("DW_SUGGESTION_TEMPLATE_TRANSLATION", List.of(
							uuid("suggestion_template_id"), text("lang"), text("restriction"), text("equivalence"),
							text("technique_notes")), List.of("suggestion_template_id", "lang")),
					new TableSpec("DW_SUGGESTION_TEMPLATE_TRANSLATION_WC", List.of(
							uuid("suggestion_template_id"), text("lang"), text("restriction"), text("equivalence"),
							text("technique_notes"), bigint("version")), List.of("suggestion_template_id", "lang")),
					PublishKind.SNAPSHOT, 3)
	);

	private Tables() {
	}

	/** Every mirrored table pair, ordered ascending by foreign-key rank (parents before children). */
	public static List<MirrorSpec> all() {
		return MIRRORS;
	}

	public static int mirrorCount() {
		return MIRRORS.size();
	}
}
