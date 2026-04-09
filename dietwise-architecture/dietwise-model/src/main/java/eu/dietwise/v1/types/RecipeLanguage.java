package eu.dietwise.v1.types;

public enum RecipeLanguage {
	EN("en");

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
			default -> throw new IllegalArgumentException("Unsupported language code: " + code);
		};
	}
}
