package com.smartdesk.service;

import com.smartdesk.model.entity.User;
import com.smartdesk.model.entity.VerificationToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EmailNotificationService {

    private final WebClient webClient;
    
    @Value("${resend.api.key}")
    private String resendApiKey;

    @Value("${resend.from.email:onboarding@resend.dev}")
    private String resendFromEmail;

    public EmailNotificationService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://api.resend.com").build();
    }

    public void sendVerificationEmail(User user, VerificationToken token) {
        String activationLink = "http://localhost:4200/auth/verify?token=" + token.getToken();
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("from", "SmartDesk <" + resendFromEmail + ">");
        requestBody.put("to", List.of(user.getEmail()));
        requestBody.put("subject", "Bienvenido a SmartDesk - Activa tu cuenta");
        requestBody.put("html", "<p>Hola " + user.getName() + ",</p>" +
                "<p>Gracias por registrarte en SmartDesk. Tu código de activación es: <b>" + token.getToken() + "</b></p>" +
                "<p>Puedes copiar el código o hacer clic en el siguiente enlace para activar tu cuenta y configurar tu entorno (Tenant):</p>" +
                "<p><a href=\"" + activationLink + "\">Activar Cuenta Automáticamente</a></p>");

        webClient.post()
                .uri("/emails")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + resendApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(response -> log.info("Email enviado exitosamente a {}", user.getEmail()))
                .doOnError(error -> log.error("Error enviando email a {}: {}", user.getEmail(), error.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }
}
