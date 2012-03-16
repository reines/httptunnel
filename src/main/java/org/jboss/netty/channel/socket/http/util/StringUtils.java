package org.jboss.netty.channel.socket.http.util;

import java.util.Formatter;

public class StringUtils {

	private StringUtils() { }

	public static String capitalize(String str) {
		if (str.isEmpty())
			return str;

		return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
	}

	public static String leftPad(String str, int length, char padding) {
		final int paddingLength = length - str.length();
		if (paddingLength < 0)
			return str;

		final StringBuilder builder = new StringBuilder(length);

		while (builder.length() < paddingLength)
			builder.append(padding);

		builder.append(str);

		return builder.toString();
	}

	public static boolean inCharArray(char[] haystack, char needle) {
		for (char candidate : haystack) {
			if (candidate == needle)
				return true;
		}

		return false;
	}

	public static boolean inStringArray(String[] haystack, String needle) {
		for (String candidate : haystack) {
			if (candidate == null) {
				if (needle == null)
					return true;

				continue;
			}

			if (candidate.equals(needle))
				return true;
		}

		return false;
	}

	public static String bytesToHex(byte[] bytes) {
		final Formatter formatter = new Formatter();

		for (byte b : bytes)
			formatter.format("%02x", b);

		return formatter.toString();
	}
}
