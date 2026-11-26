package ua.op.edu.ipatov.volunteer.domain;

import jakarta.persistence.*;

/**
 * Зв'язок волонтера з навичкою та рівнем володіння (1 — базовий, 2 — впевнений, 3 — експертний).
 */
@Entity
@Table(name = "volunteer_skills", uniqueConstraints = {
        @UniqueConstraint(name = "uq_volunteer_skill", columnNames = {"volunteer_id", "skill_id"})
})
public class VolunteerSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "volunteer_id", nullable = false)
    private UserAccount volunteer;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(name = "proficiency_level", nullable = false)
    private short proficiencyLevel;

    @Column(length = 200)
    private String notes;

    public VolunteerSkill() {
    }

    public VolunteerSkill(UserAccount volunteer, Skill skill, short proficiencyLevel, String notes) {
        this.volunteer = volunteer;
        this.skill = skill;
        this.proficiencyLevel = proficiencyLevel;
        this.notes = notes;
    }

    public Long getId() {
        return id;
    }

    public UserAccount getVolunteer() {
        return volunteer;
    }

    public Skill getSkill() {
        return skill;
    }

    public short getProficiencyLevel() {
        return proficiencyLevel;
    }

    public void setProficiencyLevel(short proficiencyLevel) {
        this.proficiencyLevel = proficiencyLevel;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
