package eu.dietwise.v1.types;

import java.io.IOException;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class RecipeLanguageDeserializer extends JsonDeserializer<RecipeLanguage> {
	@Override
	public RecipeLanguage deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException, JacksonException {
		return RecipeLanguage.fromCode(jsonParser.getValueAsString());
	}
}
