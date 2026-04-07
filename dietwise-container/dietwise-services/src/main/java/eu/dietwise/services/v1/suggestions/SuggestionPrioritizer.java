package eu.dietwise.services.v1.suggestions;

import java.util.List;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.v1.model.PersonalInfo;
import eu.dietwise.v1.model.Suggestion;
import io.smallrye.mutiny.Uni;

public interface SuggestionPrioritizer {
	Uni<List<Suggestion>> prioritizeSuggestions(ReactivePersistenceContext em, PersonalInfo personalInfo, List<Suggestion> suggestions);
}
