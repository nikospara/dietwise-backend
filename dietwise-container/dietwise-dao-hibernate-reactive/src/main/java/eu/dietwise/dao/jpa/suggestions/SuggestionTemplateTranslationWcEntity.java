package eu.dietwise.dao.jpa.suggestions;

import static jakarta.persistence.EnumType.STRING;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import eu.dietwise.v1.types.RecipeLanguage;

/**
 * The Working Copy mirror of {@link SuggestionTemplateTranslationEntity}: a proposed per-language Suggestion Template
 * translation staged for publish. Sparse — a row exists only for a template+language whose translation differs from
 * published master. A row carries all three translatable fields as a whole-row snapshot of master, so an unedited field
 * holds the same value as master; the Suggestion Template is stored as a raw id and resolved against published master
 * union Working Copy at read time, so this table carries no foreign keys.
 */
@Entity
@IdClass(SuggestionTemplateTranslationWcEntityId.class)
@Table(name = "DW_SUGGESTION_TEMPLATE_TRANSLATION_WC")
public class SuggestionTemplateTranslationWcEntity {
	@Id
	@Column(name = "suggestion_template_id")
	private UUID suggestionTemplateId;

	@Id
	@Enumerated(STRING)
	@Column(name = "lang")
	private RecipeLanguage lang;

	@Column(name = "restriction")
	private String restriction;

	@Column(name = "equivalence")
	private String equivalence;

	@Column(name = "technique_notes")
	private String techniqueNotes;

	@Column(name = "version")
	private long version;

	public UUID getSuggestionTemplateId() {
		return suggestionTemplateId;
	}

	public void setSuggestionTemplateId(UUID suggestionTemplateId) {
		this.suggestionTemplateId = suggestionTemplateId;
	}

	public RecipeLanguage getLang() {
		return lang;
	}

	public void setLang(RecipeLanguage lang) {
		this.lang = lang;
	}

	public String getRestriction() {
		return restriction;
	}

	public void setRestriction(String restriction) {
		this.restriction = restriction;
	}

	public String getEquivalence() {
		return equivalence;
	}

	public void setEquivalence(String equivalence) {
		this.equivalence = equivalence;
	}

	public String getTechniqueNotes() {
		return techniqueNotes;
	}

	public void setTechniqueNotes(String techniqueNotes) {
		this.techniqueNotes = techniqueNotes;
	}

	public long getVersion() {
		return version;
	}

	public void setVersion(long version) {
		this.version = version;
	}
}
