package eu.dietwise.v1.types;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum Country {
	// Please keep them sorted alphabetically
	BELGIUM("BE", "BEL"),
	GREECE("GR", "GRC"),
	LITHUANIA("LT", "LTU");

	private static final Map<String, Country> CODE2_TO_COUNTRY = Stream.of(values()).collect(Collectors.toMap(Country::getCode2, Function.identity()));

	private final String code2;
	private final String code3;

	Country(String code2, String code3) {
		this.code2 = code2;
		this.code3 = code3;
	}

	public String getCode2() {
		return code2;
	}

	public String getCode3() {
		return code3;
	}

	public static Country fromCode2(String code2) {
		if (code2 == null) {
			return null;
		}
		var country = CODE2_TO_COUNTRY.get(code2);
		if (country != null) return country;
		throw new IllegalArgumentException(String.format("Unknown ISO 3166-1 alpha-2 country code: %s", code2));
	}
}
