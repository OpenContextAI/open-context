package com.opencontext.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CommonResponse class.
 */
class CommonResponseTest {

    @Test
    @DisplayName("Success response creation should return correct structure")
    void success_shouldReturnCorrectStructure() {
        // Given
        String testData = "test data";

        // When
        CommonResponse<String> response = CommonResponse.success(testData);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(testData);
        assertThat(response.getMessage()).isEqualTo("Request processed successfully.");
        assertThat(response.getErrorCode()).isNull();
        assertThat(response.getTimestamp()).isNotNull();
        assertThat(response.getTimestamp()).isInstanceOf(LocalDateTime.class);
    }

    @Test
    @DisplayName("Success response creation with custom message should return custom message")
    void successWithCustomMessage_shouldReturnCustomMessage() {
        // Given
        String testData = "test data";
        String customMessage = "Custom success message";

        // When
        CommonResponse<String> response = CommonResponse.success(testData, customMessage);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(testData);
        assertThat(response.getMessage()).isEqualTo(customMessage);
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    @DisplayName("Error response creation should return correct structure")
    void error_shouldReturnCorrectStructure() {
        // Given
        String errorMessage = "Test error message";
        String errorCode = "TEST_ERROR";

        // When
        CommonResponse<String> response = CommonResponse.error(errorMessage, errorCode);

        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getData()).isNull();
        assertThat(response.getMessage()).isEqualTo(errorMessage);
        assertThat(response.getErrorCode()).isEqualTo(errorCode);
        assertThat(response.getTimestamp()).isNotNull();
        assertThat(response.getTimestamp()).isInstanceOf(LocalDateTime.class);
    }
}

