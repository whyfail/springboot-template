package {{ package }}.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_event")
public class AuditEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_public_id")
    private UUID actorPublicId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, length = 32)
    private String result;

    @Column(name = "target_type", length = 64)
    private String targetType;

    @Column(name = "target_public_id")
    private UUID targetPublicId;

    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "client_ip_hash", length = 32)
    private byte[] clientIpHash;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "user_agent_hash", length = 32)
    private byte[] userAgentHash;

    @Column(name = "details_json", columnDefinition = "json")
    private String detailsJson;

    protected AuditEventEntity() {}

    public AuditEventEntity(
            UUID publicId,
            Instant occurredAt,
            Long actorUserId,
            UUID actorPublicId,
            AuditEventType eventType,
            AuditResult result,
            String targetType,
            UUID targetPublicId,
            String requestId,
            String traceId,
            byte[] clientIpHash,
            byte[] userAgentHash,
            String detailsJson) {
        this.publicId = publicId;
        this.occurredAt = occurredAt;
        this.actorUserId = actorUserId;
        this.actorPublicId = actorPublicId;
        this.eventType = eventType.name();
        this.result = result.name();
        this.targetType = targetType;
        this.targetPublicId = targetPublicId;
        this.requestId = requestId;
        this.traceId = traceId;
        this.clientIpHash = clientIpHash;
        this.userAgentHash = userAgentHash;
        this.detailsJson = detailsJson;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public UUID getActorPublicId() {
        return actorPublicId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getResult() {
        return result;
    }

    public String getTargetType() {
        return targetType;
    }

    public UUID getTargetPublicId() {
        return targetPublicId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getTraceId() {
        return traceId;
    }

    public byte[] getClientIpHash() {
        return clientIpHash;
    }

    public byte[] getUserAgentHash() {
        return userAgentHash;
    }

    public String getDetailsJson() {
        return detailsJson;
    }
}
