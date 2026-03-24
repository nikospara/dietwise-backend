package eu.dietwise.services.v1.extraction.impl;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

final class RecipeSourceUrlValidator {
	private static final String CACHED_TEST_PAGE_PATTERN = "^[0-9]{3}\\.html$";

	private RecipeSourceUrlValidator() {
	}

	static void validateHttpPublicUrl(String url, boolean allowCachedTestPages) {
		if (url == null || url.isBlank()) {
			throw new InvalidRecipeSourceUrlException("URL must not be blank");
		}
		if (allowCachedTestPages && url.matches(CACHED_TEST_PAGE_PATTERN)) {
			return;
		}
		URI uri = makeUri(url);
		if (!uri.isAbsolute()) {
			throw new InvalidRecipeSourceUrlException("URL must be absolute");
		}
		String scheme = uri.getScheme();
		if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
			throw new InvalidRecipeSourceUrlException("Only HTTP(S) URLs are accepted");
		}
		if (uri.getRawUserInfo() != null) {
			throw new InvalidRecipeSourceUrlException("URL user-info is not accepted");
		}
		String host = uri.getHost();
		if (host == null || host.isBlank()) {
			throw new InvalidRecipeSourceUrlException("URL host is required");
		}
		String normalizedHost = host.toLowerCase();
		if (isBlockedHostName(normalizedHost)) {
			throw new InvalidRecipeSourceUrlException("URL host is not allowed");
		}
		if (isIpLiteral(normalizedHost) && isNonPublicAddress(normalizedHost)) {
			throw new InvalidRecipeSourceUrlException("URL host is not public");
		}
	}

	private static URI makeUri(String url) {
		try {
			return new URI(url);
		} catch (URISyntaxException e) {
			throw new InvalidRecipeSourceUrlException("Malformed URL");
		}
	}

	private static boolean isBlockedHostName(String host) {
		return "localhost".equals(host)
				|| host.endsWith(".localhost")
				|| host.endsWith(".local")
				|| host.endsWith(".internal")
				|| host.endsWith(".home.arpa");
	}

	private static boolean isIpLiteral(String host) {
		return host.contains(":") || host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+");
	}

	private static boolean isNonPublicAddress(String host) {
		try {
			InetAddress inetAddress = InetAddress.getByName(host);
			if (inetAddress.isAnyLocalAddress()
					|| inetAddress.isLoopbackAddress()
					|| inetAddress.isLinkLocalAddress()
					|| inetAddress.isSiteLocalAddress()
					|| inetAddress.isMulticastAddress()) {
				return true;
			}
			byte[] address = inetAddress.getAddress();
			return inetAddress instanceof Inet6Address
					? isUniqueLocalIpv6(address)
					: isReservedIpv4(address);
		} catch (UnknownHostException e) {
			throw new InvalidRecipeSourceUrlException("Malformed IP literal");
		}
	}

	private static boolean isUniqueLocalIpv6(byte[] address) {
		return address.length > 0 && (address[0] & 0xfe) == 0xfc;
	}

	private static boolean isReservedIpv4(byte[] address) {
		if (address.length != 4) {
			return true;
		}
		int first = Byte.toUnsignedInt(address[0]);
		int second = Byte.toUnsignedInt(address[1]);
		return first == 0
				|| first == 100 && second >= 64 && second <= 127
				|| first == 198 && (second == 18 || second == 19)
				|| first >= 240;
	}
}
