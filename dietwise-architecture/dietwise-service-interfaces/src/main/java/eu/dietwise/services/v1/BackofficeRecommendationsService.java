package eu.dietwise.services.v1;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.dietwise.common.types.RecommendationTranslationDetails;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.services.v1.types.StagedRecommendation;
import eu.dietwise.v1.types.RecipeLanguage;
import io.smallrye.mutiny.Uni;

/**
 * Backoffice operations on the Recommendation master data. A Recommendation cannot be created, deleted, deactivated or
 * renamed; only its English explanation for the LLM and the translations of its fields are editable. Reserved for users
 * with the ADMIN role.
 */
public interface BackofficeRecommendationsService {
	/**
	 * List every Recommendation for the backoffice grid: its English name and component for scoring, its weight, its
	 * effective English explanation for the LLM, and the completeness of its translations, ordered by name.
	 *
	 * @param user The user requesting the recommendations; must have the ADMIN role
	 * @return All Recommendations, ordered by name
	 */
	Uni<List<StagedRecommendation>> listRecommendations(User user);

	/**
	 * Stage an edit to a Recommendation's English explanation for the LLM in the Working Copy, leaving published master
	 * and recipe assessment untouched. Staging the value the Recommendation already has in master collapses the Staged
	 * Change. The explanation may be {@code null} or empty.
	 *
	 * @param user           The editor; must have the ADMIN role
	 * @param id             The Recommendation whose explanation is being staged
	 * @param explanation    The proposed explanation; may be {@code null}
	 * @param baseVersion    The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
	 * @return The Recommendation's new Working Copy version ({@code 0} when the edit collapsed back to master)
	 */
	Uni<Long> stageExplanation(User user, UUID id, String explanation, long baseVersion);

	/**
	 * Revert a Recommendation's staged explanation, restoring its published master value and removing the Working Copy
	 * row. A no-op when no Staged Change exists.
	 *
	 * @param user        The editor; must have the ADMIN role
	 * @param id          The Recommendation whose staged explanation is being reverted
	 * @param baseVersion The Working Copy version the revert is based on
	 */
	Uni<Void> revertExplanation(User user, UUID id, long baseVersion);

	/**
	 * The effective translation of one Recommendation for each non-English language (published master overlaid by any
	 * Staged Change) and the Working Copy version a subsequent edit must be based on, to pre-fill the translations dialog.
	 *
	 * @param user The editor; must have the ADMIN role
	 * @param id   The Recommendation whose translations are being edited
	 */
	Uni<Map<RecipeLanguage, RecommendationTranslationDetails>> translationsForEdit(User user, UUID id);

	/**
	 * Stage a Recommendation's name, component for scoring and explanation translation for one language in the Working
	 * Copy, leaving published master and recipe assessment untouched. The three fields share one version and are staged
	 * together. Staging the values already in master removes the override.
	 *
	 * @param user                The editor; must have the ADMIN role
	 * @param id                  The Recommendation being translated
	 * @param lang                The language being translated; must not be English
	 * @param name                The proposed translated name; {@code null} clears it (falls back to English)
	 * @param componentForScoring The proposed translated component for scoring; {@code null} clears it
	 * @param explanationForLlm   The proposed translated explanation; {@code null} clears it
	 * @param baseVersion         The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
	 * @throws IllegalArgumentException If {@code lang} is English, which is the master value rather than a translation
	 */
	Uni<Void> stageTranslation(User user, UUID id, RecipeLanguage lang, String name, String componentForScoring, String explanationForLlm, long baseVersion);

	/**
	 * Revert a Recommendation's staged translation for one language, restoring the published master translation and
	 * removing the Working Copy row.
	 *
	 * @param user        The editor; must have the ADMIN role
	 * @param id          The Recommendation whose staged translation is being reverted
	 * @param lang        The language being reverted; must not be English
	 * @param baseVersion The Working Copy version the revert is based on
	 * @throws IllegalArgumentException If {@code lang} is English, which is the master value rather than a translation
	 */
	Uni<Void> revertTranslation(User user, UUID id, RecipeLanguage lang, long baseVersion);
}
