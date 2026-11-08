package com.rag.provider.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.rag.provider.services.ProviderBusyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Maps invalid provider requests to HTTP 400 and saturation to HTTP 503 so
 * callers can distinguish permanent mistakes from transient overload.
 */
@ControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    String handleBadRequest(IllegalArgumentException e) {
        log.warn("Bad provider request rejected: {}", e.getMessage());
        return e.getMessage();
    }

    @ExceptionHandler(ProviderBusyException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    @ResponseBody
    String handleBusy(ProviderBusyException e) {
        log.warn("Provider request rejected as busy: {}", e.getMessage());
        return e.getMessage();
    }
}