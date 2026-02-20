package eu.dietwise.v1.model;

import java.util.Optional;

import eu.dietwise.v1.types.AlternativeIngredient;
import org.immutables.value.Value;

/**
 * Encapsulates a potential replacement suggestion for an ingredient, together with potential restrictions and
 * usage information.
 */
@Value.Immutable
public interface SuggestionTemplate {
	AlternativeIngredient getAlternative();

	Optional<String> getRestriction();

	Optional<String> getEquivalence();

	Optional<String> getTechniqueNotes();
}
