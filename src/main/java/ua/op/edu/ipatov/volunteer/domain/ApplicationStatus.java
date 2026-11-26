package ua.op.edu.ipatov.volunteer.domain;

/**
 * Стани опрацювання заявки волонтера на завдання.
 */
public enum ApplicationStatus {
    PENDING("Очікує розгляду"),
    APPROVED("Погоджено"),
    REJECTED("Відхилено"),
    CANCELLED("Скасовано волонтером");

    private final String labelUk;

    ApplicationStatus(String labelUk) {
        this.labelUk = labelUk;
    }

    public String getLabelUk() {
        return labelUk;
    }

    public boolean isActive() {
        return this == PENDING || this == APPROVED;
    }
}
