package ua.op.edu.ipatov.volunteer.domain;

/**
 * Стани верифікації підсумкового звіту про виконання завдання.
 */
public enum ReportStatus {
    SUBMITTED("Подано на перевірку"),
    VERIFIED("Підтверджено координатором"),
    NEEDS_REVISION("Потребує уточнення");

    private final String labelUk;

    ReportStatus(String labelUk) {
        this.labelUk = labelUk;
    }

    public String getLabelUk() {
        return labelUk;
    }
}
