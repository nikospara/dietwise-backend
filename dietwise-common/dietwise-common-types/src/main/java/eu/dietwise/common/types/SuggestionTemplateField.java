package eu.dietwise.common.types;

/**
 * One of a Suggestion Template's editable English text fields. Shared by the DAO, which stages and reverts a single
 * field of a template's Working Copy row, and the service interface, which reports per the field whether it carries a
 * pending change.
 */
public enum SuggestionTemplateField {
	RESTRICTION,
	EQUIVALENCE,
	TECHNIQUE_NOTES
}
