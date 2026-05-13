package eu.dietwise.common.v1.model;

import java.time.LocalDateTime;
import java.util.Optional;

import eu.dietwise.common.v1.types.HasUserId;
import org.immutables.value.Value;

@Value.Immutable
public interface UserData extends HasUserId {
	Optional<LocalDateTime> getDeletedAt();

	default boolean isDeleted() {
		return getDeletedAt().isPresent();
	}
}
