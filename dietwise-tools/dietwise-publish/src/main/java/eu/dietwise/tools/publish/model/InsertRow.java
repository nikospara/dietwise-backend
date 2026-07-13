package eu.dietwise.tools.publish.model;

import java.util.List;

/**
 * Insert a new row into a master table. The rollback is a delete keyed by the row's primary key.
 */
public record InsertRow(String table, List<Cell> cells) implements Change {
}
