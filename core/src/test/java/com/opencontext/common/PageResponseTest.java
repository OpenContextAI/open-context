package com.opencontext.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PageResponse class.
 */
class PageResponseTest {

    @Test
    @DisplayName("Should return correct information when creating PageResponse from Page object")
    void fromPage_shouldReturnCorrectInformation() {
        // Given
        List<String> content = List.of("item1", "item2", "item3");
        PageRequest pageRequest = PageRequest.of(0, 10);
        Page<String> page = new PageImpl<>(content, pageRequest, 25);

        // When
        PageResponse<String> response = PageResponse.from(page);

        // Then
        assertThat(response.getContent()).isEqualTo(content);
        assertThat(response.getPage()).isEqualTo(0);
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.getTotalElements()).isEqualTo(25);
        assertThat(response.getTotalPages()).isEqualTo(3);
        assertThat(response.isFirst()).isTrue();
        assertThat(response.isLast()).isFalse();
    }

    @Test
    @DisplayName("Should return correct information when creating PageResponse from Page object with custom content")
    void fromPageWithCustomContent_shouldReturnCorrectInformation() {
        // Given
        List<String> originalContent = List.of("item1", "item2", "item3");
        List<String> customContent = List.of("custom1", "custom2");
        PageRequest pageRequest = PageRequest.of(1, 10);
        Page<String> page = new PageImpl<>(originalContent, pageRequest, 25);

        // When
        PageResponse<String> response = PageResponse.from(page, customContent);

        // Then
        assertThat(response.getContent()).isEqualTo(customContent);
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.getTotalElements()).isEqualTo(25);
        assertThat(response.getTotalPages()).isEqualTo(3);
        assertThat(response.isFirst()).isFalse();
        assertThat(response.isLast()).isFalse();
    }
}
