package br.com.fiap.techchallenge.oficina.application.port.in;

public interface ProcessNotificationOutboxUseCase {
    ProcessNotificationOutboxResult processPending();

    record ProcessNotificationOutboxResult(int processed, int sent, int failed) {
    }
}
