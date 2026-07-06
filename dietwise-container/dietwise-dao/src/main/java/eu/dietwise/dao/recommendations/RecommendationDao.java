package eu.dietwise.dao.recommendations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.types.RecommendationTranslationDetails;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.services.model.recommendations.BackofficeRecommendation;
import eu.dietwise.services.model.recommendations.MasterOverride;
import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.services.model.suggestions.TranslationLangs;
import eu.dietwise.v1.types.BiologicalGender;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.Recommendation;
import io.smallrye.mutiny.Uni;

public interface RecommendationDao {
	Uni<Map<Recommendation, BigDecimal>> findRecommendations(ReactivePersistenceContext em, int age, BiologicalGender gender);

	/**
	 * Find recommendation values when the user has specified only their biological gender.
	 */
	Uni<Map<Recommendation, BigDecimal>> findRecommendations(ReactivePersistenceContext em, BiologicalGender gender);

	/**
	 * Find recommendation values when the user has specified only their age (date of birth actually).
	 */
	Uni<Map<Recommendation, BigDecimal>> findRecommendations(ReactivePersistenceContext em, int age);

	/**
	 * Find recommendation values when the user has specified no personal information.
	 */
	Uni<Map<Recommendation, BigDecimal>> findRecommendations(ReactivePersistenceContext em);

	Uni<List<RecommendationComponent>> listAllRecommendationsForScoring(ReactivePersistenceContext em, RecipeLanguage lang);

	/**
	 * Every Recommendation as an id + English name, ordered by name, for selection in the backoffice.
	 */
	Uni<List<ReferenceOption>> listOptions(ReactivePersistenceContext em);

	/**
	 * Every Recommendation's master fields for the backoffice grid (id, English name, component for scoring, weight,
	 * English explanation for the LLM, English human friendly display), ordered by name.
	 */
	Uni<List<BackofficeRecommendation>> listForBackoffice(ReactivePersistenceContext em);

	/**
	 * For each Recommendation that has at least one translation, which non-English languages it is translated into,
	 * split into published master translations ({@code present}) and pending Working Copy translations ({@code staged}).
	 * Used to derive the per-language completeness shown on the grid.
	 */
	Uni<Map<UUID, TranslationLangs>> findTranslationLangs(ReactivePersistenceContext em);

	/**
	 * For each Recommendation that has a staged edit to its English master text (explanation for the LLM or human friendly
	 * display), the proposed values and the Working Copy version a subsequent edit must be based on. A Recommendation with
	 * no entry has no Staged Change; its effective master text is the published master value. Used to overlay the grid's
	 * explanation and human friendly display columns.
	 */
	Uni<Map<UUID, MasterOverride>> findMasterOverrides(ReactivePersistenceContext em);

	/**
	 * Stage an edit to a Recommendation's English master text — its explanation for the LLM and human friendly display —
	 * in the Working Copy, leaving published master and recipe assessment untouched. The two fields share one version and
	 * are staged together. Staging the values the Recommendation already has in master removes the override and collapses
	 * the Working Copy row. Either field may be {@code null} or empty.
	 *
	 * @param baseVersion The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
	 * @return The Recommendation's new Working Copy version ({@code 0} when the edit collapsed the row back to master)
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If no such Recommendation exists
	 */
	Uni<Long> stageMaster(ReactivePersistenceTxContext tx, UUID id, String explanationForLlm, String humanFriendlyDisplay, long baseVersion);

	/**
	 * Revert a Recommendation's staged master text, restoring its published master values and removing the Working Copy
	 * row. A no-op when no Staged Change exists.
	 *
	 * @param baseVersion The Working Copy version the revert is based on
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 */
	Uni<Void> revertMaster(ReactivePersistenceTxContext tx, UUID id, long baseVersion);

	/**
	 * The effective translation of one Recommendation for each non-English language (published master overlaid by any
	 * Staged Change) and the Working Copy version a subsequent edit must be based on, to pre-fill the translations dialog.
	 * The returned map has an entry for every translatable language; a language with no translation has {@code null}
	 * fields and version {@code 0}.
	 */
	Uni<Map<RecipeLanguage, RecommendationTranslationDetails>> findTranslationsForEdit(ReactivePersistenceContext em, UUID id);

	/**
	 * Stage a Recommendation's name, component for scoring, explanation and human friendly display translation for one
	 * language in the Working Copy, leaving published master untouched. The four fields share one version and are staged
	 * together. Staging the values already in master removes the override; reverting always removes the Working Copy row.
	 *
	 * @param baseVersion The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 */
	Uni<Void> stageTranslation(ReactivePersistenceTxContext tx, UUID id, RecipeLanguage lang, String name, String componentForScoring, String explanationForLlm, String humanFriendlyDisplay, long baseVersion);

	/**
	 * Revert a Recommendation's staged translation for one language, restoring the published master translation and
	 * removing the Working Copy row. A no-op when no Staged Change exists.
	 *
	 * @param baseVersion The Working Copy version the revert is based on
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 */
	Uni<Void> revertTranslation(ReactivePersistenceTxContext tx, UUID id, RecipeLanguage lang, long baseVersion);
}
