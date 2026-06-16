package eu.dietwise.services.v1;

import java.util.List;

import eu.dietwise.common.v1.model.User;
import eu.dietwise.v1.types.RuleId;
import io.smallrye.mutiny.Uni;

/**
 * Backoffice operations on the Rule master data. Reserved for users with the ADMIN role.
 */
public interface BackofficeRulesService {
	/**
	 * List every Rule for the backoffice grid: published master overlaid by the Working Copy, with each Rule's
	 * change state and Working Copy version.
	 *
	 * @param user The user requesting the rules; must have the ADMIN role
	 * @return All Rules, master overlaid by any Staged Change
	 */
	Uni<List<StagedRule>> listRules(User user);

	/**
	 * Stage a new rationale for a Rule in the Working Copy, leaving published master and recipe assessment untouched.
	 *
	 * @param user        The editor; must have the ADMIN role
	 * @param ruleId      The Rule whose rationale is being staged
	 * @param rationale   The proposed rationale; may be {@code null}
	 * @param baseVersion The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
	 * @return The Rule's new Working Copy version
	 */
	Uni<Long> stageRationale(User user, RuleId ruleId, String rationale, long baseVersion);
}
