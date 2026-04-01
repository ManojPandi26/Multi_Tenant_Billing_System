package com.mtbs.shared.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * Structured error payload returned by {@code GlobalExceptionHandler}.
 *
 * Currently GlobalExceptionHandler builds the error shape inline using
 * ApiResponse.error(). This class makes the error shape explicit, typed,
 * and reusable — e.g. for OpenAPI documentation and test assertions.
 *
 * JSON shape (single error):
 * {
 *   "success": false,
 *   "message": "Resource not found",
 *   "errorCode": "RES_4001",
 *   "timestamp": "2026-03-26T10:00:00Z"
 * }
 *
 * JSON shape (validation error with field details):
 * {
 *   "success": false,
 *   "message": "Validation error",
 *   "errorCode": "VAL_6001",
 *   "timestamp": "2026-03-26T10:00:00Z",
 *   "fieldErrors": [
 *     { "field": "email", "message": "must be a well-formed email address" },
 *     { "field": "password", "message": "must be at least 8 characters" }
 *   ]
 * }
 *
 * MIGRATION: GlobalExceptionHandler should return ApiResponse<ErrorResponse>
 * or ErrorResponse directly. Currently it returns ApiResponse<Void> — no
 * breaking change needed until the API is versioned.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    @Builder.Default
    private boolean success = false;

    private String message;
    private String errorCode;

    @Builder.Default
    private Instant timestamp = Instant.now();

    /** Present only on validation errors (HTTP 400 from @Valid). */
    private List<FieldError> fieldErrors;

    // ── Nested DTO ────────────────────────────────────────────────────────────

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldError {
        /** The DTO field name that failed validation. */
        private String field;
        /** Human-readable validation message. */
        private String message;
    }

    // ── Factory methods ───────────────────────────────────────────────────────

    public static ErrorResponse of(String errorCode, String message) {
        return ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .build();
    }

    public static ErrorResponse withFieldErrors(String errorCode,
                                                 String message,
                                                 List<FieldError> fieldErrors) {
        return ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .fieldErrors(fieldErrors)
                .build();
    }
}