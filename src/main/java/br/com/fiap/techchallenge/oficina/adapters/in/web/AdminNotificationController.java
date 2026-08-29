package br.com.fiap.techchallenge.oficina.adapters.in.web;

import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.notification.ProcessNotificationOutboxResponse;
import br.com.fiap.techchallenge.oficina.application.port.in.ProcessNotificationOutboxUseCase;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/notifications/outbox")
public class AdminNotificationController {
    private final ProcessNotificationOutboxUseCase processOutbox;

    public AdminNotificationController(ProcessNotificationOutboxUseCase processOutbox) {
        this.processOutbox = processOutbox;
    }

    @PostMapping("/process")
    @Operation(summary = "Processa manualmente as notificações pendentes na outbox")
    public ProcessNotificationOutboxResponse processPending() {
        var result = processOutbox.processPending();
        return new ProcessNotificationOutboxResponse(result.processed(), result.sent(), result.failed());
    }
}
