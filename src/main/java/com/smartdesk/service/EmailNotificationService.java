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

    @Value("${frontend.url:http://localhost:4200}")
    private String frontendUrl;

    public EmailNotificationService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://api.resend.com").build();
    }

    public void sendVerificationEmail(User user, VerificationToken token) {
        String activationLink = frontendUrl + "/auth/verify?token=" + token.getToken();
        
        String htmlContent = String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; background-color: #f4f5f7; margin: 0; padding: 0; }
                    .container { max-width: 600px; margin: 40px auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 15px rgba(0,0,0,0.05); }
                    .header { background-color: #1a1b26; padding: 30px; text-align: center; }
                    .header img { height: 40px; }
                    .content { padding: 40px; color: #333333; line-height: 1.6; }
                    .content h1 { font-size: 24px; font-weight: 600; color: #1a1b26; margin-top: 0; }
                    .otp-box { background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 20px; text-align: center; margin: 30px 0; }
                    .otp-code { font-size: 32px; font-weight: 700; letter-spacing: 6px; color: #3b82f6; margin: 0; }
                    .button { display: inline-block; background-color: #3b82f6; color: #ffffff; text-decoration: none; padding: 14px 28px; border-radius: 8px; font-weight: 600; font-size: 16px; margin-top: 10px; }
                    .footer { text-align: center; padding: 20px; color: #888888; font-size: 14px; background-color: #f9fafb; border-top: 1px solid #f3f4f6; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <img src="https://smartdeskcloud.com/assets/logo-light.png" alt="SmartDesk Logo" onerror="this.src='https://placehold.co/200x50/1a1b26/ffffff?text=SmartDesk'"/>
                    </div>
                    <div class="content">
                        <h1>¡Bienvenido a SmartDesk, %s!</h1>
                        <p>Estamos muy emocionados de tenerte a bordo. Para activar tu cuenta y configurar tu espacio de trabajo (Tenant), por favor ingresa el siguiente código de activación de 6 dígitos en la aplicación:</p>
                        
                        <div class="otp-box">
                            <p class="otp-code">%s</p>
                        </div>
                        
                        <p>O si prefieres activar tu cuenta automáticamente, puedes hacer clic en el siguiente botón:</p>
                        <center>
                            <a href="%s" class="button">Activar Cuenta</a>
                        </center>
                    </div>
                    <div class="footer">
                        &copy; 2026 SmartDesk Cloud. Todos los derechos reservados.<br>
                        Si no solicitaste este correo, puedes ignorarlo de manera segura.
                    </div>
                </div>
            </body>
            </html>
            """, user.getName(), token.getShortCode(), activationLink);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("from", "SmartDesk <" + resendFromEmail + ">");
        requestBody.put("to", List.of(user.getEmail()));
        requestBody.put("subject", "Bienvenido a SmartDesk - Código de Activación: " + token.getShortCode());
        requestBody.put("html", htmlContent);

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
