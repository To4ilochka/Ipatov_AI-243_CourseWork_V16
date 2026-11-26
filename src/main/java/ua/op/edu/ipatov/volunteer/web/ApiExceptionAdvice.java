package ua.op.edu.ipatov.volunteer.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ua.op.edu.ipatov.volunteer.config.RequestCorrelationFilter;
import ua.op.edu.ipatov.volunteer.exception.AccessPolicyViolationException;
import ua.op.edu.ipatov.volunteer.exception.DomainConflictException;
import ua.op.edu.ipatov.volunteer.exception.ResourceNotFoundException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Централізований обробник помилок REST API у форматі Problem Details (RFC 9457).
 * Стек викликів і службові дані конфігурації ніколи не передаються клієнту.
 */
@RestControllerAdvice(annotations = RestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionAdvice.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Перевірте коректність заповнення полів запиту");
        pd.setTitle("Помилка валідації вхідних даних");
        pd.setType(URI.create("https://volunthub.op.edu.ua/problems/validation-error"));
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        pd.setProperty("code", "VALIDATION_ERROR");
        pd.setProperty("fieldErrors", fieldErrors);
        attachRequestId(pd);
        log.warn("Validation failed on {} {}: fields={}", req.getMethod(), req.getRequestURI(), fieldErrors.keySet());
        return pd;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Ресурс не знайдено");
        pd.setType(URI.create("https://volunthub.op.edu.ua/problems/not-found"));
        pd.setProperty("code", "NOT_FOUND");
        attachRequestId(pd);
        log.info("Resource not found on {} {}: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return pd;
    }

    @ExceptionHandler(DomainConflictException.class)
    public ProblemDetail handleConflict(DomainConflictException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Конфлікт предметного правила або стану");
        pd.setType(URI.create("https://volunthub.op.edu.ua/problems/domain-conflict"));
        pd.setProperty("code", ex.getErrorCode());
        attachRequestId(pd);
        log.warn("Domain conflict [{}] on {} {}: {}", ex.getErrorCode(), req.getMethod(), req.getRequestURI(), ex.getMessage());
        return pd;
    }

    @ExceptionHandler(AccessPolicyViolationException.class)
    public ProblemDetail handleAccessViolation(AccessPolicyViolationException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        pd.setTitle("Порушення політики доступу до об'єкта");
        pd.setType(URI.create("https://volunthub.op.edu.ua/problems/access-denied"));
        pd.setProperty("code", "ACCESS_POLICY_VIOLATION");
        attachRequestId(pd);
        log.warn("Access policy violation on {} {}: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return pd;
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(org.springframework.dao.OptimisticLockingFailureException ex,
                                              HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Дані об'єкта були одночасно змінені іншим запитом. Оновіть сторінку та повторіть дію.");
        pd.setTitle("Конкурентна зміна запису");
        pd.setType(URI.create("https://volunthub.op.edu.ua/problems/concurrent-modification"));
        pd.setProperty("code", "CONCURRENT_MODIFICATION");
        attachRequestId(pd);
        log.warn("Optimistic lock conflict on {} {}: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return pd;
    }

    private void attachRequestId(ProblemDetail pd) {
        String reqId = MDC.get(RequestCorrelationFilter.MDC_KEY);
        if (reqId != null) {
            pd.setProperty("requestId", reqId);
        }
    }
}
