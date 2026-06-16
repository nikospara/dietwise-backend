package eu.dietwise.services.v1;

import java.util.List;

import eu.dietwise.common.v1.model.User;
import eu.dietwise.v1.model.Rule;
import io.smallrye.mutiny.Uni;

/**
 * Backoffice operations on the Rule master data. Reserved for users with the ADMIN role.
 */
public interface BackofficeRulesService {
	/**
	 * List every Rule for the backoffice grid, with its names and rationale in English.
	 *
	 * @param user The user requesting the rules; must have the ADMIN role
	 * @return All Rules
	 */
	Uni<List<Rule>> listRules(User user);
}
