package ua.op.edu.ipatov.volunteer.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import ua.op.edu.ipatov.volunteer.domain.TaskStatus;
import ua.op.edu.ipatov.volunteer.domain.VolunteerTask;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторій завдань із підтримкою песимістичного блокування для атомарного контролю ліміту місць
 * та специфікацій фільтрації каталогу.
 */
public interface VolunteerTaskRepository extends JpaRepository<VolunteerTask, Long>, JpaSpecificationExecutor<VolunteerTask> {

    @EntityGraph(attributePaths = {"initiative", "initiative.coordinator", "requiredSkills", "requiredSkills.skill"})
    Optional<VolunteerTask> findDetailedById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM VolunteerTask t WHERE t.id = :id")
    Optional<VolunteerTask> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT DISTINCT t FROM VolunteerTask t
            JOIN FETCH t.initiative i
            JOIN FETCH i.coordinator
            LEFT JOIN FETCH t.requiredSkills rs
            LEFT JOIN FETCH rs.skill
            WHERE t.status = :status
              AND i.status = ua.op.edu.ipatov.volunteer.domain.InitiativeStatus.ACTIVE
              AND t.approvedCount < t.maxVolunteers
              AND t.deadlineAt > :now
            """)
    List<VolunteerTask> findAllCandidateTasksForMatching(@Param("status") TaskStatus status,
                                                         @Param("now") Instant now);
}
