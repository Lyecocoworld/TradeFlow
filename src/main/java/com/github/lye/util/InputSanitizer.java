package com.github.lye.util;

import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * OWASP-grade input sanitization utilities.
 * <p>
 * Follows OWASP recommendations:
 * <ul>
 *   <li>Allowlist validation (never denylist)</li>
 *   <li>Context-aware output encoding</li>
 *   <li>Length limits to prevent DoS</li>
 *   <li>Control character stripping</li>
 * </ul>
 *
 * @author lye
 * @since 0.2
 */
public final class InputSanitizer {

    private static final int MAX_SEARCH_LENGTH = 128;
    private static final int MAX_GENERAL_LENGTH = 1024;

    private static final Pattern ALLOWLIST_SEARCH = Pattern.compile("^[\\p{L}\\p{N}\\s._\\-']+$");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    private InputSanitizer() {}

    /**
     * Sanitizes a search query parameter using allowlist validation.
     * Only allows letters, digits, spaces, dots, underscores, hyphens and apostrophes.
     * Truncates to {@value #MAX_SEARCH_LENGTH} characters.
     *
     * @param input raw search query, may be null
     * @return sanitized query, or null if input is null
     */
    public static String sanitizeSearch(String input) {
        if (input == null) {
            return null;
        }
        String cleaned = stripControlChars(input);
        if (cleaned.length() > MAX_SEARCH_LENGTH) {
            cleaned = cleaned.substring(0, MAX_SEARCH_LENGTH);
        }
        cleaned = cleaned.trim();
        if (cleaned.isEmpty()) {
            return "";
        }
        if (!ALLOWLIST_SEARCH.matcher(cleaned).matches()) {
            StringBuilder sb = new StringBuilder(cleaned.length());
            for (int i = 0; i < cleaned.length(); i++) {
                char c = cleaned.charAt(i);
                if (Character.isLetterOrDigit(c) || c == ' ' || c == '.' || c == '_' || c == '-' || c == '\'') {
                    sb.append(c);
                }
            }
            cleaned = sb.toString();
        }
        return cleaned;
    }

    /**
     * Encodes a string for safe inclusion in HTML content using OWASP entity encoding.
     * Encodes {@code & < > " ' /} and characters above 0x7F.
     *
     * @param input raw string, may be null
     * @return HTML-encoded string, or null if input is null
     */
    public static String encodeForHtml(String input) {
        if (input == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(input.length() * 2);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '&'  -> sb.append("&amp;");
                case '<'  -> sb.append("&lt;");
                case '>'  -> sb.append("&gt;");
                case '"'  -> sb.append("&quot;");
                case '\'' -> sb.append("&#x27;");
                case '/'  -> sb.append("&#x2F;");
                default -> {
                    if (c > 0x7F) {
                        sb.append("&#x").append(HexFormat.of().toHexDigits(c)).append(";");
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * Encodes a string for safe inclusion in a JavaScript/JSON string literal context.
     * Handles the OWASP ESAPI recommended escapes for JSON.
     *
     * @param input raw string, may be null
     * @return JavaScript-encoded string, or null if input is null
     */
    public static String encodeForJavaScript(String input) {
        if (input == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(input.length() * 2);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '\\'  -> sb.append("\\\\");
                case '"'   -> sb.append("\\\"");
                case '\''  -> sb.append("\\\'");
                case '\n'  -> sb.append("\\n");
                case '\r'  -> sb.append("\\r");
                case '\t'  -> sb.append("\\t");
                case '/'   -> sb.append("\\/");
                default -> {
                    if (c < 0x20) {
                        sb.append("\\u").append(String.format("%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * Strips ASCII control characters (except whitespace tabs/newlines/carriage returns).
     *
     * @param input raw string, may be null
     * @return cleaned string, or null if input is null
     */
    public static String stripControlChars(String input) {
        if (input == null) {
            return null;
        }
        return CONTROL_CHARS.matcher(input).replaceAll("");
    }

    /**
     * Truncates a string to a maximum length.
     *
     * @param input     raw string, may be null
     * @param maxLength maximum allowed length
     * @return truncated string, or null if input is null
     */
    public static String truncate(String input, int maxLength) {
        if (input == null) {
            return null;
        }
        if (input.length() <= maxLength) {
            return input;
        }
        return input.substring(0, maxLength);
    }
}
