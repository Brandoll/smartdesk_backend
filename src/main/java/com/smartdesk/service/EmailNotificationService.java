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
                    body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; background-color: #f8f9fa; margin: 0; padding: 0; }
                    .container { max-width: 600px; margin: 40px auto; background-color: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 8px 24px rgba(0,0,0,0.06); }
                    .header { background: linear-gradient(135deg, #F05023 0%%, #FF9A55 100%%); padding: 40px 30px; text-align: center; }
                    .header-icon { background: #ffffff; width: 64px; height: 64px; border-radius: 16px; display: inline-flex; align-items: center; justify-content: center; margin-bottom: 16px; box-shadow: 0 8px 16px rgba(0,0,0,0.1); }
                    .header-icon img { height: 40px; width: auto; display: block; margin: auto; padding-top: 12px; }
                    .header h2 { color: #ffffff; margin: 0; font-size: 24px; font-weight: 600; letter-spacing: -0.02em; }
                    .content { padding: 40px; color: #191c1d; line-height: 1.6; text-align: center; }
                    .content h1 { font-size: 22px; font-weight: 600; color: #191c1d; margin-top: 0; margin-bottom: 16px; }
                    .content p { font-size: 16px; color: #5b403d; margin-bottom: 24px; }
                    .otp-box { background-color: #fffaf9; border: 1px dashed #F05023; border-radius: 12px; padding: 24px; text-align: center; margin: 32px 0; }
                    .otp-code { font-family: monospace; font-size: 36px; font-weight: 700; letter-spacing: 8px; color: #F05023; margin: 0; }
                    .otp-label { font-size: 12px; color: #8f6f6c; text-transform: uppercase; letter-spacing: 0.1em; font-weight: 600; margin-bottom: 8px; display: block; }
                    .button { display: inline-block; background-color: #F05023; color: #ffffff; text-decoration: none; padding: 16px 32px; border-radius: 12px; font-weight: 600; font-size: 16px; margin-top: 10px; box-shadow: 0 4px 12px rgba(240,80,35,0.25); transition: background-color 0.2s; }
                    .footer { text-align: center; padding: 24px; color: #8f6f6c; font-size: 13px; background-color: #f3f4f5; border-top: 1px solid #e1e3e4; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <div class="header-icon">
                            <img src="https://smartdeskcloud.com/assets/logo-light.png" alt="SmartDesk Logo" onerror="this.src='https://placehold.co/40x40/F05023/ffffff?text=SD'"/>
                        </div>
                        <h2>SmartDesk</h2>
                    </div>
                    <div class="content">
                        <h1>¡Bienvenido a tu nuevo espacio de trabajo, %s!</h1>
                        <p>Estamos emocionados de tenerte con nosotros. Para activar tu entorno y comenzar a operar, por favor ingresa el siguiente código de activación en la aplicación:</p>
                        
                        <div class="otp-box">
                            <span class="otp-label">Tu código de activación</span>
                            <p class="otp-code">%s</p>
                        </div>
                        
                        <p>O si prefieres activar tu cuenta automáticamente, haz clic en el siguiente botón:</p>
                        <a href="%s" class="button">Activar y Entrar</a>
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
