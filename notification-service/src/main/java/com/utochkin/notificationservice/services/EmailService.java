package com.utochkin.notificationservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Формирование и отправка письма (JavaMailSender, SMTP-учётка из config-repo в {cipher}).
 * Текст собирается по статусу заказа: SUCCESS — детали заказа и адрес, FAILED/REFUNDED — короткие шаблоны.
 * From задаётся явно из spring.mail.username: без него MAIL FROM формируется из системных
 * данных, и строгие SMTP-серверы (Яндекс) отклоняют команду кодом 501 Bad address syntax.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class EmailService {

    private final JavaMailSender mailSender;

    /** Адрес отправителя = SMTP-username (дефолт пустой — в тестах JavaMailSender замокан). */
    @Value("${spring.mail.username:}")
    private String from;

    /** Отправка письма (SimpleMailMessage через JavaMailSender; SMTP-учётка из config-repo). */
    public void sendOrderNotification(String to, String subject, String text) {
        log.info("EmailService: отправка уведомления на {}: {}", to, subject);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);

        try {
            mailSender.send(message);
            log.info("EmailService: письмо успешно отправлено на {}", to);
        } catch (MailException exception) {
            // WARN без стектрейса: это промежуточный сбой — Kafka-ретраи повторят отправку,
            // и в подавляющем большинстве случаев письмо уходит со следующей попытки.
            // Финальная неудача (все ретраи исчерпаны) логируется ERROR в KafkaErrorConfig при переносе в DLT.
            log.warn("EmailService: не удалось отправить письмо на {} ({}), будет повторено ретраем",
                    to, exception.getMostSpecificCause().toString());
            throw exception;
        }
    }
}
