package eu.dietwise.common.utils;

public interface StringUtils {
	static String limit(String s, int limit) {
		return s == null ? null : s.substring(0, Math.min(s.length(), limit));
	}
}
