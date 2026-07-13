package eu.dietwise.tools.publish.model;

import java.util.List;

/**
 * The rows of one Working Copy table captured before it is cleared. On publish the whole table is deleted; on rollback
 * every captured row is re-inserted to restore the Working Copy.
 */
public record TableSnapshot(String table, List<List<Cell>> rows) {
}
