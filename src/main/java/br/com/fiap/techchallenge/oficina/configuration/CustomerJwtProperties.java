package br.com.fiap.techchallenge.oficina.configuration;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.jwt.customer")
public record CustomerJwtProperties(boolean enabled, String issuer, String audience,
                                    Map<String, String> publicKeys) {
}
