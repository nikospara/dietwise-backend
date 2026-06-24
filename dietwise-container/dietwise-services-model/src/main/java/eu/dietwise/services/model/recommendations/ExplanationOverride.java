package eu.dietwise.services.model.recommendations;

/**
 * A Recommendation's staged explanation for the LLM held in the Working Copy: the proposed value (may be {@code null})
 * and the Working Copy version a subsequent edit must be based on. A carrier between the DAO and the service layer,
 * keyed by Recommendation id; its presence means the explanation differs from published master.
 */
public record ExplanationOverride(String explanationForLlm, long version) {
}
