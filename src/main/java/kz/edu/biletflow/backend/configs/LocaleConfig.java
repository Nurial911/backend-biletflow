package kz.edu.biletflow.backend.configs;

import kz.edu.biletflow.backend.entities.Language;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.Arrays;

// Accept-Language picks the language of anonymous requests and of new accounts that don't name one;
// without the header, or with a language we don't have, it is Russian.
@Configuration
public class LocaleConfig {
    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(Arrays.stream(Language.values()).map(Language::locale).toList());
        resolver.setDefaultLocale(Language.DEFAULT.locale());
        return resolver;
    }
}
