package eu.dietwise.tools.publish;

/**
 * Normalizes string values so the published data is clean regardless of how it was entered in the Working Copy: leading
 * and trailing whitespace is trimmed, every non-space whitespace character in the body becomes a plain space and each
 * run of whitespace collapses to a single space.
 */
public final class StringCleanup {

	private StringCleanup() {
	}

	/** Returns the cleaned value, or {@code null} unchanged. */
	public static String clean(String value) {
		if (value == null) {
			return null;
		}
		return value.replaceAll("\\s+", " ").trim();
	}
}
