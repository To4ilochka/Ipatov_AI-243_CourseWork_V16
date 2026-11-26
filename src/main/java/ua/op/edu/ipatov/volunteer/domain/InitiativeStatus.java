package ua.op.edu.ipatov.volunteer.domain;

/**
 * Стани життєвого циклу волонтерської ініціативи.
 */
public enum InitiativeStatus {
    DRAFT("Чернетка"),
    ACTIVE("Активна"),
    COMPLETED("Завершена"),
    ARCHIVED("Архівована");

    private final String labelUk;

    InitiativeStatus(String labelUk) {
        this.labelUk = labelUk;
    }

    public String getLabelUk() {
        return labelUk;
    }
}
