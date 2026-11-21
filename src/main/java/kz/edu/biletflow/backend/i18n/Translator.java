package kz.edu.biletflow.backend.i18n;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.entities.Language;
import kz.edu.biletflow.backend.entities.User;
import kz.edu.biletflow.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders the texts the backend delivers (SRS section 7) from i18n/messages*.properties.
 * Placeholders are {0}, {1}, ... and are filled in here rather than by MessageFormat, so apostrophes need no
 * escaping and numbers are never regrouped ("#1 234").
 * Whose language: a signed-in user's saved one ({@link #localeOf}); for an anonymous request, Accept-Language.
 * API error messages stay English on purpose: the clients show their own texts for those.
 */
@Component
@RequiredArgsConstructor
public class Translator {
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)}");

    private final MessageSource messageSource;
    private final UserRepository userRepository;

    public String get(Locale locale, String key, Object... args) {
        return render(Text.of(key, args), locale);
    }

    public String render(Text text, Locale locale) {
        StringBuilder out = new StringBuilder();
        for (Text.Part part : text.parts()) {
            if (part.key() == null) {
                out.append(part.literal());
                continue;
            }
            // No arguments here: the pattern comes back as written, placeholders and all.
            String pattern = messageSource.getMessage(part.key(), null, locale);
            Matcher matcher = PLACEHOLDER.matcher(pattern);
            StringBuilder filled = new StringBuilder();
            while (matcher.find()) {
                int index = Integer.parseInt(matcher.group(1));
                String value = index < part.args().size() ? format(part.args().get(index), locale) : matcher.group();
                matcher.appendReplacement(filled, Matcher.quoteReplacement(value));
            }
            matcher.appendTail(filled);
            out.append(filled);
        }
        return out.toString();
    }

    public static Locale localeOf(User user) {
        return user.getLanguage() != null ? user.getLanguage().locale() : Language.DEFAULT.locale();
    }

    // A signed-in user's saved language; for nobody (or a deleted account) the request's language.
    public Locale localeOf(@Nullable Long userId) {
        if (userId == null) {
            return requestLocale();
        }
        return userRepository.findById(userId).map(Translator::localeOf).orElseGet(Translator::requestLocale);
    }

    // From Accept-Language (see LocaleConfig). Russian outside a request (schedulers, the mail dispatcher), where
    // LocaleContextHolder would answer with the server's own locale.
    public static Locale requestLocale() {
        if (RequestContextHolder.getRequestAttributes() == null) {
            return Language.DEFAULT.locale();
        }
        return Language.of(LocaleContextHolder.getLocale()).locale();
    }

    // "Stalls, row A, seat 5", "Field (standing)", or null for general admission.
    @Nullable
    public static Text seatText(@Nullable String sectionName, @Nullable String rowLabel, @Nullable Integer seatNumber) {
        if (sectionName == null) {
            return null;
        }
        if (rowLabel == null || seatNumber == null) {
            return Text.raw(sectionName);
        }
        return Text.of("seat.label", sectionName, rowLabel, seatNumber);
    }

    @Nullable
    public String seatLabel(@Nullable String sectionName, @Nullable String rowLabel, @Nullable Integer seatNumber,
                            Locale locale) {
        Text text = seatText(sectionName, rowLabel, seatNumber);
        return text != null ? render(text, locale) : null;
    }

    private String format(@Nullable Object value, Locale locale) {
        return switch (value) {
            case null -> "";
            case Text text -> render(text, locale);
            case BigDecimal amount -> amount.toPlainString();
            case LocalDateTime time -> time.format(DATE_TIME);
            default -> String.valueOf(value);
        };
    }
}
