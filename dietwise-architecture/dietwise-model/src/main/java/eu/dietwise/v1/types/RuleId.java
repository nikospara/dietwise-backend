package eu.dietwise.v1.types;

import java.util.UUID;

import eu.dietwise.common.types.RepresentableAsString;

public interface RuleId extends HasRuleId, RepresentableAsString {
	@Override
	default RuleId getId() {
		return this;
	}

	UUID asUuid();
}
