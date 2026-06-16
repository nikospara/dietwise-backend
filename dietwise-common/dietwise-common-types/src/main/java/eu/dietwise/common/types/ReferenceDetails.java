package eu.dietwise.common.types;

/**
 * The editable details of a reference entity (a Trigger Ingredient or a Role or Technique): its name, its explanation
 * for the LLM, and the Working Copy version a subsequent edit must be based on ({@code 0} when no Staged Change exists
 * yet). The values are effective: published master overlaid by any Staged Change.
 */
public record ReferenceDetails(String name, String explanationForLlm, long version) {
}
