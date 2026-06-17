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
 * The Working Copy mirror of {@link AlternativeIngredientTranslationEntity}: a proposed per-language Alternative
 * Ingredient name and explanation translation staged for publish. Sparse — a row exists only for a translation that
 * carries a Staged Change. Shared master data: a translation edit here is seen by every Suggestion Template that
 * references this entity.
 */
@Entity
@IdClass(AlternativeIngredientTranslationWcEntityId.class)
@Table(name = "DW_ALTERNATIVE_INGREDIENT_TRANSLATION_WC")
public class AlternativeIngredientTranslationWcEntity {
	@Id
	@Column(name = "alternative_ingredient_id")
	private UUID alternativeIngredientId;

	@Id
	@Enumerated(STRING)
	@Column(name = "lang")
	private RecipeLanguage lang;

	@Column(name = "name")
	private String name;

	@Column(name = "explanation_for_llm")
	private String explanationForLlm;

	@Column(name = "version")
	private long version;

	public UUID getAlternativeIngredientId() {
		return alternativeIngredientId;
	}

	public void setAlternativeIngredientId(UUID alternativeIngredientId) {
		this.alternativeIngredientId = alternativeIngredientId;
	}

	public RecipeLanguage getLang() {
		return lang;
	}

	public void setLang(RecipeLanguage lang) {
		this.lang = lang;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getExplanationForLlm() {
		return explanationForLlm;
	}

	public void setExplanationForLlm(String explanationForLlm) {
		this.explanationForLlm = explanationForLlm;
	}

	public long getVersion() {
		return version;
	}

	public void setVersion(long version) {
		this.version = version;
	}
}
