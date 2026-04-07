package eu.dietwise.v1.types;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class CountryDeserializer extends JsonDeserializer<Country> {
	@Override
	public Country deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
		return Country.fromCode2(jsonParser.getValueAsString());
	}
}
