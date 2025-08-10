package com.opencontext.exception;

import com.opencontext.common.CommonResponse;
import com.opencontext.enums.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler.
 * Converts exceptions from all controllers into consistent CommonResponse format.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles business logic related exceptions.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<CommonResponse<Void>> handleBusinessException(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        log.warn("BusinessException occurred: code={}, message={}", errorCode.getCode(), ex.getMessage());
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(CommonResponse.error(ex.getMessage(), errorCode.getCode()));
    }

    /**
     * Handles @Valid annotation validation failures.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
        String firstErrorMessage = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.warn("Validation failed: {}", firstErrorMessage);
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(CommonResponse.error(firstErrorMessage, errorCode.getCode()));
    }

    /**
     * Handles all unexpected server errors.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponse<Void>> handleAllUncaughtException(Exception ex) {
        ErrorCode errorCode = ErrorCode.UNKNOWN_SERVER_ERROR;
        log.error("Unknown server error occurred", ex);
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(CommonResponse.error(errorCode.getMessage(), errorCode.getCode()));
    }
}

