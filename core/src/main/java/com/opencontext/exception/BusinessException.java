package com.opencontext.exception;

import com.opencontext.enums.ErrorCode;
import lombok.Getter;

/**
 * Class representing predictable exceptions that occur in business logic.
 * This exception is used to provide clear error information to clients.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * Creates BusinessException using ErrorCode.
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * Creates BusinessException using ErrorCode and custom message.
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Creates BusinessException using ErrorCode, custom message, and cause exception.
     */
    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Creates BusinessException using ErrorCode and cause exception.
     */
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}

