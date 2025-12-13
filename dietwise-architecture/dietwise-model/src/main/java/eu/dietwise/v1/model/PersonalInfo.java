package eu.dietwise.v1.model;

import eu.dietwise.v1.types.BiologicalGender;
import org.immutables.value.Value;

@Value.Immutable
public interface PersonalInfo {
	BiologicalGender getGender();
	Integer getYearOfBirth();
}
