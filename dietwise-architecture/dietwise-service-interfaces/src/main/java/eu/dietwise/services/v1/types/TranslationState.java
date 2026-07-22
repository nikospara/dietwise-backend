package eu.dietwise.services.v1.types;

/**
 * The completeness state of one translatable thing in one language, shown on the backoffice grid. Completeness is
 * measured over the effective value (published master overlaid by any Working Copy change) of every translatable field.
 *
 * <ul>
 *   <li>{@link #MISSING} — every field is empty; assessment falls back to English.</li>
 *   <li>{@link #PARTIAL} — some but not all fields are translated, unchanged.</li>
 *   <li>{@link #PRESENT} — every field is translated, unchanged.</li>
 *   <li>{@link #PARTIAL_STAGED} — some but not all fields are translated, with a pending change in the Working Copy.</li>
 *   <li>{@link #STAGED} — every field is translated, with a pending change in the Working Copy.</li>
 * </ul>
 */
public enum TranslationState {
	MISSING,
	PARTIAL,
	PRESENT,
	PARTIAL_STAGED,
	STAGED
}
