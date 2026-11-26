package ua.op.edu.ipatov.volunteer.domain;

/**
 * Рівень пріоритетності та терміновості завдання.
 */
public enum TaskUrgency {
    NORMAL("Планове", 0.40),
    HIGH("Високий пріоритет", 0.70),
    CRITICAL("Термінове", 1.00);

    private final String labelUk;
    private final double factor;

    TaskUrgency(String labelUk, double factor) {
        this.labelUk = labelUk;
        this.factor = factor;
    }

    public String getLabelUk() {
        return labelUk;
    }

    public double getFactor() {
        return factor;
    }
}
