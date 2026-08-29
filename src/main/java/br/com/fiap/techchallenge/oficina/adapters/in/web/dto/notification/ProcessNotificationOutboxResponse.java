package br.com.fiap.techchallenge.oficina.adapters.in.web.dto.notification;

public record ProcessNotificationOutboxResponse(int processed, int sent, int failed) {
}
