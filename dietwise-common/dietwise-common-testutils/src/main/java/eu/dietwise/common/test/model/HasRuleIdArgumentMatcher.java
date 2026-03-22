package eu.dietwise.common.test.model;

import java.util.Objects;

import eu.dietwise.v1.types.HasRuleId;
import eu.dietwise.v1.types.RuleId;
import org.mockito.ArgumentMatcher;

public class HasRuleIdArgumentMatcher implements ArgumentMatcher<HasRuleId> {
	private final RuleId ruleId;

	public HasRuleIdArgumentMatcher(RuleId ruleId) {
		this.ruleId = Objects.requireNonNull(ruleId);
	}

	public static HasRuleIdArgumentMatcher hasRuleId(RuleId ruleId) {
		return new HasRuleIdArgumentMatcher(ruleId);
	}

	@Override
	public boolean matches(HasRuleId argument) {
		return argument != null && argument.getId() != null && argument.getId().equals(ruleId);
	}

	@Override
	public String toString() {
		return "has RuleId of representation " + ruleId.asString();
	}

	@Override
	public Class<?> type() {
		return HasRuleId.class;
	}
}
