package com.example.api.notes;

import cn.code91.facility.web.exception.FacilityHttpErrors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice(assignableTypes = NotesController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class NotesErrors {
    private final FacilityHttpErrors errors;
    NotesErrors(FacilityHttpErrors errors) { this.errors = errors; }
    @ExceptionHandler({org.springframework.dao.DataAccessException.class, org.springframework.transaction.TransactionException.class})
    ResponseEntity<Object> persistence(RuntimeException failure, WebRequest request) {
        // DataAccessException diagnostics include SQL and database detail, which may contain submitted values.
        boolean unavailable = failure instanceof org.springframework.dao.TransientDataAccessException
                || failure instanceof org.springframework.dao.DataAccessResourceFailureException
                || failure instanceof org.springframework.transaction.CannotCreateTransactionException
                || failure instanceof org.springframework.transaction.TransactionTimedOutException
                // Spring's default SQL-state translator leaves PostgreSQL lock_not_available uncategorized.
                || (failure instanceof org.springframework.jdbc.UncategorizedSQLException sql
                    && "55P03".equals(sql.getSQLException().getSQLState()));
        var result = errors.response(new ErrorResponseException(unavailable ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.INTERNAL_SERVER_ERROR), request);
        if (result != null && result.getBody() instanceof ProblemDetail problem) problem.setProperty("code", unavailable ? "persistence_unavailable" : "persistence_failed");
        return result;
    }
    @ExceptionHandler(NotesFailure.class) ResponseEntity<Object> business(NotesFailure failure, WebRequest request) {
        var status = switch (failure.code()) {
            case "note_not_found" -> HttpStatus.NOT_FOUND;
            case "command_receipt_expired" -> HttpStatus.GONE;
            case "invalid_page", "invalid_note", "invalid_actor", "invalid_command_key" -> HttpStatus.BAD_REQUEST;
            case "workspace_forbidden" -> HttpStatus.FORBIDDEN;
            case "workspace_command_limit" -> HttpStatus.TOO_MANY_REQUESTS;
            case "note_slug_conflict", "command_conflict", "command_processing" -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        var safe = new ErrorResponseException(status);
        if (failure.code().equals("command_processing")) safe.getHeaders().set(HttpHeaders.RETRY_AFTER, "1");
        var result = errors.response(safe, request);
        if (result != null && result.getBody() instanceof ProblemDetail problem) problem.setProperty("code", failure.code());
        return result;
    }
}
