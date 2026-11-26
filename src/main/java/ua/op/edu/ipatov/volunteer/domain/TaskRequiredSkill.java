package ua.op.edu.ipatov.volunteer.domain;

import jakarta.persistence.*;

/**
 * Вимога завдання до конкретної навички волонтера (мінімальний рівень, обов'язковість, вага).
 */
@Entity
@Table(name = "task_required_skills", uniqueConstraints = {
        @UniqueConstraint(name = "uq_task_skill", columnNames = {"task_id", "skill_id"})
})
public class TaskRequiredSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private VolunteerTask task;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(name = "min_level", nullable = false)
    private short minLevel;

    @Column(nullable = false)
    private boolean mandatory;

    @Column(nullable = false)
    private short weight;

    public TaskRequiredSkill() {
    }

    public TaskRequiredSkill(VolunteerTask task, Skill skill, short minLevel, boolean mandatory, short weight) {
        this.task = task;
        this.skill = skill;
        this.minLevel = minLevel;
        this.mandatory = mandatory;
        this.weight = weight;
    }

    public Long getId() {
        return id;
    }

    public VolunteerTask getTask() {
        return task;
    }

    public Skill getSkill() {
        return skill;
    }

    public short getMinLevel() {
        return minLevel;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public short getWeight() {
        return weight;
    }
}
