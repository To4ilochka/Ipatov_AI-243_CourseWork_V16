package ua.op.edu.ipatov.volunteer.repository;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import ua.op.edu.ipatov.volunteer.domain.*;
import java.util.Locale;

/**
 * Динамічні специфікації фільтрації та пошуку завдань для публічного каталогу і кабінету волонтера.
 */
public final class TaskSpecifications {

    private TaskSpecifications() {
    }

    public static Specification<VolunteerTask> publicCatalogFilter(
            String query,
            String category,
            String city,
            WorkFormat workFormat,
            TaskUrgency urgency,
            Long skillId,
            boolean onlyOpen) {
        return (root, cq, cb) -> {
            if (cq != null && Long.class != cq.getResultType() && long.class != cq.getResultType()) {
                root.fetch("initiative", JoinType.INNER).fetch("coordinator", JoinType.INNER);
                cq.distinct(true);
            }
            Join<VolunteerTask, Initiative> initiative = root.join("initiative", JoinType.INNER);
            var predicates = cb.conjunction();

            predicates = cb.and(predicates, cb.notEqual(initiative.get("status"), InitiativeStatus.DRAFT));
            predicates = cb.and(predicates, cb.notEqual(initiative.get("status"), InitiativeStatus.ARCHIVED));

            if (onlyOpen) {
                predicates = cb.and(predicates, cb.equal(root.get("status"), TaskStatus.OPEN));
                predicates = cb.and(predicates, cb.equal(initiative.get("status"), InitiativeStatus.ACTIVE));
                predicates = cb.and(predicates, cb.lessThan(root.get("approvedCount"), root.get("maxVolunteers")));
            }
            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                predicates = cb.and(predicates, cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern),
                        cb.like(cb.lower(initiative.get("title")), pattern)
                ));
            }
            if (category != null && !category.isBlank()) {
                predicates = cb.and(predicates, cb.equal(initiative.get("category"), category.trim()));
            }
            if (city != null && !city.isBlank()) {
                predicates = cb.and(predicates, cb.equal(cb.lower(initiative.get("locationCity")),
                        city.trim().toLowerCase(Locale.ROOT)));
            }
            if (workFormat != null) {
                predicates = cb.and(predicates, cb.equal(root.get("workFormat"), workFormat));
            }
            if (urgency != null) {
                predicates = cb.and(predicates, cb.equal(root.get("urgency"), urgency));
            }
            if (skillId != null) {
                if (cq != null) {
                    cq.distinct(true);
                }
                Join<VolunteerTask, TaskRequiredSkill> reqJoin = root.join("requiredSkills", JoinType.INNER);
                predicates = cb.and(predicates, cb.equal(reqJoin.get("skill").get("id"), skillId));
            }
            return predicates;
        };
    }
}
