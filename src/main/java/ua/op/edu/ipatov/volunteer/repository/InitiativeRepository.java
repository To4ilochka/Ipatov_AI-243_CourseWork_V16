package ua.op.edu.ipatov.volunteer.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ua.op.edu.ipatov.volunteer.domain.Initiative;
import ua.op.edu.ipatov.volunteer.domain.InitiativeStatus;
import java.util.List;
import java.util.Optional;

public interface InitiativeRepository extends JpaRepository<Initiative, Long> {

    @EntityGraph(attributePaths = {"coordinator"})
    List<Initiative> findByStatusOrderByCreatedAtDesc(InitiativeStatus status);

    @EntityGraph(attributePaths = {"coordinator"})
    List<Initiative> findByCoordinatorIdOrderByCreatedAtDesc(Long coordinatorId);

    @EntityGraph(attributePaths = {"coordinator", "tasks"})
    Optional<Initiative> findWithTasksById(Long id);
}
