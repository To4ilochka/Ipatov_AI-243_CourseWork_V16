package ua.op.edu.ipatov.volunteer.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ua.op.edu.ipatov.volunteer.domain.ExecutionReport;
import java.util.List;
import java.util.Optional;

public interface ExecutionReportRepository extends JpaRepository<ExecutionReport, Long> {

    Optional<ExecutionReport> findByApplicationId(Long applicationId);

    @EntityGraph(attributePaths = {"application", "application.task", "application.task.initiative", "application.task.initiative.coordinator", "author"})
    Optional<ExecutionReport> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"application", "application.task", "application.task.initiative", "author"})
    List<ExecutionReport> findByAuthorIdOrderBySubmittedAtDesc(Long authorId);

    @EntityGraph(attributePaths = {"application", "application.task", "application.task.initiative", "author"})
    @Query("""
            SELECT r FROM ExecutionReport r
            WHERE r.application.task.initiative.coordinator.id = :coordinatorId
            ORDER BY r.submittedAt DESC
            """)
    List<ExecutionReport> findAllForCoordinator(@Param("coordinatorId") Long coordinatorId);
}
