package eu.dietwise.tools.publish;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import eu.dietwise.tools.publish.model.Cell;
import eu.dietwise.tools.publish.model.ColumnChange;
import eu.dietwise.tools.publish.model.DeleteRow;
import eu.dietwise.tools.publish.model.InsertRow;
import eu.dietwise.tools.publish.model.PublishPlan;
import eu.dietwise.tools.publish.model.Row;
import eu.dietwise.tools.publish.model.TableSnapshot;
import eu.dietwise.tools.publish.model.UpdateRow;
import eu.dietwise.tools.publish.schema.ColumnType;
import org.junit.jupiter.api.Test;

class PublishPlannerTest {

	private static final String TI_ID = "11111111-1111-1111-1111-111111111111";
	private static final String RULE_ID = "22222222-2222-2222-2222-222222222222";
	private static final String REC_ID = "33333333-3333-3333-3333-333333333333";
	private static final String ALT_ID = "44444444-4444-4444-4444-444444444444";
	private static final String TEMPLATE_ID = "55555555-5555-5555-5555-555555555555";
	private static final String LANG = "el";

	private final PublishPlanner sut = new PublishPlanner();

	@Test
	void newEntityBecomesAnInsertOfItsMasterColumnsWithoutVersion() {
		Map<String, List<Row>> wc = Map.of("DW_TRIGGER_INGREDIENT_WC", List.of(
				Row.builder().set("id", TI_ID).set("name", "Beef").set("explanation_for_llm", "red meat")
						.set("version", "1").build()));

		PublishPlan plan = sut.plan(Map.of(), wc);

		assertThat(plan.inserts()).containsExactly(new InsertRow("DW_TRIGGER_INGREDIENT", List.of(
				new Cell("id", ColumnType.UUID, TI_ID),
				new Cell("name", ColumnType.STRING, "Beef"),
				new Cell("explanation_for_llm", ColumnType.STRING, "red meat"))));
		assertThat(plan.updates()).isEmpty();
		assertThat(plan.deletes()).isEmpty();
	}

	@Test
	void changedFieldBecomesAnUpdateOfOnlyThatColumn() {
		Map<String, List<Row>> master = Map.of("DW_RULE", List.of(rule("old rationale", "true")));
		Map<String, List<Row>> wc = Map.of("DW_RULE_WC", List.of(ruleWc("new rationale", "true")));

		PublishPlan plan = sut.plan(master, wc);

		assertThat(plan.updates()).containsExactly(new UpdateRow("DW_RULE",
				List.of(new Cell("id", ColumnType.UUID, RULE_ID)),
				List.of(new ColumnChange("rationale", ColumnType.STRING, "old rationale", "new rationale"))));
		assertThat(plan.inserts()).isEmpty();
		assertThat(plan.deletes()).isEmpty();
	}

	@Test
	void stagedDeactivationBecomesAnUpdateOfTheActiveColumn() {
		Map<String, List<Row>> master = Map.of("DW_RULE", List.of(rule("same", "true")));
		Map<String, List<Row>> wc = Map.of("DW_RULE_WC", List.of(ruleWc("same", "false")));

		PublishPlan plan = sut.plan(master, wc);

		assertThat(plan.updates()).containsExactly(new UpdateRow("DW_RULE",
				List.of(new Cell("id", ColumnType.UUID, RULE_ID)),
				List.of(new ColumnChange("active", ColumnType.BOOLEAN, "true", "false"))));
	}

	@Test
	void aWorkingCopyRowEqualToMasterProducesNoMasterChange() {
		Map<String, List<Row>> master = Map.of("DW_RULE", List.of(rule("same", "true")));
		Map<String, List<Row>> wc = Map.of("DW_RULE_WC", List.of(ruleWc("same", "true")));

		PublishPlan plan = sut.plan(master, wc);

		assertThat(plan.inserts()).isEmpty();
		assertThat(plan.updates()).isEmpty();
		assertThat(plan.deletes()).isEmpty();
	}

	@Test
	void componentPresentAndAbsentInMasterBecomesALinkInsert() {
		Map<String, List<Row>> wc = Map.of("DW_ALTERNATIVE_INGREDIENT_COMPONENTS_FOR_SCORING_WC", List.of(
				component(ALT_ID, REC_ID, "true")));

		PublishPlan plan = sut.plan(Map.of(), wc);

		assertThat(plan.inserts()).containsExactly(
				new InsertRow("DW_ALTERNATIVE_INGREDIENT_COMPONENTS_FOR_SCORING", List.of(
						new Cell("alternative_ingredient_id", ColumnType.UUID, ALT_ID),
						new Cell("recommendation_id", ColumnType.UUID, REC_ID))));
	}

	@Test
	void componentAbsentAndPresentInMasterBecomesALinkDelete() {
		Map<String, List<Row>> master = Map.of("DW_ALTERNATIVE_INGREDIENT_COMPONENTS_FOR_SCORING", List.of(
				component(ALT_ID, REC_ID, null)));
		Map<String, List<Row>> wc = Map.of("DW_ALTERNATIVE_INGREDIENT_COMPONENTS_FOR_SCORING_WC", List.of(
				component(ALT_ID, REC_ID, "false")));

		PublishPlan plan = sut.plan(master, wc);

		assertThat(plan.deletes()).containsExactly(
				new DeleteRow("DW_ALTERNATIVE_INGREDIENT_COMPONENTS_FOR_SCORING", List.of(
						new Cell("alternative_ingredient_id", ColumnType.UUID, ALT_ID),
						new Cell("recommendation_id", ColumnType.UUID, REC_ID)),
						List.of("alternative_ingredient_id", "recommendation_id")));
		assertThat(plan.inserts()).isEmpty();
	}

	@Test
	void componentPresentAndAlreadyInMasterIsANoop() {
		Map<String, List<Row>> master = Map.of("DW_ALTERNATIVE_INGREDIENT_COMPONENTS_FOR_SCORING", List.of(
				component(ALT_ID, REC_ID, null)));
		Map<String, List<Row>> wc = Map.of("DW_ALTERNATIVE_INGREDIENT_COMPONENTS_FOR_SCORING_WC", List.of(
				component(ALT_ID, REC_ID, "true")));

		PublishPlan plan = sut.plan(master, wc);

		assertThat(plan.inserts()).isEmpty();
		assertThat(plan.deletes()).isEmpty();
	}

	@Test
	void seasonalityStagedEmptyDeletesTheMasterRow() {
		Map<String, List<Row>> master = Map.of("DW_ALTERNATIVE_INGREDIENT_SEASONALITY", List.of(
				seasonality("3", "8")));
		Map<String, List<Row>> wc = Map.of("DW_ALTERNATIVE_INGREDIENT_SEASONALITY_WC", List.of(
				seasonalityWc(null, null)));

		PublishPlan plan = sut.plan(master, wc);

		assertThat(plan.deletes()).containsExactly(
				new DeleteRow("DW_ALTERNATIVE_INGREDIENT_SEASONALITY", List.of(
						new Cell("alternative_ingredient_id", ColumnType.UUID, ALT_ID),
						new Cell("country", ColumnType.STRING, "GR"),
						new Cell("month_from", ColumnType.INT, "3"),
						new Cell("month_to", ColumnType.INT, "8")),
						List.of("alternative_ingredient_id", "country")));
	}

	@Test
	void seasonalityChangeUpdatesTheChangedColumns() {
		Map<String, List<Row>> master = Map.of("DW_ALTERNATIVE_INGREDIENT_SEASONALITY", List.of(
				seasonality("3", "8")));
		Map<String, List<Row>> wc = Map.of("DW_ALTERNATIVE_INGREDIENT_SEASONALITY_WC", List.of(
				seasonalityWc("4", "8")));

		PublishPlan plan = sut.plan(master, wc);

		assertThat(plan.updates()).containsExactly(new UpdateRow("DW_ALTERNATIVE_INGREDIENT_SEASONALITY",
				List.of(new Cell("alternative_ingredient_id", ColumnType.UUID, ALT_ID),
						new Cell("country", ColumnType.STRING, "GR")),
				List.of(new ColumnChange("month_from", ColumnType.INT, "3", "4"))));
	}

	@Test
	void seasonalityNewInsertsTheMasterRow() {
		Map<String, List<Row>> wc = Map.of("DW_ALTERNATIVE_INGREDIENT_SEASONALITY_WC", List.of(
				seasonalityWc("3", "8")));

		PublishPlan plan = sut.plan(Map.of(), wc);

		assertThat(plan.inserts()).containsExactly(
				new InsertRow("DW_ALTERNATIVE_INGREDIENT_SEASONALITY", List.of(
						new Cell("alternative_ingredient_id", ColumnType.UUID, ALT_ID),
						new Cell("country", ColumnType.STRING, "GR"),
						new Cell("month_from", ColumnType.INT, "3"),
						new Cell("month_to", ColumnType.INT, "8"))));
	}

	@Test
	void costStagedEmptyDeletesTheMasterRow() {
		Map<String, List<Row>> master = Map.of("DW_ALTERNATIVE_INGREDIENT_COST", List.of(
				Row.builder().set("alternative_ingredient_id", ALT_ID).set("country", "GR").set("cost", "MED").build()));
		Map<String, List<Row>> wc = Map.of("DW_ALTERNATIVE_INGREDIENT_COST_WC", List.of(
				Row.builder().set("alternative_ingredient_id", ALT_ID).set("country", "GR").set("cost", null)
						.set("version", "2").build()));

		PublishPlan plan = sut.plan(master, wc);

		assertThat(plan.deletes()).containsExactly(
				new DeleteRow("DW_ALTERNATIVE_INGREDIENT_COST", List.of(
						new Cell("alternative_ingredient_id", ColumnType.UUID, ALT_ID),
						new Cell("country", ColumnType.STRING, "GR"),
						new Cell("cost", ColumnType.STRING, "MED")),
						List.of("alternative_ingredient_id", "country")));
	}

	@Test
	void insertsAreOrderedParentsBeforeChildren() {
		Map<String, List<Row>> wc = Map.of(
				"DW_TRIGGER_INGREDIENT_WC", List.of(Row.builder().set("id", TI_ID).set("name", "Beef")
						.set("explanation_for_llm", null).set("version", "1").build()),
				"DW_ALTERNATIVE_INGREDIENT_WC", List.of(Row.builder().set("id", ALT_ID).set("name", "Lentils")
						.set("explanation_for_llm", null).set("version", "1").build()),
				"DW_RULE_WC", List.of(ruleWc("rationale", "true")),
				"DW_SUGGESTION_TEMPLATE_WC", List.of(templateWc()));

		PublishPlan plan = sut.plan(Map.of(), wc);

		List<String> tables = plan.inserts().stream().map(InsertRow::table).toList();
		assertThat(tables.indexOf("DW_TRIGGER_INGREDIENT")).isLessThan(tables.indexOf("DW_RULE"));
		assertThat(tables.indexOf("DW_ALTERNATIVE_INGREDIENT")).isLessThan(tables.indexOf("DW_SUGGESTION_TEMPLATE"));
		assertThat(tables.indexOf("DW_RULE")).isLessThan(tables.indexOf("DW_SUGGESTION_TEMPLATE"));
	}

	@Test
	void workingCopySnapshotCapturesEveryNonEmptyTableForRestore() {
		Map<String, List<Row>> wc = Map.of(
				"DW_TRIGGER_INGREDIENT_WC", List.of(Row.builder().set("id", TI_ID).set("name", "Beef")
						.set("explanation_for_llm", null).set("version", "1").build()),
				"DW_RULE_WC", List.of(ruleWc("rationale", "true")));

		PublishPlan plan = sut.plan(Map.of(), wc);

		assertThat(plan.workingCopy()).hasSize(2);
		assertThat(plan.workingCopy()).extracting("table")
				.containsExactlyInAnyOrder("DW_TRIGGER_INGREDIENT_WC", "DW_RULE_WC");
	}

	@Test
	void dirtyStringsAreCleanedInInserts() {
		Map<String, List<Row>> wc = Map.of("DW_TRIGGER_INGREDIENT_WC", List.of(
				Row.builder().set("id", TI_ID).set("name", "  Beef ").set("explanation_for_llm", "red\tmeat\n\nsource")
						.set("version", "1").build()));

		PublishPlan plan = sut.plan(Map.of(), wc);

		assertThat(plan.inserts()).containsExactly(new InsertRow("DW_TRIGGER_INGREDIENT", List.of(
				new Cell("id", ColumnType.UUID, TI_ID),
				new Cell("name", ColumnType.STRING, "Beef"),
				new Cell("explanation_for_llm", ColumnType.STRING, "red meat source"))));
	}

	@Test
	void dirtyStringsAreCleanedInBothDirectionsOfAnUpdate() {
		Map<String, List<Row>> master = Map.of("DW_RULE", List.of(rule("old    rationale", "true")));
		Map<String, List<Row>> wc = Map.of("DW_RULE_WC", List.of(ruleWc("new\trationale", "true")));

		PublishPlan plan = sut.plan(master, wc);

		assertThat(plan.updates()).containsExactly(new UpdateRow("DW_RULE",
				List.of(new Cell("id", ColumnType.UUID, RULE_ID)),
				List.of(new ColumnChange("rationale", ColumnType.STRING, "old rationale", "new rationale"))));
	}

	@Test
	void aDifferenceOnlyInWhitespaceIsNotAChange() {
		Map<String, List<Row>> master = Map.of("DW_RULE", List.of(rule("same rationale", "true")));
		Map<String, List<Row>> wc = Map.of("DW_RULE_WC", List.of(ruleWc("  same   rationale ", "true")));

		PublishPlan plan = sut.plan(master, wc);

		assertThat(plan.inserts()).isEmpty();
		assertThat(plan.updates()).isEmpty();
		assertThat(plan.deletes()).isEmpty();
	}

	@Test
	void theWorkingCopySnapshotIsCleanedForRollbackRestore() {
		Map<String, List<Row>> wc = Map.of("DW_TRIGGER_INGREDIENT_WC", List.of(
				Row.builder().set("id", TI_ID).set("name", "  Beef ").set("explanation_for_llm", "red\tmeat")
						.set("version", "1").build()));

		PublishPlan plan = sut.plan(Map.of(), wc);

		assertThat(plan.workingCopy()).containsExactly(new TableSnapshot("DW_TRIGGER_INGREDIENT_WC", List.of(List.of(
				new Cell("id", ColumnType.UUID, TI_ID),
				new Cell("name", ColumnType.STRING, "Beef"),
				new Cell("explanation_for_llm", ColumnType.STRING, "red meat"),
				new Cell("version", ColumnType.BIGINT, "1")))));
	}

	@Test
	void aSuggestionTemplateWhoseRuleIsMissingIsDroppedNotPublished() {
		Map<String, List<Row>> wc = Map.of("DW_SUGGESTION_TEMPLATE_WC", List.of(templateWc()));

		PublishPlan plan = sut.plan(Map.of(), wc);

		assertThat(plan.inserts()).isEmpty();
		assertThat(plan.updates()).isEmpty();
		// the orphan is not restored on rollback (empty snapshot rows) but the table is still cleared and fingerprinted
		assertThat(plan.workingCopy()).containsExactly(new TableSnapshot("DW_SUGGESTION_TEMPLATE_WC", List.of()));
		assertThat(plan.workingCopyFingerprint()).isEqualTo(2L);
	}

	@Test
	void aSuggestionTemplateWhoseRuleIsStagedInTheSamePublishIsPublished() {
		Map<String, List<Row>> wc = Map.of(
				"DW_RULE_WC", List.of(ruleWc("rationale", "true")),
				"DW_SUGGESTION_TEMPLATE_WC", List.of(templateWc()));

		PublishPlan plan = sut.plan(Map.of(), wc);

		assertThat(plan.inserts()).extracting(InsertRow::table).contains("DW_SUGGESTION_TEMPLATE");
	}

	@Test
	void aSuggestionTemplateWhoseRuleExistsInMasterIsPublished() {
		Map<String, List<Row>> master = Map.of("DW_RULE", List.of(rule("rationale", "true")));
		Map<String, List<Row>> wc = Map.of("DW_SUGGESTION_TEMPLATE_WC", List.of(templateWc()));

		PublishPlan plan = sut.plan(master, wc);

		assertThat(plan.inserts()).extracting(InsertRow::table).contains("DW_SUGGESTION_TEMPLATE");
	}

	@Test
	void aRuleTranslationWhoseRuleIsMissingIsDroppedNotPublished() {
		Map<String, List<Row>> wc = Map.of("DW_RULE_TRANSLATION_WC", List.of(ruleTranslationWc()));

		PublishPlan plan = sut.plan(Map.of(), wc);

		assertThat(plan.inserts()).isEmpty();
		assertThat(plan.workingCopy()).containsExactly(new TableSnapshot("DW_RULE_TRANSLATION_WC", List.of()));
	}

	@Test
	void aSuggestionTemplateTranslationWhoseTemplateIsMissingIsDroppedNotPublished() {
		Map<String, List<Row>> wc = Map.of(
				"DW_SUGGESTION_TEMPLATE_TRANSLATION_WC", List.of(templateTranslationWc()));

		PublishPlan plan = sut.plan(Map.of(), wc);

		assertThat(plan.inserts()).isEmpty();
	}

	@Test
	void aSuggestionTemplateTranslationWhoseTemplateIsItselfAnOrphanIsDropped() {
		// the template is staged but its rule is missing, so the template is dropped; its translation must be dropped too
		Map<String, List<Row>> wc = Map.of(
				"DW_SUGGESTION_TEMPLATE_WC", List.of(templateWc()),
				"DW_SUGGESTION_TEMPLATE_TRANSLATION_WC", List.of(templateTranslationWc()));

		PublishPlan plan = sut.plan(Map.of(), wc);

		assertThat(plan.inserts()).isEmpty();
	}

	@Test
	void aSuggestionTemplateTranslationWhoseTemplateIsPublishedInTheSamePublishIsPublished() {
		Map<String, List<Row>> wc = Map.of(
				"DW_RULE_WC", List.of(ruleWc("rationale", "true")),
				"DW_SUGGESTION_TEMPLATE_WC", List.of(templateWc()),
				"DW_SUGGESTION_TEMPLATE_TRANSLATION_WC", List.of(templateTranslationWc()));

		PublishPlan plan = sut.plan(Map.of(), wc);

		assertThat(plan.inserts()).extracting(InsertRow::table).contains("DW_SUGGESTION_TEMPLATE_TRANSLATION");
	}

	@Test
	void fingerprintSumsRowCountsAndVersions() {
		Map<String, List<Row>> wc = Map.of(
				"DW_TRIGGER_INGREDIENT_WC", List.of(
						Row.builder().set("id", TI_ID).set("name", "a").set("explanation_for_llm", null)
								.set("version", "1").build(),
						Row.builder().set("id", ALT_ID).set("name", "b").set("explanation_for_llm", null)
								.set("version", "3").build()),
				"DW_ALTERNATIVE_INGREDIENT_COMPONENTS_FOR_SCORING_WC", List.of(component(ALT_ID, REC_ID, "true")));

		PublishPlan plan = sut.plan(Map.of(), wc);

		// versioned table: 2 rows + (1 + 3) versions = 6; link-delta table: 1 row, no version = 1; total 7
		assertThat(plan.workingCopyFingerprint()).isEqualTo(7L);
	}

	private static Row rule(String rationale, String active) {
		return Row.builder().set("id", RULE_ID).set("recommendation_id", REC_ID).set("trigger_ingredient_id", TI_ID)
				.set("role_or_technique_id", null).set("cuisine", null).set("rationale", rationale)
				.set("active", active).build();
	}

	private static Row ruleWc(String rationale, String active) {
		return Row.builder().set("id", RULE_ID).set("recommendation_id", REC_ID).set("trigger_ingredient_id", TI_ID)
				.set("role_or_technique_id", null).set("cuisine", null).set("rationale", rationale)
				.set("active", active).set("version", "2").build();
	}

	private static Row templateWc() {
		return Row.builder().set("id", TEMPLATE_ID).set("rule_id", RULE_ID).set("alternative_ingredient_id", ALT_ID)
				.set("alternative_order", "0").set("restriction", null).set("equivalence", null)
				.set("technique_notes", null).set("active", "true").set("version", "1").build();
	}

	private static Row ruleTranslationWc() {
		return Row.builder().set("rule_id", RULE_ID).set("lang", LANG).set("rationale", "translated rationale")
				.set("version", "1").build();
	}

	private static Row templateTranslationWc() {
		return Row.builder().set("suggestion_template_id", TEMPLATE_ID).set("lang", LANG).set("restriction", null)
				.set("equivalence", null).set("technique_notes", null).set("version", "1").build();
	}

	private static Row component(String altId, String recId, String present) {
		Row.Builder builder = Row.builder().set("alternative_ingredient_id", altId).set("recommendation_id", recId);
		if (present != null) {
			builder.set("present", present);
		}
		return builder.build();
	}

	private static Row seasonality(String monthFrom, String monthTo) {
		return Row.builder().set("alternative_ingredient_id", ALT_ID).set("country", "GR")
				.set("month_from", monthFrom).set("month_to", monthTo).build();
	}

	private static Row seasonalityWc(String monthFrom, String monthTo) {
		return Row.builder().set("alternative_ingredient_id", ALT_ID).set("country", "GR")
				.set("month_from", monthFrom).set("month_to", monthTo).set("version", "2").build();
	}
}
