package eu.dietwise.common.types;

import java.util.UUID;

/**
 * A selectable entry identified by an id and shown by its English name, e.g. an option in a dropdown of reference
 * data. Its id serializes to its string form on the wire.
 */
public record ReferenceOption(UUID id, String name) {
}
