package eu.dietwise.services.model.suggestions;

/**
 * The per-language translation completeness of one Suggestion Template, split by field: each of {@code restriction},
 * {@code equivalence} and {@code techniqueNotes} carries its own {@link TranslationLangs} (which languages have a
 * published master translation of that field, and which have a pending change in the Working Copy). Used to derive the
 * three independent per-field completeness chip-sets shown in the backoffice panel without exposing the translation
 * text.
 */
public record FieldTranslationLangs(
		TranslationLangs restriction,
		TranslationLangs equivalence,
		TranslationLangs techniqueNotes
) {
}
