package eu.dietwise.services.types.suggestions;

import java.util.UUID;

import eu.dietwise.common.types.RepresentableAsString;

public interface RoleOrTechniqueId extends HasRoleOrTechniqueId, RepresentableAsString {
	@Override
	default RoleOrTechniqueId getId() {
		return this;
	}

	UUID asUuid();
}
