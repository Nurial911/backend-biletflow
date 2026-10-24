package kz.edu.biletflow.backend.configs;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI biletFlowOpenApi() {
        return new OpenAPI()
                // "/" = same address the page was opened from (localhost, tunnel, or real server),
                // so Swagger never calls http:// from an https:// page
                .servers(List.of(new Server().url("/").description("Current server")))
                .info(new Info()
                        .title("BiletFlow API")
                        .version("0.3")
                        .description("Backend API for the BiletFlow ticketing platform"))
                // adds the "Authorize" button (paste the accessToken from /api/auth/login)
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}

