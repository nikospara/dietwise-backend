package eu.dietwise.services.v1.types;

import java.util.UUID;

/**
 * The outcome of adding a Suggestion Template to a Rule for a chosen AlternativeIngredient: the id of the template that
 * now covers that alternative, and whether it was newly created. {@code created} is {@code false} when the Rule already
 * had a template for the alternative (active or deactivated) — the existing one is surfaced instead of a duplicate, so
 * the caller can offer to reactivate it.
 */
public record AddedTemplate(UUID templateId, boolean created) {
}
