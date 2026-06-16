package eu.dietwise.dao.suggestions;

import java.util.List;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
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
}
