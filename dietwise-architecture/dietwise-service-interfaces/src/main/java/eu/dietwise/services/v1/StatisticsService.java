package eu.dietwise.services.v1;

import eu.dietwise.common.v1.model.User;
import io.smallrye.mutiny.Uni;

public interface StatisticsService {
	/**
	 * Called every time the application sees an activity by the user to update usage statistics.
	 *
	 * @param user The user
	 * @return The same user, for convenience
	 */
	Uni<User> markUserActivity(User user);

	Uni<User> assessedRecipe(User user);
}
