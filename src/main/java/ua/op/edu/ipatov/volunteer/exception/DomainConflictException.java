package ua.op.edu.ipatov.volunteer.exception;

/**
 * Помилка порушення предметного інваріанта, дублювання заявки або вичерпання ліміту (HTTP 409).
 */
public class DomainConflictException extends RuntimeException {
    private final String errorCode;

    public DomainConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
