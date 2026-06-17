package eu.dietwise.common.types;

/**
 * The editable details of a reference entity (a Trigger Ingredient or a Role or Technique): its name, its explanation
 * for the LLM, and the Working Copy version a subsequent edit must be based on ({@code 0} when no Staged Change exists
 * yet). The values are effective: published master overlaid by any Staged Change. {@code published} is {@code true}
 * when a published master baseline exists behind these details (so a Staged Change can be reverted to it); it is
 * {@code false} for an entity that exists only in the Working Copy and has never been published.
 */
public record ReferenceDetails(String name, String explanationForLlm, long version, boolean published) {
}
