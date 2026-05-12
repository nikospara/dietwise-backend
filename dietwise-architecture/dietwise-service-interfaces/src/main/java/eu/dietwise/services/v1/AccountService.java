package eu.dietwise.services.v1;

import eu.dietwise.common.v1.model.User;
import io.smallrye.mutiny.Uni;

public interface AccountService {
	Uni<Void> deleteAccount(User user);
}
