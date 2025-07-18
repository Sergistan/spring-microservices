package com.utochkin.notificationservice.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;


@ExtendWith(MockitoExtension.class)
public class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    @Captor
    private ArgumentCaptor<SimpleMailMessage> messageCaptor;

    @Test
    @DisplayName("Уведомление о заказе на отправку должно вызвать JavaMailSender и отправить корректные поля")
    void sendOrderNotification_invokesMailSender() {
        String to      = "user@example.com";
        String subject = "Hello";
        String text    = "Body text";

        emailService.sendOrderNotification(to, subject, text);

        then(mailSender).should().send(messageCaptor.capture());
        SimpleMailMessage sent = messageCaptor.getValue();

        assertThat(sent.getTo()).containsExactly(to);
        assertThat(sent.getSubject()).isEqualTo(subject);
        assertThat(sent.getText()).isEqualTo(text);
    }

    @Test
    @DisplayName("Уведомление о заказе на отправку выдает исключение MailException в случае сбоя")
    void sendOrderNotification_throwsOnMailException() {
        willThrow(new MailSendException("smtp down"))
                .given(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() ->
                emailService.sendOrderNotification("a@b.com", "subj", "body"))
                .isInstanceOf(MailException.class)
                .hasMessageContaining("smtp down");
    }

}
