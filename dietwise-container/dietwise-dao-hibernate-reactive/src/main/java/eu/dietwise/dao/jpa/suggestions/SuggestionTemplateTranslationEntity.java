package eu.dietwise.dao.jpa.suggestions;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import eu.dietwise.v1.types.RecipeLanguage;

@Entity
@IdClass(SuggestionTemplateTranslationEntityId.class)
@Table(name = "DW_SUGGESTION_TEMPLATE_TRANSLATION")
public class SuggestionTemplateTranslationEntity {
	@Id
	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "suggestion_template_id")
	private SuggestionTemplateEntity suggestionTemplate;

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

	public SuggestionTemplateEntity getSuggestionTemplate() {
		return suggestionTemplate;
	}

	public void setSuggestionTemplate(SuggestionTemplateEntity suggestionTemplate) {
		this.suggestionTemplate = suggestionTemplate;
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
}
