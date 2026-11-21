package kz.edu.biletflow.backend.entities;

import jakarta.annotation.Nullable;

import java.util.Locale;

// Language of everything the backend writes for a user: emails, notifications, PDF tickets, calendar files and
// scan results (SRS section 7). Russian first, Kazakh, and English as the additional locale.
public enum Language {
    RU, KK, EN;

    public static final Language DEFAULT = RU;

    public Locale locale() {
        return Locale.of(name().toLowerCase(Locale.ROOT));
    }

    // The supported language of a locale ("kk-KZ" -> KK), or the default for any other.
    public static Language of(@Nullable Locale locale) {
        if (locale != null) {
            for (Language language : values()) {
                if (language.locale().getLanguage().equals(locale.getLanguage())) {
                    return language;
                }
            }
        }
        return DEFAULT;
    }
}
