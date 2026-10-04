package com.example.api.bench;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.ErrorResponseException;

@RestController
final class FailuresController {
    @GetMapping("/api/bench/failure")
    ResponseEntity<ProblemDetail> failure(@RequestParam String kind) {
        if ("internal".equals(kind)) throw new InternalFailure(new IllegalStateException("BENCH_PRIVATE_FAILURE"));
        if (!"conflict".equals(kind)) throw new ErrorResponseException(HttpStatus.BAD_REQUEST);
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Requested inventory is unavailable");
        problem.setProperty("code", "inventory_unavailable");
        return ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    @ExceptionHandler(InternalFailure.class)
    ResponseEntity<ProblemDetail> internalFailure() {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An internal error occurred");
        problem.setProperty("code", "internal_error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    private static final class InternalFailure extends RuntimeException {
        InternalFailure(Throwable cause) { super(cause); }
    }
}
