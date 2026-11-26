package ua.op.edu.ipatov.volunteer.exception;

/**
 * Помилка спроби доступу до чужого об'єкта або закритих контактних даних (HTTP 403).
 */
public class AccessPolicyViolationException extends RuntimeException {
    public AccessPolicyViolationException(String message) {
        super(message);
    }
}
