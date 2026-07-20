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
                    .container { max-width: 520px; margin: 60px auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 24px rgba(0,0,0,0.04); border: 1px solid #e1e3e4; }
                    .header { padding: 40px 30px 20px 30px; text-align: center; }
                    .header img { height: 32px; width: auto; display: block; margin: 0 auto; }
                    .content { padding: 0 40px 40px 40px; color: #191c1d; line-height: 1.6; text-align: center; }
                    .content h1 { font-size: 20px; font-weight: 600; color: #191c1d; margin-top: 24px; margin-bottom: 12px; letter-spacing: -0.01em; }
                    .content p { font-size: 15px; color: #4a4a4a; margin-bottom: 32px; }
                    .otp-box { background-color: #f9fafb; border: 1px solid #e1e3e4; border-radius: 12px; padding: 24px; text-align: center; margin: 32px 0; }
                    .otp-code { font-family: 'Courier New', Courier, monospace; font-size: 40px; font-weight: 700; letter-spacing: 12px; color: #F05023; margin: 0; }
                    .otp-label { font-size: 11px; color: #6b7280; text-transform: uppercase; letter-spacing: 0.15em; font-weight: 600; margin-bottom: 12px; display: block; }
                    .button { display: inline-block; background-color: #F05023; color: #ffffff; text-decoration: none; padding: 14px 28px; border-radius: 8px; font-weight: 600; font-size: 15px; margin-top: 8px; transition: opacity 0.2s; }
                    .divider { height: 1px; background-color: #f3f4f6; margin: 32px 0; }
                    .footer { text-align: center; padding: 0 40px 40px 40px; color: #9ca3af; font-size: 12px; line-height: 1.5; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <img src="https://www.smartdeskcloud.com/SmartDesk_small.png" alt="SmartDesk Logo" onerror="this.src='https://placehold.co/120x32/ffffff/F05023?text=SmartDesk'"/>
                    </div>
                    <div class="content">
                        <h1>¡Hola, %s!</h1>
                        <p>Bienvenido a SmartDesk. Para asegurar tu cuenta y activar tu entorno de trabajo, introduce el siguiente código en la pantalla de verificación:</p>
                        
                        <div class="otp-box">
                            <span class="otp-label">Código de Seguridad</span>
                            <p class="otp-code">%s</p>
                        </div>
                        
                        <a href="%s" class="button">Activar mi Entorno</a>
                    </div>
                    <div class="divider"></div>
                    <div class="footer">
                        SmartDesk Cloud &copy; 2026<br>
                        Este es un mensaje automático. Si no solicitaste este registro, por favor ignora este correo.
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
