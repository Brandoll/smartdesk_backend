package com.smartdesk.service;

import com.smartdesk.model.entity.User;
import com.smartdesk.model.entity.VerificationToken;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final JavaMailSender mailSender;

    public void sendVerificationEmail(User user, VerificationToken token) {
        String verificationUrl = "http://localhost:4200/verify?token=" + token.getToken();
        
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("noreply@smartdesk.com");
        message.setTo(user.getEmail());
        message.setSubject("Activa tu cuenta SmartDesk y crea tu Sandbox");
        message.setText("Hola " + user.getNombres() + ",\n\n" +
                "Por favor, activa tu cuenta y crea tu empresa accediendo al siguiente enlace:\n\n" +
                verificationUrl + "\n\n" +
                "Este enlace expira en 24 horas.\n\n" +
                "El equipo de SmartDesk.");
                
        mailSender.send(message);
    }
}
