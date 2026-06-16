package eu.dietwise.common.types;

/**
 * A text value together with the Working Copy version a subsequent edit must be based on ({@code 0} when no Staged
 * Change exists yet). The text is effective: published master overlaid by any Staged Change, and may be {@code null}
 * when no value is present.
 */
public record VersionedText(String text, long version) {
}
