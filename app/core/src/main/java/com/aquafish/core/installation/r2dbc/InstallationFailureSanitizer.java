package com.aquafish.core.installation.r2dbc;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 安装失败信息入库前的最小安全清洗器。
 */
final class InstallationFailureSanitizer {

    static final int MAX_CODE_LENGTH = 100;
    static final int MAX_MESSAGE_LENGTH = 500;

    private static final String DEFAULT_CODE =
        "INSTALLATION_FAILED";

    private static final String DEFAULT_MESSAGE =
        "安装失败，未提供安全错误摘要。";

    private static final Pattern URL_CREDENTIALS =
        Pattern.compile(
            "(?i)((?:r2dbc|jdbc):[^\\s]*//)[^\\s/@:]+:[^\\s/@]+@"
        );

    private static final Pattern SECRET_ASSIGNMENT =
        Pattern.compile(
            "(?i)\\b(password|passwd|pwd|token|secret|api[_-]?key)"
                + "\\s*[:=]\\s*([^\\s,;]+)"
        );

    private static final Pattern WHITESPACE =
        Pattern.compile("\\s+");

    private InstallationFailureSanitizer() {
    }

    static String sanitizeCode(
        String value
    ) {
        if (value == null || value.isBlank()) {
            return DEFAULT_CODE;
        }

        String normalized =
            value.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9._-]", "_");

        return truncate(
            normalized.isBlank()
                ? DEFAULT_CODE
                : normalized,
            MAX_CODE_LENGTH
        );
    }

    static String sanitizeMessage(
        String value
    ) {
        if (value == null || value.isBlank()) {
            return DEFAULT_MESSAGE;
        }

        String sanitized =
            URL_CREDENTIALS
                .matcher(value)
                .replaceAll("$1[REDACTED]@");

        sanitized =
            SECRET_ASSIGNMENT
                .matcher(sanitized)
                .replaceAll("$1=[REDACTED]");

        sanitized =
            WHITESPACE
                .matcher(sanitized)
                .replaceAll(" ")
                .trim();

        return truncate(
            sanitized.isBlank()
                ? DEFAULT_MESSAGE
                : sanitized,
            MAX_MESSAGE_LENGTH
        );
    }

    private static String truncate(
        String value,
        int maxLength
    ) {
        return value.length() <= maxLength
            ? value
            : value.substring(0, maxLength);
    }
}
