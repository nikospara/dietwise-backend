package eu.dietwise.v1.types;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class RecipeLanguageSerializer extends JsonSerializer<RecipeLanguage> {
	@Override
	public void serialize(RecipeLanguage value, JsonGenerator jsonGenerator, SerializerProvider serializers) throws IOException {
		jsonGenerator.writeString(value.getCode());
	}
}
