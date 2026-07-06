package eu.dietwise.services.model.recommendations;

/**
 * A Recommendation's staged English master text held in the Working Copy: the proposed explanation for the LLM and human
 * friendly display (either may be {@code null}) and the Working Copy version a subsequent edit must be based on. A carrier
 * between the DAO and the service layer, keyed by Recommendation id; its presence means at least one of the two fields
 * differs from published master. The two fields share the single version and are staged and reverted together.
 */
public record MasterOverride(String explanationForLlm, String humanFriendlyDisplay, long version) {
}
