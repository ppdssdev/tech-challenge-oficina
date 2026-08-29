package br.com.fiap.techchallenge.oficina.application.port.out;

public interface NotificationPort {
    void send(NotificationMessage message);

    record NotificationMessage(
        String recipient,
        String subject,
        String body
    ) {
    }
}
