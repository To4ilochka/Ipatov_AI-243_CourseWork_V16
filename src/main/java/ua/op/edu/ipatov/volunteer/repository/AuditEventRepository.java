package ua.op.edu.ipatov.volunteer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.op.edu.ipatov.volunteer.domain.AuditEvent;
import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findTop50ByOrderByOccurredAtDesc();
}
