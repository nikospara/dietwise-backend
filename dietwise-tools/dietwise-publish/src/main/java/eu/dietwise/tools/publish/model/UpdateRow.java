package eu.dietwise.tools.publish.model;

import java.util.List;

/**
 * Update the changed columns of an existing master row, identified by its primary key. The rollback sets the same
 * columns back to their {@code before} values.
 */
public record UpdateRow(String table, List<Cell> primaryKey, List<ColumnChange> changes) implements Change {
}
