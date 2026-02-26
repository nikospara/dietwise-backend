package eu.dietwise.v1.types;

public record SuggestionStats(
		int timesSuggested,
		int timesAccepted,
		int timesRejected
) {
	public static final SuggestionStats ALL_ZEROES = new SuggestionStats(0, 0, 0);
}
