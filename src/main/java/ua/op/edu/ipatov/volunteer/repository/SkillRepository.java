package ua.op.edu.ipatov.volunteer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.op.edu.ipatov.volunteer.domain.Skill;
import java.util.List;
import java.util.Optional;

public interface SkillRepository extends JpaRepository<Skill, Long> {
    Optional<Skill> findByCode(String code);
    List<Skill> findAllByOrderByCategoryAscTitleAsc();
}
