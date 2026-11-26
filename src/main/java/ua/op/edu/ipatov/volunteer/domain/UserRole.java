package ua.op.edu.ipatov.volunteer.domain;

/**
 * Ролі облікових записів платформи.
 */
public enum UserRole {
    VOLUNTEER("Волонтер"),
    COORDINATOR("Координатор");

    private final String labelUk;

    UserRole(String labelUk) {
        this.labelUk = labelUk;
    }

    public String getLabelUk() {
        return labelUk;
    }
}
