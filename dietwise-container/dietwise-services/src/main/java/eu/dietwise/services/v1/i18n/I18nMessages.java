package eu.dietwise.services.v1.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.v1.types.RecipeLanguage;

@ApplicationScoped
public class I18nMessages {
	private static final String BUNDLE_NAME = "eu.dietwise.services.v1.i18n.messages";
	private static final ResourceBundle.Control NO_DEFAULT_LOCALE_FALLBACK =
			ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT);

	public String format(RecipeLanguage lang, String key, Object... arguments) {
		Locale locale = toLocale(lang);
		String pattern = findPattern(locale, key);
		return new MessageFormat(pattern, locale).format(arguments);
	}

	private String findPattern(Locale locale, String key) {
		ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale, NO_DEFAULT_LOCALE_FALLBACK);
		if (bundle.containsKey(key)) {
			return bundle.getString(key);
		}
		ResourceBundle englishBundle = ResourceBundle.getBundle(BUNDLE_NAME, toLocale(RecipeLanguage.EN), NO_DEFAULT_LOCALE_FALLBACK);
		if (englishBundle.containsKey(key)) {
			return englishBundle.getString(key);
		}
		throw new MissingResourceException("Missing i18n message key", BUNDLE_NAME, key);
	}

	private Locale toLocale(RecipeLanguage lang) {
		RecipeLanguage resolvedLang = lang == null ? RecipeLanguage.EN : lang;
		return Locale.forLanguageTag(resolvedLang.getCode());
	}
}
