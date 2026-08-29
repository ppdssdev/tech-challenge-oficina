package br.com.fiap.techchallenge.oficina.adapters.out.notification;

import br.com.fiap.techchallenge.oficina.application.port.out.NotificationPort.NotificationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MailpitEmailNotificationAdapterTest {
    @Mock JavaMailSender mailSender;

    @Test
    void shouldBuildAndSendSimpleMailMessage() {
        var adapter = new MailpitEmailNotificationAdapter(mailSender, "oficina@local.test");

        adapter.send(new NotificationMessage("cliente@email.com", "Orçamento OS-001", "Corpo do e-mail"));

        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getFrom()).isEqualTo("oficina@local.test");
        assertThat(captor.getValue().getTo()).containsExactly("cliente@email.com");
        assertThat(captor.getValue().getSubject()).isEqualTo("Orçamento OS-001");
        assertThat(captor.getValue().getText()).isEqualTo("Corpo do e-mail");
    }
}
