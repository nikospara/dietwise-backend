package eu.dietwise.tools.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import eu.dietwise.tools.publish.model.Cell;
import eu.dietwise.tools.publish.model.ColumnChange;
import eu.dietwise.tools.publish.model.DeleteRow;
import eu.dietwise.tools.publish.model.InsertRow;
import eu.dietwise.tools.publish.model.PublishPlan;
import eu.dietwise.tools.publish.model.TableSnapshot;
import eu.dietwise.tools.publish.model.UpdateRow;
import eu.dietwise.tools.publish.schema.ColumnType;
import org.junit.jupiter.api.Test;

class ChangelogRendererTest {

	private static final String ID = "publish_working_copy_test";
	private static final String AUTHOR = "publish-tool";
	private static final String TI_ID = "11111111-1111-1111-1111-111111111111";
	private static final String RULE_ID = "22222222-2222-2222-2222-222222222222";
	private static final String ALT_ID = "44444444-4444-4444-4444-444444444444";

	private final ChangelogRenderer sut = new ChangelogRenderer();

	@Test
	void rendersTheChangeSetHeaderWithIdAndAuthor() {
		String xml = sut.render(empty(), ID, AUTHOR);

		assertThat(xml).contains("<changeSet id=\"" + ID + "\" author=\"" + AUTHOR + "\">");
		assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		assertThat(xml).contains("dbchangelog-5.0.xsd");
	}

	@Test
	void rendersInsertWithTypedColumnsAndDeleteRollback() {
		PublishPlan plan = new PublishPlan(List.of(new InsertRow("DW_TRIGGER_INGREDIENT", List.of(
				new Cell("id", ColumnType.UUID, TI_ID),
				new Cell("name", ColumnType.STRING, "Beef"),
				new Cell("explanation_for_llm", ColumnType.STRING, null)))),
				List.of(), List.of(), List.of(), 0L);

		String xml = sut.render(plan, ID, AUTHOR);

		assertThat(xml).contains("<insert tableName=\"DW_TRIGGER_INGREDIENT\">");
		assertThat(xml).contains("<column name=\"id\" value=\"" + TI_ID + "\"/>");
		assertThat(xml).contains("<column name=\"name\" value=\"Beef\"/>");
		assertThat(xml).contains("<column name=\"explanation_for_llm\" valueComputed=\"NULL\"/>");
		// rollback deletes the inserted row by its primary key
		assertThat(rollback(xml)).contains("<delete tableName=\"DW_TRIGGER_INGREDIENT\">");
		assertThat(rollback(xml)).contains("id = '" + TI_ID + "'");
	}

	@Test
	void rendersUpdateWithWhereAndBeforeValueRollback() {
		PublishPlan plan = new PublishPlan(List.of(),
				List.of(new UpdateRow("DW_RULE",
						List.of(new Cell("id", ColumnType.UUID, RULE_ID)),
						List.of(new ColumnChange("rationale", ColumnType.STRING, "old", "new")))),
				List.of(), List.of(), 0L);

		String xml = sut.render(plan, ID, AUTHOR);

		assertThat(forward(xml)).contains("<update tableName=\"DW_RULE\">");
		assertThat(forward(xml)).contains("<column name=\"rationale\" value=\"new\"/>");
		assertThat(forward(xml)).contains("<where>id = '" + RULE_ID + "'</where>");
		assertThat(rollback(xml)).contains("<column name=\"rationale\" value=\"old\"/>");
	}

	@Test
	void rendersDeleteWithReinsertRollback() {
		PublishPlan plan = new PublishPlan(List.of(), List.of(),
				List.of(new DeleteRow("DW_ALTERNATIVE_INGREDIENT_SEASONALITY", List.of(
						new Cell("alternative_ingredient_id", ColumnType.UUID, ALT_ID),
						new Cell("country", ColumnType.STRING, "GR"),
						new Cell("month_from", ColumnType.INT, "3"),
						new Cell("month_to", ColumnType.INT, "8")),
						List.of("alternative_ingredient_id", "country"))),
				List.of(), 0L);

		String xml = sut.render(plan, ID, AUTHOR);

		assertThat(forward(xml)).contains("<delete tableName=\"DW_ALTERNATIVE_INGREDIENT_SEASONALITY\">");
		assertThat(forward(xml)).contains("<where>alternative_ingredient_id = '" + ALT_ID + "' AND country = 'GR'</where>");
		// rollback re-inserts the full deleted row, numeric columns as valueNumeric
		assertThat(rollback(xml)).contains("<insert tableName=\"DW_ALTERNATIVE_INGREDIENT_SEASONALITY\">");
		assertThat(rollback(xml)).contains("<column name=\"month_from\" valueNumeric=\"3\"/>");
	}

	@Test
	void rendersNumericAndBooleanColumns() {
		PublishPlan plan = new PublishPlan(List.of(new InsertRow("DW_SUGGESTION_TEMPLATE", List.of(
				new Cell("alternative_order", ColumnType.INT, "0"),
				new Cell("active", ColumnType.BOOLEAN, "true")))),
				List.of(), List.of(), List.of(), 0L);

		String xml = sut.render(plan, ID, AUTHOR);

		assertThat(xml).contains("<column name=\"alternative_order\" valueNumeric=\"0\"/>");
		assertThat(xml).contains("<column name=\"active\" valueBoolean=\"true\"/>");
	}

	@Test
	void clearsWorkingCopyForwardAndRestoresItOnRollback() {
		TableSnapshot snapshot = new TableSnapshot("DW_RULE_WC", List.of(List.of(
				new Cell("id", ColumnType.UUID, RULE_ID),
				new Cell("rationale", ColumnType.STRING, "staged"),
				new Cell("version", ColumnType.BIGINT, "2"))));
		PublishPlan plan = new PublishPlan(List.of(), List.of(), List.of(), List.of(snapshot), 0L);

		String xml = sut.render(plan, ID, AUTHOR);

		assertThat(forward(xml)).contains("<delete tableName=\"DW_RULE_WC\"/>");
		assertThat(rollback(xml)).contains("<insert tableName=\"DW_RULE_WC\">");
		assertThat(rollback(xml)).contains("<column name=\"version\" valueNumeric=\"2\"/>");
	}

	@Test
	void rendersPreconditionFingerprintOverTheWorkingCopyTables() {
		String xml = sut.render(new PublishPlan(List.of(), List.of(), List.of(), List.of(), 7L), ID, AUTHOR);

		assertThat(xml).contains("<preConditions onFail=\"HALT\"");
		assertThat(xml).contains("<sqlCheck expectedResult=\"7\">");
		assertThat(xml).contains("dw_rule_wc");
		assertThat(xml).contains("dw_alternative_ingredient_components_for_scoring_wc");
		assertThat(xml).contains("coalesce(sum(version),0)");
	}

	@Test
	void escapesXmlSpecialCharactersInValues() {
		PublishPlan plan = new PublishPlan(List.of(new InsertRow("DW_TRIGGER_INGREDIENT", List.of(
				new Cell("name", ColumnType.STRING, "salt & pepper < \"spice\"")))),
				List.of(), List.of(), List.of(), 0L);

		String xml = sut.render(plan, ID, AUTHOR);

		assertThat(xml).contains("value=\"salt &amp; pepper &lt; &quot;spice&quot;\"");
	}

	@Test
	void escapesSingleQuotesInWhereClauseAsSqlLiterals() {
		PublishPlan plan = new PublishPlan(List.of(), List.of(),
				List.of(new DeleteRow("DW_ALTERNATIVE_INGREDIENT_COST", List.of(
						new Cell("alternative_ingredient_id", ColumnType.UUID, ALT_ID),
						new Cell("country", ColumnType.STRING, "d'A"),
						new Cell("cost", ColumnType.STRING, "MED")),
						List.of("alternative_ingredient_id", "country"))),
				List.of(), 0L);

		String xml = sut.render(plan, ID, AUTHOR);

		assertThat(forward(xml)).contains("country = 'd''A'");
	}

	@Test
	void forwardAppliesThenRollbackUndoesInReverseOrder() {
		PublishPlan plan = new PublishPlan(
				List.of(new InsertRow("DW_TRIGGER_INGREDIENT", List.of(new Cell("id", ColumnType.UUID, TI_ID)))),
				List.of(new UpdateRow("DW_RULE", List.of(new Cell("id", ColumnType.UUID, RULE_ID)),
						List.of(new ColumnChange("rationale", ColumnType.STRING, "old", "new")))),
				List.of(),
				List.of(new TableSnapshot("DW_RULE_WC",
						List.of(List.of(new Cell("id", ColumnType.UUID, RULE_ID))))),
				0L);

		String xml = sut.render(plan, ID, AUTHOR);
		String forward = forward(xml);
		String rollback = rollback(xml);

		// forward: master changes first, Working Copy cleared last
		assertThat(forward.indexOf("<insert tableName=\"DW_TRIGGER_INGREDIENT\">"))
				.isLessThan(forward.indexOf("<delete tableName=\"DW_RULE_WC\"/>"));
		// rollback: Working Copy restored first, inserted master rows deleted last
		assertThat(rollback.indexOf("<insert tableName=\"DW_RULE_WC\">"))
				.isLessThan(rollback.indexOf("<delete tableName=\"DW_TRIGGER_INGREDIENT\">"));
	}

	@Test
	void producesWellFormedXml() {
		PublishPlan plan = new PublishPlan(
				List.of(new InsertRow("DW_TRIGGER_INGREDIENT", List.of(
						new Cell("id", ColumnType.UUID, TI_ID),
						new Cell("explanation_for_llm", ColumnType.STRING, null)))),
				List.of(new UpdateRow("DW_RULE", List.of(new Cell("id", ColumnType.UUID, RULE_ID)),
						List.of(new ColumnChange("rationale", ColumnType.STRING, "old & <b>", "new")))),
				List.of(new DeleteRow("DW_ALTERNATIVE_INGREDIENT_COST", List.of(
						new Cell("alternative_ingredient_id", ColumnType.UUID, ALT_ID),
						new Cell("country", ColumnType.STRING, "GR"),
						new Cell("cost", ColumnType.STRING, "MED")),
						List.of("alternative_ingredient_id", "country"))),
				List.of(new TableSnapshot("DW_RULE_WC",
						List.of(List.of(new Cell("id", ColumnType.UUID, RULE_ID), new Cell("version", ColumnType.BIGINT, "1"))))),
				5L);

		String xml = sut.render(plan, ID, AUTHOR);

		assertThatCode(() -> DocumentBuilderFactory.newInstance().newDocumentBuilder()
				.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))).doesNotThrowAnyException();
	}

	private static PublishPlan empty() {
		return new PublishPlan(List.of(), List.of(), List.of(), List.of(), 0L);
	}

	private static String forward(String xml) {
		return xml.substring(0, xml.indexOf("<rollback>"));
	}

	private static String rollback(String xml) {
		return xml.substring(xml.indexOf("<rollback>"));
	}
}
