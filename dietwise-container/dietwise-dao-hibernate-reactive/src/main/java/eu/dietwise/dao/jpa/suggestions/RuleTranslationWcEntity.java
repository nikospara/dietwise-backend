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
 * The Working Copy mirror of {@link RuleTranslationEntity}: a proposed per-language Rule rationale translation staged
 * for publish. Sparse — a row exists only for a translation that differs from published master. The Rule is stored as a
 * raw id and resolved against published master union Working Copy at read time, so this table carries no foreign keys.
 */
@Entity
@IdClass(RuleTranslationWcEntityId.class)
@Table(name = "DW_RULE_TRANSLATION_WC")
public class RuleTranslationWcEntity {
	@Id
	@Column(name = "rule_id")
	private UUID ruleId;

	@Id
	@Enumerated(STRING)
	@Column(name = "lang")
	private RecipeLanguage lang;

	@Column(name = "rationale")
	private String rationale;

	@Column(name = "version")
	private long version;

	public UUID getRuleId() {
		return ruleId;
	}

	public void setRuleId(UUID ruleId) {
		this.ruleId = ruleId;
	}

	public RecipeLanguage getLang() {
		return lang;
	}

	public void setLang(RecipeLanguage lang) {
		this.lang = lang;
	}

	public String getRationale() {
		return rationale;
	}

	public void setRationale(String rationale) {
		this.rationale = rationale;
	}

	public long getVersion() {
		return version;
	}

	public void setVersion(long version) {
		this.version = version;
	}
}
