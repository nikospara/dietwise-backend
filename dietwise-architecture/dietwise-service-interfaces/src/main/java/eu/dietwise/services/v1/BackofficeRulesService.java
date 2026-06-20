package eu.dietwise.services.v1;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.dietwise.common.types.VersionedText;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.services.v1.types.NewRuleOptions;
import eu.dietwise.services.v1.types.StagedRule;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.RuleId;
import io.smallrye.mutiny.Uni;

/**
 * Backoffice operations on the Rule master data. Reserved for users with the ADMIN role.
 */
public interface BackofficeRulesService {
	/**
	 * List every Rule for the backoffice grid: published master overlaid by the Working Copy, with each Rule's
	 * change state and Working Copy version.
	 *
	 * @param user The user requesting the rules; must have the ADMIN role
	 * @return All Rules, master overlaid by any Staged Change
	 */
	Uni<List<StagedRule>> listRules(User user);

	/**
	 * The reference data an editor chooses from when creating a new Rule.
	 *
	 * @param user The editor; must have the ADMIN role
	 */
	Uni<NewRuleOptions> newRuleOptions(User user);

	/**
	 * Stage a brand-new Rule in the Working Copy from existing reference data, choosing its business key. The new Rule
	 * starts active with no rationale and shows as a new row until published.
	 *
	 * @param user                The editor; must have the ADMIN role
	 * @param recommendationId    The chosen Recommendation
	 * @param triggerIngredientId The chosen Trigger Ingredient
	 * @param roleOrTechniqueId   The chosen Role or Technique, or {@code null} for none
	 * @return The id of the newly staged Rule
	 * @throws eu.dietwise.common.dao.DuplicateBusinessKeyException If a Rule with the same business key already exists
	 *                                                              in published master or the Working Copy
	 */
	Uni<RuleId> createRule(User user, UUID recommendationId, UUID triggerIngredientId, UUID roleOrTechniqueId);

	/**
	 * Discard an unpublished new Rule from the Working Copy, removing it from the grid. Only a Rule that exists solely
	 * in the Working Copy can be discarded; a published Rule is deactivated instead.
	 *
	 * @param user        The editor; must have the ADMIN role
	 * @param ruleId      The Working-Copy-only Rule to discard
	 * @param baseVersion The Working Copy version the discard is based on
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If the Rule is published rather than a Working-Copy-only Rule
	 */
	Uni<Void> discardNewRule(User user, RuleId ruleId, long baseVersion);

	/**
	 * Stage a new rationale for a Rule in the Working Copy, leaving published master and recipe assessment untouched.
	 *
	 * @param user        The editor; must have the ADMIN role
	 * @param ruleId      The Rule whose rationale is being staged
	 * @param rationale   The proposed rationale; may be {@code null}
	 * @param baseVersion The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
	 * @return The Rule's new Working Copy version
	 */
	Uni<Long> stageRationale(User user, RuleId ruleId, String rationale, long baseVersion);

	/**
	 * Revert a Rule's staged rationale in the Working Copy, restoring the published master value. When the Rule has no
	 * other Staged Change left, its Working Copy row is removed entirely.
	 *
	 * @param user        The editor; must have the ADMIN role
	 * @param ruleId      The Rule whose staged rationale is being reverted
	 * @param baseVersion The Working Copy version the revert is based on
	 */
	Uni<Void> revertRationale(User user, RuleId ruleId, long baseVersion);

	/**
	 * Stage a Rule's active state in the Working Copy, leaving published master and recipe assessment untouched.
	 * Deactivating an applied Rule, or activating a deactivated one, is a Staged Change like any other; staging the
	 * value the Rule already has in master removes the override.
	 *
	 * @param user        The editor; must have the ADMIN role
	 * @param ruleId      The Rule whose active state is being staged
	 * @param active      The proposed active state
	 * @param baseVersion The Working Copy version the change is based on ({@code 0} when no Staged Change exists yet)
	 */
	Uni<Void> setActive(User user, RuleId ruleId, boolean active, long baseVersion);

	/**
	 * The effective rationale translation of one Rule for each non-English language (published master overlaid by any
	 * Staged Change) and the Working Copy version a subsequent edit must be based on, to pre-fill the translations
	 * dialog. The returned map has an entry for every translatable language; a language with no translation has a {@code
	 * null} text and version {@code 0}.
	 *
	 * @param user   The editor; must have the ADMIN role
	 * @param ruleId The Rule whose rationale translations are being edited
	 */
	Uni<Map<RecipeLanguage, VersionedText>> rationaleTranslationsForEdit(User user, RuleId ruleId);

	/**
	 * Stage a Rule's rationale translation for one language in the Working Copy, leaving published master and recipe
	 * assessment untouched. Staging the value already in master removes the override.
	 *
	 * @param user        The editor; must have the ADMIN role
	 * @param ruleId      The Rule whose translation is being staged
	 * @param lang        The language being translated; must not be English
	 * @param rationale   The proposed translated rationale; {@code null} clears the translation (falls back to English)
	 * @param baseVersion The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
	 * @throws IllegalArgumentException If {@code lang} is English, which is the master value rather than a translation
	 */
	Uni<Void> stageRationaleTranslation(User user, RuleId ruleId, RecipeLanguage lang, String rationale, long baseVersion);

	/**
	 * Revert a Rule's staged rationale translation for one language, restoring the published master translation and
	 * removing the Working Copy row.
	 *
	 * @param user        The editor; must have the ADMIN role
	 * @param ruleId      The Rule whose staged translation is being reverted
	 * @param lang        The language being reverted; must not be English
	 * @param baseVersion The Working Copy version the revert is based on
	 * @throws IllegalArgumentException If {@code lang} is English, which is the master value rather than a translation
	 */
	Uni<Void> revertRationaleTranslation(User user, RuleId ruleId, RecipeLanguage lang, long baseVersion);
}
