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
 * The Working Copy mirror of {@link TriggerIngredientTranslationEntity}: a proposed per-language Trigger Ingredient
 * name and explanation translation staged for publish. Sparse — a row exists only for a translation that differs from
 * published master. The Trigger Ingredient is stored as a raw id and resolved against published master union Working
 * Copy at read time, so this table carries no foreign keys.
 */
@Entity
@IdClass(TriggerIngredientTranslationWcEntityId.class)
@Table(name = "DW_TRIGGER_INGREDIENT_TRANSLATION_WC")
public class TriggerIngredientTranslationWcEntity {
	@Id
	@Column(name = "trigger_ingredient_id")
	private UUID triggerIngredientId;

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

	public UUID getTriggerIngredientId() {
		return triggerIngredientId;
	}

	public void setTriggerIngredientId(UUID triggerIngredientId) {
		this.triggerIngredientId = triggerIngredientId;
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
