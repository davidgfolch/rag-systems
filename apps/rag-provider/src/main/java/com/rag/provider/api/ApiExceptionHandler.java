package com.rag.provider.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.rag.provider.services.ProviderBusyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Maps invalid provider requests to HTTP 400 and saturation to HTTP 429 with a
 * {@code Retry-After} header so callers can distinguish permanent mistakes
 * from transient overload.
 */
@ControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final long MIN_RETRY_AFTER_SECONDS = 1;

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    String handleBadRequest(IllegalArgumentException e) {
        log.warn("Bad provider request rejected: {}", e.getMessage());
        return e.getMessage();
    }

    @ExceptionHandler(ProviderBusyException.class)
    @ResponseBody
    ResponseEntity<String> handleBusy(ProviderBusyException e) {
        var seconds = Math.max(MIN_RETRY_AFTER_SECONDS, e.retryAfter().toSeconds());
        log.warn("Provider request rejected as busy (retry after {}s): {}", seconds, e.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
                .body(e.getMessage());
    }
}
