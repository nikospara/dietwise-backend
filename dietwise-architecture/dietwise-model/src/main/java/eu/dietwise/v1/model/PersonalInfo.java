package eu.dietwise.v1.model;

import eu.dietwise.common.types.Nullable;
import eu.dietwise.v1.types.BiologicalGender;
import eu.dietwise.v1.types.Country;
import org.immutables.value.Value;

@Value.Immutable
public interface PersonalInfo {
	@Nullable
	BiologicalGender getGender();

	@Nullable
	Integer getYearOfBirth();

	@Nullable
	Country getCountry();
}
