package com.utochkin.notificationservice.services;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Log4j2
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendOrderNotification(String to, String subject, String text) {
        log.info("EmailService: отправка уведомления на {}: {}", to, subject);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);

        try {
            mailSender.send(message);
            log.info("EmailService: письмо успешно отправлено на {}", to);
        } catch (MailException exception) {
            log.error("EmailService: не удалось отправить письмо на {}: {}", to, exception.getMessage(), exception);
            throw exception;
        }
    }
}
