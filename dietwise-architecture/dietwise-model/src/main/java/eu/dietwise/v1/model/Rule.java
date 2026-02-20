package eu.dietwise.v1.model;

import eu.dietwise.v1.types.HasRuleId;
import org.immutables.value.Value;

@Value.Immutable
public interface Rule extends HasRuleId, RuleData {

}
