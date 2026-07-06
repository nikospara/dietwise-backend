package eu.dietwise.jaxrs.v1;

/**
 * Request to stage a Recommendation's English master text — its explanation for the LLM and human friendly display — in
 * the Working Copy. The two fields share one version and are staged together.
 *
 * @param explanationForLlm    The proposed explanation for the LLM; may be {@code null}
 * @param humanFriendlyDisplay The proposed human friendly display; may be {@code null}
 * @param baseVersion          The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
 */
public record StageMasterRequest(String explanationForLlm, String humanFriendlyDisplay, long baseVersion) {
}
