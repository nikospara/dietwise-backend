package eu.dietwise.services.v1.types;

/**
 * The completeness state of one translatable thing in one language, shown on the backoffice grid.
 *
 * <ul>
 *   <li>{@link #MISSING} — no translation; assessment falls back to English.</li>
 *   <li>{@link #PRESENT} — a published master translation exists and is unchanged.</li>
 *   <li>{@link #STAGED} — the translation has a pending change in the Working Copy.</li>
 * </ul>
 */
public enum TranslationState {
	MISSING,
	PRESENT,
	STAGED
}
