package com.manager.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String BEARER_AUTH = "BearerAuth";

    @Bean
    public OpenAPI springBlogPessoalOpenAPI() {
        return new OpenAPI().info(
                        new Info().title("API de Autenticação")
                                .description("Projeto Integrador AprovIA")
                                .version("v0.0.1")
                                .license(new License()
                                        .name("Allan D., Beatriz e Nickolas")
                                        .url("https://github.com/AllanDeyvison/manager_back"))
                                .contact(new Contact()
                                        .name("AprovIA")
                                        .url("")
                                        .email("aprovia.faq@gmail.com")))
                .externalDocs(new ExternalDocumentation()
                        .description("Github")
                        .url("https://github.com/AllanDeyvison/manager_back")
                )
                // Botão Authorize: cole o token como o /user/login devolve ("Bearer <JWT>"),
                // igual ao Swagger do backend Python
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("Authorization")
                        .description("Informe o token no formato: Bearer <JWT>")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    @Bean
    public OpenApiCustomizer customerGlobalHeaderOpenApiCustomiser() {

        return openApi -> {
            openApi.getPaths().values().forEach(pathItem -> pathItem.readOperations().forEach(operation -> {

                ApiResponses apiResponses = operation.getResponses();

                apiResponses.addApiResponse("200", createApiResponse("Deu bom! 😎"));
                apiResponses.addApiResponse("201", createApiResponse("Salvou!"));
                apiResponses.addApiResponse("204", createApiResponse("Foi de vala! 👻👻"));
                apiResponses.addApiResponse("400", createApiResponse("Deu ruim!"));
                apiResponses.addApiResponse("401", createApiResponse("Pode não, brother!"));
                apiResponses.addApiResponse("403", createApiResponse("Ta errado isso ai!"));
                apiResponses.addApiResponse("404", createApiResponse("Não achei!"));
                apiResponses.addApiResponse("500", createApiResponse("Chama o cara do back que deu ruim!"));

            }));
        };
    }

    private ApiResponse createApiResponse(String message) {

        return new ApiResponse().description(message);

    }
}
