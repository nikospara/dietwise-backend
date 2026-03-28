package eu.dietwise.dao.impl.suggestions;

import java.util.Objects;
import java.util.UUID;

import eu.dietwise.v1.types.RuleId;

public class UuidRuleId implements RuleId {
	private final UUID uuid;
	private String representation;

	public UuidRuleId(UUID uuid) {
		this.uuid = Objects.requireNonNull(uuid);
	}

	@Override
	public String asString() {
		if (representation == null) representation = uuid.toString();
		return representation;
	}

	@Override
	public UUID asUuid() {
		return uuid;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof UuidRuleId that) return Objects.equals(uuid, that.uuid);
		else if (o instanceof RuleId that) return Objects.equals(asString(), that.asString());
		else return false;
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(asString());
	}

	@Override
	public String toString() {
		return "UuidRuleId(" + asString() + ")";
	}
}
