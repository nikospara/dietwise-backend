package eu.dietwise.tools.publish.schema;

import java.util.List;

/**
 * The columns and primary key of one table (either a master table or a Working Copy mirror). Column order is
 * significant: it is the order in which values are read and rendered.
 */
public record TableSpec(String name, List<ColumnSpec> columns, List<String> primaryKey) {

	public List<String> columnNames() {
		return columns.stream().map(ColumnSpec::name).toList();
	}

	public boolean hasColumn(String column) {
		return columns.stream().anyMatch(c -> c.name().equals(column));
	}

	public boolean isPrimaryKey(String column) {
		return primaryKey.contains(column);
	}

	public ColumnType typeOf(String column) {
		return columns.stream()
				.filter(c -> c.name().equals(column))
				.map(ColumnSpec::type)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No column " + column + " in table " + name));
	}
}
