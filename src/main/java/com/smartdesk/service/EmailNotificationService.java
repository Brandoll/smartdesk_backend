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
                    body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; background-color: #f4f5f7; margin: 0; padding: 40px 0; }
                    .container { max-width: 540px; margin: 0 auto; background-color: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 12px 32px rgba(0,0,0,0.06); }
                    .gradient-bar { height: 8px; background: linear-gradient(135deg, #F05023 0%%, #FF9A55 100%%); width: 100%%; }
                    .header { padding: 48px 30px 24px 30px; text-align: center; }
                    .header img { height: 60px; width: auto; display: block; margin: 0 auto; }
                    .content { padding: 0 48px 40px 48px; color: #191c1d; line-height: 1.6; text-align: center; }
                    .content h1 { font-size: 24px; font-weight: 700; color: #191c1d; margin-top: 16px; margin-bottom: 12px; letter-spacing: -0.02em; }
                    .content p { font-size: 16px; color: #4a4a4a; margin-bottom: 32px; }
                    .otp-box { background: linear-gradient(180deg, #ffffff 0%%, #fff5f2 100%%); border: 1px solid #ffe6de; border-radius: 16px; padding: 32px 24px; text-align: center; margin: 32px 0; box-shadow: 0 4px 16px rgba(240,80,35,0.05); }
                    .otp-code { font-family: 'Courier New', Courier, monospace; font-size: 46px; font-weight: 700; letter-spacing: 12px; color: #F05023; margin: 0; text-shadow: 0 2px 8px rgba(240,80,35,0.15); }
                    .otp-label { font-size: 12px; color: #F05023; text-transform: uppercase; letter-spacing: 0.2em; font-weight: 700; margin-bottom: 16px; display: block; opacity: 0.9; }
                    .button { display: inline-block; background: linear-gradient(135deg, #F05023 0%%, #FF9A55 100%%); color: #ffffff !important; text-decoration: none; padding: 18px 36px; border-radius: 9999px; font-weight: 600; font-size: 16px; margin-top: 12px; box-shadow: 0 8px 20px rgba(240,80,35,0.3); transition: transform 0.2s; }
                    .divider { height: 1px; background-color: #f3f4f6; margin: 0 48px 32px 48px; }
                    .footer { text-align: center; padding: 0 48px 48px 48px; color: #9ca3af; font-size: 13px; line-height: 1.5; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="gradient-bar"></div>
                    <div class="header">
                        <img src="https://www.smartdeskcloud.com/SmartDesk_small.png" alt="SmartDesk Logo" onerror="this.src='https://placehold.co/160x60/ffffff/F05023?text=SmartDesk'"/>
                    </div>
                    <div class="content">
                        <h1>¡Hola, %s!</h1>
                        <p>Bienvenido a SmartDesk. Para asegurar tu cuenta y activar tu entorno de trabajo, introduce el siguiente código en la pantalla de verificación:</p>
                        
                        <div class="otp-box">
                            <span class="otp-label">Código de Seguridad</span>
                            <p class="otp-code">%s</p>
                        </div>
                        
                        <a href="%s" class="button" style="color: #ffffff; text-decoration: none;">Activar mi Entorno</a>
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
