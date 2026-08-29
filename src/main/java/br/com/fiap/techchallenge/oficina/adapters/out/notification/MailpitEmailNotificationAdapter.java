package br.com.fiap.techchallenge.oficina.adapters.out.notification;

import br.com.fiap.techchallenge.oficina.application.port.out.NotificationPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class MailpitEmailNotificationAdapter implements NotificationPort {
    private final JavaMailSender mailSender;
    private final String from;

    public MailpitEmailNotificationAdapter(
        JavaMailSender mailSender,
        @Value("${app.notification.email.from}") String from
    ) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(NotificationMessage notification) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(notification.recipient());
        message.setSubject(notification.subject());
        message.setText(notification.body());
        mailSender.send(message);
    }
}
