package eu.dietwise.tools.publish;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import eu.dietwise.tools.publish.model.Row;
import eu.dietwise.tools.publish.schema.ColumnSpec;
import eu.dietwise.tools.publish.schema.ColumnType;
import eu.dietwise.tools.publish.schema.MirrorSpec;
import eu.dietwise.tools.publish.schema.TableSpec;
import eu.dietwise.tools.publish.schema.Tables;

/**
 * Reads the master and Working Copy tables of the registry into {@link Row}s. Every value is fetched as its canonical
 * text form (booleans as {@code "true"}/{@code "false"}, everything else via {@link ResultSet#getString}), which is
 * how the rest of the tool compares and renders values.
 */
public final class DatabaseReader {

	public Map<String, List<Row>> readMaster(Connection connection) throws SQLException {
		Map<String, List<Row>> byTable = new LinkedHashMap<>();
		for (MirrorSpec mirror : Tables.all()) {
			byTable.put(mirror.master().name(), read(connection, mirror.master()));
		}
		return byTable;
	}

	public Map<String, List<Row>> readWorkingCopy(Connection connection) throws SQLException {
		Map<String, List<Row>> byTable = new LinkedHashMap<>();
		for (MirrorSpec mirror : Tables.all()) {
			byTable.put(mirror.wc().name(), read(connection, mirror.wc()));
		}
		return byTable;
	}

	private List<Row> read(Connection connection, TableSpec table) throws SQLException {
		String sql = "SELECT " + String.join(", ", table.columnNames()) + " FROM " + table.name();
		List<Row> rows = new ArrayList<>();
		try (Statement statement = connection.createStatement();
		     ResultSet resultSet = statement.executeQuery(sql)) {
			while (resultSet.next()) {
				Row.Builder builder = Row.builder();
				for (ColumnSpec column : table.columns()) {
					builder.set(column.name(), readValue(resultSet, column));
				}
				rows.add(builder.build());
			}
		}
		return rows;
	}

	private String readValue(ResultSet resultSet, ColumnSpec column) throws SQLException {
		if (column.type() == ColumnType.BOOLEAN) {
			boolean value = resultSet.getBoolean(column.name());
			return resultSet.wasNull() ? null : Boolean.toString(value);
		}
		return resultSet.getString(column.name());
	}
}
