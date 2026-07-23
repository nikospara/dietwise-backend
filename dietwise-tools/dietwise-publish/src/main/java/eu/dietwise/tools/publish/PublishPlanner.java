package eu.dietwise.tools.publish;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import eu.dietwise.tools.publish.model.Cell;
import eu.dietwise.tools.publish.model.ColumnChange;
import eu.dietwise.tools.publish.model.DeleteRow;
import eu.dietwise.tools.publish.model.InsertRow;
import eu.dietwise.tools.publish.model.PublishPlan;
import eu.dietwise.tools.publish.model.Row;
import eu.dietwise.tools.publish.model.TableSnapshot;
import eu.dietwise.tools.publish.model.UpdateRow;
import eu.dietwise.tools.publish.schema.ColumnSpec;
import eu.dietwise.tools.publish.schema.ColumnType;
import eu.dietwise.tools.publish.schema.MirrorSpec;
import eu.dietwise.tools.publish.schema.ParentRef;
import eu.dietwise.tools.publish.schema.TableSpec;
import eu.dietwise.tools.publish.schema.Tables;

/**
 * Computes a {@link PublishPlan} by diffing each Working Copy table against its master table, following the publish
 * semantics of each {@link MirrorSpec}. The planner is pure: it works on in-memory rows and holds no database state.
 */
public final class PublishPlanner {

	private static final String VERSION = "version";

	public PublishPlan plan(Map<String, List<Row>> masterRowsByTable, Map<String, List<Row>> workingCopyRowsByTable) {
		Map<String, List<Row>> master = cleanStrings(masterRowsByTable, MirrorSpec::master);
		Map<String, List<Row>> workingCopyRows = cleanStrings(workingCopyRowsByTable, MirrorSpec::wc);

		List<InsertRow> inserts = new ArrayList<>();
		List<UpdateRow> updates = new ArrayList<>();
		List<DeleteRow> deletes = new ArrayList<>();
		List<TableSnapshot> workingCopy = new ArrayList<>();
		long fingerprint = 0L;

		// The primary keys that will exist in each master table after the publish, built as we walk the mirrors in
		// foreign-key rank order (parents before children), so a child's parents are already resolved when we reach it.
		Map<String, Set<String>> presentParentKeys = new HashMap<>();

		for (MirrorSpec mirror : Tables.all()) {
			List<Row> wcRows = workingCopyRows.getOrDefault(mirror.wc().name(), List.of());
			List<Row> masterRows = master.getOrDefault(mirror.master().name(), List.of());
			Map<List<String>, Row> masterByKey = indexByPrimaryKey(mirror.master(), masterRows);

			// Drop and forget rows whose parent will not exist in master: they are neither published nor restored.
			List<Row> publishable = new ArrayList<>();
			for (Row wcRow : wcRows) {
				if (parentsPresent(mirror, wcRow, presentParentKeys)) {
					publishable.add(wcRow);
				}
			}

			for (Row wcRow : publishable) {
				switch (mirror.kind()) {
					case SNAPSHOT -> planSnapshot(mirror, wcRow, masterByKey, inserts, updates);
					case LINK_DELTA -> planLinkDelta(mirror, wcRow, masterByKey, inserts, deletes);
					case NULLABLE_PAYLOAD -> planNullablePayload(mirror, wcRow, masterByKey, inserts, updates, deletes);
				}
			}

			recordPresentKeys(mirror, masterRows, publishable, presentParentKeys);

			if (!wcRows.isEmpty()) {
				workingCopy.add(snapshotOf(mirror.wc(), publishable));
				fingerprint += fingerprintOf(mirror, wcRows);
			}
		}

		Map<String, Integer> orderByTable = orderByMasterTable();
		inserts.sort(Comparator.comparingInt(insert -> orderByTable.get(insert.table())));
		deletes.sort(Comparator.comparingInt((DeleteRow delete) -> orderByTable.get(delete.table())).reversed());

		return new PublishPlan(List.copyOf(inserts), List.copyOf(updates), List.copyOf(deletes),
				List.copyOf(workingCopy), fingerprint);
	}

	/**
	 * Returns a copy of the rows with every {@code STRING} column {@link StringCleanup#clean cleaned}. Cleaning the
	 * inputs before diffing means both the published values and the values restored on rollback are clean, and a
	 * difference that is only whitespace does not register as a change.
	 */
	private Map<String, List<Row>> cleanStrings(Map<String, List<Row>> rowsByTable, Function<MirrorSpec, TableSpec> side) {
		Map<String, List<Row>> cleaned = new LinkedHashMap<>();
		for (MirrorSpec mirror : Tables.all()) {
			TableSpec spec = side.apply(mirror);
			List<Row> rows = new ArrayList<>();
			for (Row row : rowsByTable.getOrDefault(spec.name(), List.of())) {
				rows.add(cleanRow(spec, row));
			}
			cleaned.put(spec.name(), rows);
		}
		return cleaned;
	}

	private Row cleanRow(TableSpec spec, Row row) {
		Row.Builder builder = Row.builder();
		for (ColumnSpec column : spec.columns()) {
			String value = row.get(column.name());
			builder.set(column.name(), column.type() == ColumnType.STRING ? StringCleanup.clean(value) : value);
		}
		return builder.build();
	}

	/** Whether every foreign-key parent of this Working Copy row will exist in master after the publish. */
	private boolean parentsPresent(MirrorSpec mirror, Row wcRow, Map<String, Set<String>> presentParentKeys) {
		for (ParentRef ref : mirror.parentRefs()) {
			if (!presentParentKeys.getOrDefault(ref.parentMaster(), Set.of()).contains(wcRow.get(ref.column()))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Records the primary keys that will exist in this master table after the publish (its current rows plus the rows
	 * this publish adds), so later mirrors can resolve foreign keys against it. Only single-column primary keys are
	 * recorded, which is all a {@link ParentRef} can point at.
	 */
	private void recordPresentKeys(MirrorSpec mirror, List<Row> masterRows, List<Row> publishable,
	                               Map<String, Set<String>> presentParentKeys) {
		if (mirror.master().primaryKey().size() != 1) {
			return;
		}
		Set<String> keys = new HashSet<>();
		for (Row row : masterRows) {
			keys.add(singleKey(mirror.master(), row));
		}
		for (Row row : publishable) {
			keys.add(singleKey(mirror.wc(), row));
		}
		presentParentKeys.put(mirror.master().name(), keys);
	}

	private String singleKey(TableSpec spec, Row row) {
		return row.get(spec.primaryKey().get(0));
	}

	private void planSnapshot(MirrorSpec mirror, Row wcRow, Map<List<String>, Row> masterByKey,
	                          List<InsertRow> inserts, List<UpdateRow> updates) {
		Row masterRow = masterByKey.get(keyOf(mirror.master(), wcRow));
		if (masterRow == null) {
			inserts.add(insertFrom(mirror, wcRow));
		} else {
			addUpdateIfChanged(mirror, wcRow, masterRow, updates);
		}
	}

	private void planLinkDelta(MirrorSpec mirror, Row wcRow, Map<List<String>, Row> masterByKey,
	                           List<InsertRow> inserts, List<DeleteRow> deletes) {
		boolean present = Boolean.parseBoolean(wcRow.get("present"));
		Row masterRow = masterByKey.get(keyOf(mirror.master(), wcRow));
		if (present && masterRow == null) {
			inserts.add(insertFrom(mirror, wcRow));
		} else if (!present && masterRow != null) {
			deletes.add(deleteFrom(mirror, masterRow));
		}
	}

	private void planNullablePayload(MirrorSpec mirror, Row wcRow, Map<List<String>, Row> masterByKey,
	                                 List<InsertRow> inserts, List<UpdateRow> updates, List<DeleteRow> deletes) {
		Row masterRow = masterByKey.get(keyOf(mirror.master(), wcRow));
		boolean stagedEmpty = mirror.payloadColumns().stream().allMatch(column -> wcRow.get(column) == null);
		if (stagedEmpty) {
			if (masterRow != null) {
				deletes.add(deleteFrom(mirror, masterRow));
			}
		} else if (masterRow == null) {
			inserts.add(insertFrom(mirror, wcRow));
		} else {
			addUpdateIfChanged(mirror, wcRow, masterRow, updates);
		}
	}

	private void addUpdateIfChanged(MirrorSpec mirror, Row wcRow, Row masterRow, List<UpdateRow> updates) {
		List<ColumnChange> changes = new ArrayList<>();
		for (String column : mirror.payloadColumns()) {
			String before = masterRow.get(column);
			String after = wcRow.get(column);
			if (!Objects.equals(before, after)) {
				changes.add(new ColumnChange(column, mirror.master().typeOf(column), before, after));
			}
		}
		if (!changes.isEmpty()) {
			updates.add(new UpdateRow(mirror.master().name(), primaryKeyCells(mirror.master(), wcRow), changes));
		}
	}

	/**
	 * The row to insert: every master column that also exists in the Working Copy, valued from the Working Copy row.
	 */
	private InsertRow insertFrom(MirrorSpec mirror, Row wcRow) {
		List<Cell> cells = new ArrayList<>();
		for (ColumnSpec column : mirror.master().columns()) {
			if (mirror.wc().hasColumn(column.name())) {
				cells.add(new Cell(column.name(), column.type(), wcRow.get(column.name())));
			}
		}
		return new InsertRow(mirror.master().name(), cells);
	}

	/**
	 * The row to delete: the full master row (for rollback re-insert) and the primary-key column names.
	 */
	private DeleteRow deleteFrom(MirrorSpec mirror, Row masterRow) {
		List<Cell> cells = new ArrayList<>();
		for (ColumnSpec column : mirror.master().columns()) {
			cells.add(new Cell(column.name(), column.type(), masterRow.get(column.name())));
		}
		return new DeleteRow(mirror.master().name(), cells, mirror.master().primaryKey());
	}

	private List<Cell> primaryKeyCells(TableSpec spec, Row row) {
		List<Cell> cells = new ArrayList<>();
		for (String column : spec.primaryKey()) {
			cells.add(new Cell(column, spec.typeOf(column), row.get(column)));
		}
		return cells;
	}

	private TableSnapshot snapshotOf(TableSpec wc, List<Row> wcRows) {
		List<List<Cell>> rows = new ArrayList<>();
		for (Row row : wcRows) {
			List<Cell> cells = new ArrayList<>();
			for (ColumnSpec column : wc.columns()) {
				cells.add(new Cell(column.name(), column.type(), row.get(column.name())));
			}
			rows.add(cells);
		}
		return new TableSnapshot(wc.name(), rows);
	}

	private long fingerprintOf(MirrorSpec mirror, List<Row> wcRows) {
		long sum = wcRows.size();
		if (mirror.isVersioned()) {
			for (Row row : wcRows) {
				sum += Long.parseLong(row.get(VERSION));
			}
		}
		return sum;
	}

	private Map<List<String>, Row> indexByPrimaryKey(TableSpec spec, List<Row> rows) {
		Map<List<String>, Row> index = new HashMap<>();
		for (Row row : rows) {
			index.put(keyOf(spec, row), row);
		}
		return index;
	}

	private List<String> keyOf(TableSpec spec, Row row) {
		List<String> key = new ArrayList<>();
		for (String column : spec.primaryKey()) {
			key.add(row.get(column));
		}
		return key;
	}

	private Map<String, Integer> orderByMasterTable() {
		Map<String, Integer> orders = new HashMap<>();
		for (MirrorSpec mirror : Tables.all()) {
			orders.put(mirror.master().name(), mirror.order());
		}
		return orders;
	}
}
