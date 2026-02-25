package eu.dietwise.v1.types;

public record SuggestionStats(
		int timesSuggested,
		int timesAccepted,
		int timesRejected
) {
}
