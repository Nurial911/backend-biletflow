package kz.edu.biletflow.backend.i18n;

import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A text that is not yet in any language: message keys with their arguments, joined in order, plus literal
 * pieces (a user's own words, such as a refund reason). {@link Translator} renders it in the recipient's
 * language, so one action can tell several people, each in their own.
 * Arguments may be strings, numbers, dates and times (dd.MM.yyyy, HH:mm) or other Texts.
 */
public final class Text {
    // A list rather than an array, so equal texts are equal (seat labels are de-duplicated). Allows nulls.
    record Part(@Nullable String key, @Nullable String literal, List<Object> args) {
        static Part of(@Nullable String key, @Nullable String literal, Object... args) {
            return new Part(key, literal, Collections.unmodifiableList(Arrays.asList(args)));
        }
    }

    private final List<Part> parts;

    private Text(List<Part> parts) {
        this.parts = List.copyOf(parts);
    }

    public static Text of(String key, Object... args) {
        return new Text(List.of(Part.of(key, null, args)));
    }

    // Shown as given, in every language.
    public static Text raw(String value) {
        return new Text(List.of(Part.of(null, value)));
    }

    public Text and(String key, Object... args) {
        return and(of(key, args));
    }

    public Text and(Text other) {
        List<Part> joined = new ArrayList<>(parts);
        joined.addAll(other.parts);
        return new Text(joined);
    }

    public Text andIf(boolean condition, String key, Object... args) {
        return condition ? and(key, args) : this;
    }

    List<Part> parts() {
        return parts;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Text text && parts.equals(text.parts);
    }

    @Override
    public int hashCode() {
        return parts.hashCode();
    }
}
