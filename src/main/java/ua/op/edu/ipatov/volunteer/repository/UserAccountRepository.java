package ua.op.edu.ipatov.volunteer.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ua.op.edu.ipatov.volunteer.domain.UserAccount;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {"skills", "skills.skill"})
    Optional<UserAccount> findWithSkillsById(Long id);

    boolean existsByEmailIgnoreCase(String email);
}
