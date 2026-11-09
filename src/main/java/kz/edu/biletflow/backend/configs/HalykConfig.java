package kz.edu.biletflow.backend.configs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// Halyk ePay connection, used when app.payments.provider=halyk. The URL defaults are Halyk's test environment.
@Configuration
@ConfigurationProperties(prefix = "app.payments.halyk")
@Getter
@Setter
public class HalykConfig {
    private String clientId = "";
    private String clientSecret = "";
    private String terminalId = "";
    private String oauthUrl = "";
    private String apiUrl = "";
    private String scriptUrl = "";
    // Public https address of this backend for the bank's result call. Empty: rely on status checks.
    private String callbackBaseUrl = "";
    // True against the test environment: payments stay labelled as demonstrations (SRS 4.6).
    private boolean testMode = true;
    // How long the bank's payment page stays usable. Halyk's test page counts down 20 minutes.
    private int sessionMinutes = 20;
}
