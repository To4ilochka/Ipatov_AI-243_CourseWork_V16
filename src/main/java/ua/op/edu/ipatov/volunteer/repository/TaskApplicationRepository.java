package ua.op.edu.ipatov.volunteer.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ua.op.edu.ipatov.volunteer.domain.ApplicationStatus;
import ua.op.edu.ipatov.volunteer.domain.TaskApplication;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TaskApplicationRepository extends JpaRepository<TaskApplication, Long> {

    boolean existsByTaskIdAndVolunteerIdAndStatusIn(Long taskId, Long volunteerId, Collection<ApplicationStatus> statuses);

    @EntityGraph(attributePaths = {"task", "task.initiative", "volunteer"})
    List<TaskApplication> findByVolunteerIdOrderByCreatedAtDesc(Long volunteerId);

    @EntityGraph(attributePaths = {"task", "task.initiative", "volunteer", "volunteer.skills", "volunteer.skills.skill"})
    @Query("""
            SELECT a FROM TaskApplication a
            WHERE a.task.initiative.coordinator.id = :coordinatorId
            ORDER BY a.createdAt DESC
            """)
    List<TaskApplication> findAllForCoordinator(@Param("coordinatorId") Long coordinatorId);

    @EntityGraph(attributePaths = {"task", "task.initiative", "task.initiative.coordinator", "volunteer"})
    Optional<TaskApplication> findDetailedById(Long id);

    List<TaskApplication> findByTaskIdAndStatusOrderByCreatedAtAsc(Long taskId, ApplicationStatus status);
}
