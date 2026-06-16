package eu.dietwise.dao.suggestions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.types.ReferenceDetails;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.services.model.suggestions.RoleOrTechnique;
import eu.dietwise.v1.types.RecipeLanguage;
import io.smallrye.mutiny.Uni;

public interface RoleOrTechniqueDao {
	Uni<List<RoleOrTechnique>> findAll(ReactivePersistenceContext em, RecipeLanguage lang);

	/**
	 * The selectable Roles or Techniques for the backoffice: published master overlaid by the Working Copy (mirror
	 * wins), including Working-Copy-only entries, as id + English name, ordered by name.
	 */
	Uni<List<ReferenceOption>> listOptions(ReactivePersistenceContext em);

	/**
	 * Stage a brand-new Role or Technique in the Working Copy with the given name and no explanation. Uniqueness of the
	 * name across master and the Working Copy is the caller's responsibility; the Working Copy carries a unique-name
	 * constraint as a backstop.
	 *
	 * @return The generated Working Copy id of the new Role or Technique
	 */
	Uni<UUID> createRoleOrTechnique(ReactivePersistenceTxContext tx, String name);

	/**
	 * The Roles or Techniques that carry a Staged Change in the Working Copy, as id to English name. Sparse: only
	 * entities with a Working Copy row appear (an edited existing entity or one created in the Working Copy). Used to
	 * overlay the effective name onto the grid and to flag the affected cells.
	 */
	Uni<Map<UUID, String>> findStagedNames(ReactivePersistenceContext em);

	/**
	 * The effective editable details of one Role or Technique: published master overlaid by any Staged Change, with the
	 * Working Copy version a subsequent edit must be based on ({@code 0} when no Staged Change exists yet).
	 *
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If no such Role or Technique exists
	 */
	Uni<ReferenceDetails> findEditableById(ReactivePersistenceContext em, UUID id);

	/**
	 * Stage an edit to a Role or Technique's name and explanation in the Working Copy, leaving published master
	 * untouched. The edit is shared: every Rule referencing this entity sees it. Staging the values the entity already
	 * has in master removes the override; if no field still differs, the Working Copy row collapses. Name uniqueness
	 * across master and the Working Copy is the caller's responsibility; the Working Copy carries a unique-name backstop.
	 *
	 * @param baseVersion The Working Copy version the caller based the edit on ({@code 0} when no Staged Change exists yet)
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If no such Role or Technique exists
	 */
	Uni<Void> editRoleOrTechnique(ReactivePersistenceTxContext tx, UUID id, String name, String explanationForLlm, long baseVersion);
}
