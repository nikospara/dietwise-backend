package eu.dietwise.services.model.suggestions;

import java.util.Optional;

import eu.dietwise.services.types.suggestions.HasRoleOrTechniqueId;
import org.immutables.value.Value;

@Value.Immutable
public interface RoleOrTechnique extends HasRoleOrTechniqueId {
	String getName();

	Optional<String> getExplanationForLlm();
}
