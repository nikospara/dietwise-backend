package eu.dietwise.v1.types;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;

@JsonDeserialize(as = GenericSuggestionTemplateId.class)
public abstract class SuggestionTemplateIdMixin {
}
