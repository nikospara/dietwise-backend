package eu.dietwise.dao.jpa;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import eu.dietwise.v1.types.Country;

@Converter
public class CountryCode2Converter implements AttributeConverter<Country, String> {
	@Override
	public String convertToDatabaseColumn(Country attribute) {
		return attribute == null ? null : attribute.getCode2();
	}

	@Override
	public Country convertToEntityAttribute(String dbData) {
		return Country.fromCode2(dbData);
	}
}
