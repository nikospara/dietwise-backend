package eu.dietwise.v1.types;

public enum RecipeLanguage {
	EN("en"),
	NL("nl"),
	EL("el"),
	LT("lt");

	private final String code;

	RecipeLanguage(String code) {
		this.code = code;
	}

	public String getCode() {
		return code;
	}

	public static RecipeLanguage fromCode(String code) {
		if (code == null || code.isBlank()) return EN;
		return switch (code.trim().toLowerCase()) {
			case "en" -> EN;
			case "nl" -> NL;
			case "el" -> EL;
			case "lt" -> LT;
			default -> throw new IllegalArgumentException("Unsupported language code: " + code);
		};
	}
}
