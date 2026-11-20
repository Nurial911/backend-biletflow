package kz.edu.biletflow.backend.services.impl;

import jakarta.annotation.Nullable;

import java.util.Locale;

// The free-text "q" of admin searches: matched as a case-insensitive substring, and as an exact id when numeric.
// % and _ typed by the admin are matched literally (escaped with \), not as LIKE wildcards.
final class SearchText {
    static final char ESCAPE = '\\';

    private SearchText() {
    }

    // null when there is nothing to search for
    @Nullable
    static String normalize(@Nullable String q) {
        return q == null || q.isBlank() ? null : q.trim();
    }

    // LIKE pattern to compare with lower(column)
    static String containsPattern(String text) {
        String escaped = text.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    // the text as an id, or null when it isn't one
    @Nullable
    static Long asId(String text) {
        return text.matches("\\d{1,18}") ? Long.parseLong(text) : null;
    }
}
