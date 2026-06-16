package eu.dietwise.jaxrs.v1;

/**
 * Request to stage a brand-new shared reference entity (a Trigger Ingredient or a Role or Technique) in the Working
 * Copy.
 *
 * @param name The proposed name
 */
public record CreateReferenceRequest(String name) {
}
