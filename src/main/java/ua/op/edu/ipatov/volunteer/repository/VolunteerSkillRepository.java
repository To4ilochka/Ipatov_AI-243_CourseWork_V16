package ua.op.edu.ipatov.volunteer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.op.edu.ipatov.volunteer.domain.VolunteerSkill;
import java.util.List;

public interface VolunteerSkillRepository extends JpaRepository<VolunteerSkill, Long> {
    List<VolunteerSkill> findByVolunteerIdOrderBySkillTitleAsc(Long volunteerId);
    void deleteByVolunteerIdAndSkillId(Long volunteerId, Long skillId);
}
