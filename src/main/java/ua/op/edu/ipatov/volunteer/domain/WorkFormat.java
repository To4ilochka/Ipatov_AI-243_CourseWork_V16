package ua.op.edu.ipatov.volunteer.domain;

/**
 * Формат залучення волонтера до виконання завдання.
 */
public enum WorkFormat {
    ONSITE("Очно на локації"),
    REMOTE("Дистанційно"),
    HYBRID("Змішаний формат");

    private final String labelUk;

    WorkFormat(String labelUk) {
        this.labelUk = labelUk;
    }

    public String getLabelUk() {
        return labelUk;
    }
}
