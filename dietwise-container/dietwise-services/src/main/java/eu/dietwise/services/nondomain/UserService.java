package eu.dietwise.services.nondomain;

import eu.dietwise.common.v1.model.UserData;
import io.smallrye.mutiny.Uni;

public interface UserService {
	Uni<UserData> findOrCreateByIdmId(String idmId);
}
