package eu.dietwise.services.v1;

import eu.dietwise.common.v1.model.User;
import eu.dietwise.v1.types.SuggestionTemplateId;
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

	/**
	 * Increase times, return new value, does not increment if times accepted plus times rejected is greater than times suggested.
	 */
	Uni<Integer> increaseTimesAccepted(User user, SuggestionTemplateId suggestionId);

	/**
	 * Decrease times, return new value, does not decrement below zero.
	 */
	Uni<Integer> decreaseTimesAccepted(User user, SuggestionTemplateId suggestionId);

	/**
	 * Increase times, return new value, does not increment if times accepted plus times rejected is greater than times suggested.
	 */
	Uni<Integer> increaseTimesRejected(User user, SuggestionTemplateId suggestionId);

	/**
	 * Decrease times, return new value, does not decrement below zero.
	 */
	Uni<Integer> decreaseTimesRejected(User user, SuggestionTemplateId suggestionId);
}
