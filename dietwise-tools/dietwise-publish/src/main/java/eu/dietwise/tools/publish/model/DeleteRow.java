package eu.dietwise.tools.publish.model;

import java.util.List;

/**
 * Delete an existing master row. {@link #row} is the full row as it stood before publish, so the rollback can
 * re-insert it; {@link #primaryKey} names the columns forming the key used in the delete's where clause.
 */
public record DeleteRow(String table, List<Cell> row, List<String> primaryKey) implements Change {

	/** The cells forming the primary key, used to locate the row to delete. */
	public List<Cell> primaryKeyCells() {
		return row.stream().filter(cell -> primaryKey.contains(cell.column())).toList();
	}
}
