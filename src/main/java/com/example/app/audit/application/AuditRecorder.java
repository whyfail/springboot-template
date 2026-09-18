package com.example.app.audit.application;

import com.example.app.audit.domain.AuditEventEntity;
import com.example.app.audit.domain.AuditEventRepository;
import com.example.app.audit.domain.AuditEventType;
import com.example.app.audit.domain.AuditResult;
import com.example.app.shared.api.RequestIdFilter;
import com.example.app.shared.security.Hashes;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Persists audit events inside the caller's transaction when one exists (REQUIRES semantics), so
 * user creation writes the user and the audit row atomically. Details are sanitised JSON: callers
 * must never pass passwords or tokens, and request fingerprints are hashed, never stored raw.
 */
@Service
public class AuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);

    /** Actor identity for audit rows; absent for anonymous events such as failed logins. */
    public record Actor(Long userId, UUID publicId) {}

    public static final String TARGET_USER = "user";

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditRecorder(AuditEventRepository auditEventRepository, ObjectMapper objectMapper, Clock clock) {
        this.auditEventRepository = auditEventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public void record(
            AuditEventType eventType,
            AuditResult result,
            Actor actor,
            String targetType,
            UUID targetPublicId,
            Map<String, Object> details,
            String clientIp,
            String userAgent,
            String requestIdOverride) {
        AuditEventEntity event = new AuditEventEntity(
                UUID.randomUUID(),
                clock.instant(),
                actor != null ? actor.userId() : null,
                actor != null ? actor.publicId() : null,
                eventType,
                result,
                targetType,
                targetPublicId,
                requestIdOverride != null ? requestIdOverride : currentRequestId(),
                MDC.get("traceId"),
                clientIp != null ? Hashes.sha256(clientIp) : null,
                userAgent != null ? Hashes.sha256(userAgent) : null,
                toJson(details));
        auditEventRepository.save(event);
        log.debug("Audit event {} {} recorded for target {}", eventType, result, targetType);
    }

    public void record(
            AuditEventType eventType,
            AuditResult result,
            Actor actor,
            String targetType,
            UUID targetPublicId,
            Map<String, Object> details,
            String clientIp,
            String userAgent) {
        record(eventType, result, actor, targetType, targetPublicId, details, clientIp, userAgent, null);
    }

    private String currentRequestId() {
        String requestId = MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY);
        return requestId != null ? requestId : "no-request-context";
    }

    private String toJson(Map<String, Object> details) {
        Map<String, Object> payload = details == null ? Map.of() : new HashMap<>(details);
        payload.remove("password");
        payload.remove("token");
        payload.remove("passwordHash");
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Audit details serialization failed; storing failure marker only");
            return "{\"serialization\":\"failed\"}";
        }
    }
}
