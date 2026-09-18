package com.wl.cwa.audit.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, Long> {

    boolean existsByPublicId(UUID publicId);
}
