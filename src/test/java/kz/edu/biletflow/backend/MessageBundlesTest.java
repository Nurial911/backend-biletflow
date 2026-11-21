package kz.edu.biletflow.backend;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

// The Russian and Kazakh texts must cover every English key, with the same placeholders, or a message would
// fall back to English (missing key) or lose a value such as an order number (missing placeholder).
class MessageBundlesTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+}");

    @Test
    void everyLanguageHasEveryMessageWithTheSamePlaceholders() throws IOException {
        Properties english = load("i18n/messages.properties");
        for (String file : new String[]{"i18n/messages_ru.properties", "i18n/messages_kk.properties"}) {
            Properties other = load(file);
            assertThat(other.stringPropertyNames()).as(file).isEqualTo(english.stringPropertyNames());
            for (String key : english.stringPropertyNames()) {
                assertThat(placeholders(other.getProperty(key))).as(file + " " + key)
                        .isEqualTo(placeholders(english.getProperty(key)));
                assertThat(other.getProperty(key)).as(file + " " + key).isNotBlank();
            }
        }
    }

    private static Properties load(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = MessageBundlesTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as(resource).isNotNull();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    private static Set<String> placeholders(String value) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }
}
