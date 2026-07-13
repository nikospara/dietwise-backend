package eu.dietwise.tools.publish.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One database row as a map from column name to its canonical text value (or {@code null} for a SQL NULL). Every value
 * is a string: UUIDs and text as-is, integers as their decimal text, booleans as {@code "true"}/{@code "false"}. This
 * matches how Liquibase represents values in a changeset and makes comparison a plain, null-safe string equality.
 */
public record Row(Map<String, String> values) {

	public Row(Map<String, String> values) {
		this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
	}

	public String get(String column) {
		return values.get(column);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private final Map<String, String> values = new LinkedHashMap<>();

		public Builder set(String column, String value) {
			values.put(column, value);
			return this;
		}

		public Row build() {
			return new Row(values);
		}
	}
}
