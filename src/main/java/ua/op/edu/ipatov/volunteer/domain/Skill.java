package ua.op.edu.ipatov.volunteer.domain;

import jakarta.persistence.*;

/**
 * Довідник компетентностей і практичних навичок для підбору завдань.
 */
@Entity
@Table(name = "skills")
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 48)
    private String code;

    @Column(nullable = false, unique = true, length = 100)
    private String title;

    @Column(nullable = false, length = 60)
    private String category;

    public Skill() {
    }

    public Skill(String code, String title, String category) {
        this.code = code;
        this.title = title;
        this.category = category;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
    }
}
