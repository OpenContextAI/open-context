package com.opencontext.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Standard API response structure class.
 * Handles success and failure responses in a consistent format to simplify client response processing.
 * 
 * This class should only be created through static factory methods,
 * preventing direct constructor usage to ensure API response consistency.
 */
@Schema(description = "Standard API response structure")
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommonResponse<T> {

    @Schema(description = "Request success status", example = "true")
    private final boolean success;

    @Schema(description = "Response data (null on failure)")
    private final T data;

    @Schema(description = "Message to display to user", example = "Request processed successfully.")
    private final String message;

    @Schema(description = "Error code (null on success)", example = "VALIDATION_FAILED")
    private final String errorCode;

    @Schema(description = "Response creation timestamp", example = "2025-08-07T12:00:00")
    private final LocalDateTime timestamp;

    /**
     * Private constructor. Only allows instance creation through static factory methods.
     */
    private CommonResponse(boolean success, T data, String message, String errorCode, LocalDateTime timestamp) {
        this.success = success;
        this.data = data;
        this.message = message;
        this.errorCode = errorCode;
        this.timestamp = timestamp;
    }

    /**
     * Static factory method for creating success responses.
     */
    public static <T> CommonResponse<T> success(T data) {
        return new CommonResponse<>(
                true,
                data,
                "Request processed successfully.",
                null,
                LocalDateTime.now()
        );
    }

    /**
     * Static factory method for creating success responses with custom message.
     */
    public static <T> CommonResponse<T> success(T data, String message) {
        return new CommonResponse<>(
                true,
                data,
                message,
                null,
                LocalDateTime.now()
        );
    }

    /**
     * Static factory method for creating error responses.
     */
    public static <T> CommonResponse<T> error(String message, String errorCode) {
        return new CommonResponse<>(
                false,
                null,
                message,
                errorCode,
                LocalDateTime.now()
        );
    }

    /**
     * Static factory method for creating error responses from ErrorCode enum.
     */
    public static <T> CommonResponse<T> error(com.opencontext.enums.ErrorCode errorCode, String message) {
        return new CommonResponse<>(
                false,
                null,
                message,
                errorCode.getCode(),
                LocalDateTime.now()
        );
    }
}
