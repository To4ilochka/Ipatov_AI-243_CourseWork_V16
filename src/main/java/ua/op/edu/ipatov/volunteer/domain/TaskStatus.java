package ua.op.edu.ipatov.volunteer.domain;

/**
 * Стани конкретного волонтерського завдання.
 */
public enum TaskStatus {
    OPEN("Відкрите"),
    IN_PROGRESS("У виконанні"),
    COMPLETED("Завершене"),
    CANCELLED("Скасоване");

    private final String labelUk;

    TaskStatus(String labelUk) {
        this.labelUk = labelUk;
    }

    public String getLabelUk() {
        return labelUk;
    }
}
