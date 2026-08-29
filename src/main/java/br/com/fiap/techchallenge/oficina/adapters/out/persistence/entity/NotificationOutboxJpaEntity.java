package br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity;

import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.Channel;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.Status;
import br.com.fiap.techchallenge.oficina.application.port.out.NotificationOutboxPort.Type;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity(name = "NotificationOutboxJpaEntity")
@Table(name = "notification_outbox")
public class NotificationOutboxJpaEntity {
    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Type type;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Channel channel;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status;
    @Column(nullable = false, length = 255)
    private String recipient;
    @Column(nullable = false, length = 255)
    private String subject;
    @Column(nullable = false, columnDefinition = "text")
    private String body;
    @Column(name = "work_order_code", length = 50)
    private String workOrderCode;
    @Column(name = "approve_url", columnDefinition = "text")
    private String approveUrl;
    @Column(name = "reject_url", columnDefinition = "text")
    private String rejectUrl;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        var now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public Channel getChannel() { return channel; }
    public void setChannel(Channel channel) { this.channel = channel; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getWorkOrderCode() { return workOrderCode; }
    public void setWorkOrderCode(String value) { workOrderCode = value; }
    public String getApproveUrl() { return approveUrl; }
    public void setApproveUrl(String value) { approveUrl = value; }
    public String getRejectUrl() { return rejectUrl; }
    public void setRejectUrl(String value) { rejectUrl = value; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public String getLastError() { return lastError; }
    public void setLastError(String value) { lastError = value; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getSentAt() { return sentAt; }
    public void setSentAt(OffsetDateTime value) { sentAt = value; }
}
