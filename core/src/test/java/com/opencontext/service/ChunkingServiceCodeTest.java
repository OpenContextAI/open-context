package com.opencontext.service;

import com.opencontext.dto.StructuredChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ChunkingService code handling functionality.
 * Tests the enhanced chunking behavior for programming language content.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChunkingService Code Handling Tests")
class ChunkingServiceCodeTest {

    @InjectMocks
    private ChunkingService chunkingService;

    private UUID testDocumentId;

    @BeforeEach
    void setUp() {
        testDocumentId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should preserve complete Java method in single chunk")
    void shouldPreserveCompleteJavaMethod() {
        // Given: A Java method that would normally be split across multiple chunks
        String javaCode = """
            public static void main(String[] args) {
                int sum = numbers.stream()
                    .mapToInt(Integer::intValue)
                    .sum();
                System.out.println("Sum: " + sum);
            }""";

        List<Map<String, Object>> parsedElements = List.of(
            createCodeElement("NarrativeText", javaCode, "java")
        );

        // When
        List<StructuredChunk> chunks = chunkingService.createChunks(testDocumentId, parsedElements);

        // Then: Should create one complete chunk, not split by lines
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getContent()).contains("public static void main");
        assertThat(chunks.get(0).getContent()).contains("System.out.println");
        assertThat(chunks.get(0).getContent()).contains("sum();");
        // Should preserve the complete method structure
        assertThat(chunks.get(0).getContent()).isEqualTo(javaCode);
    }

    @Test
    @DisplayName("Should preserve complete Python function in single chunk")
    void shouldPreserveCompletePythonFunction() {
        // Given: A Python function
        String pythonCode = """
            def calculate_sum(numbers):
                total = 0
                for num in numbers:
                    total += num
                return total""";

        List<Map<String, Object>> parsedElements = List.of(
            createCodeElement("NarrativeText", pythonCode, "python")
        );

        // When
        List<StructuredChunk> chunks = chunkingService.createChunks(testDocumentId, parsedElements);

        // Then: Should preserve complete function
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getContent()).contains("def calculate_sum");
        assertThat(chunks.get(0).getContent()).contains("return total");
        assertThat(chunks.get(0).getContent()).isEqualTo(pythonCode);
    }

    @Test
    @DisplayName("Should group related imports with code for Java")
    void shouldGroupRelatedImportsWithJavaCode() {
        // Given: Import statements followed by class definition
        String importCode = "import java.util.List;\nimport java.util.stream.Collectors;";
        String classCode = """
            public class Example {
                private List<String> items;
                public void process() {
                    items.stream().collect(Collectors.toList());
                }
            }""";

        List<Map<String, Object>> parsedElements = List.of(
            createCodeElement("NarrativeText", importCode, "java"),
            createCodeElement("NarrativeText", classCode, "java")
        );

        // When
        List<StructuredChunk> chunks = chunkingService.createChunks(testDocumentId, parsedElements);

        // Then: Should group related imports and class
        assertThat(chunks).hasSize(2); // Two logical units: imports and class
        
        // First chunk should contain imports
        assertThat(chunks.get(0).getContent()).contains("import java.util.List");
        assertThat(chunks.get(0).getContent()).contains("import java.util.stream.Collectors");
        
        // Second chunk should contain complete class
        assertThat(chunks.get(1).getContent()).contains("public class Example");
        assertThat(chunks.get(1).getContent()).contains("Collectors.toList()");
    }

    @Test
    @DisplayName("Should handle long code by splitting at logical boundaries")
    void shouldSplitLongCodeAtLogicalBoundaries() {
        // Given: A long Java class with multiple methods
        String longJavaCode = """
            public class LongExample {
                public void methodOne() {
                    // This is method one
                    System.out.println("Method one");
                    for (int i = 0; i < 100; i++) {
                        System.out.println("Iteration: " + i);
                    }
                }
                
                public void methodTwo() {
                    // This is method two
                    System.out.println("Method two");
                    List<String> items = new ArrayList<>();
                    items.add("item1");
                    items.add("item2");
                    return items;
                }
                
                public void methodThree() {
                    // This is method three
                    System.out.println("Method three");
                    Map<String, Object> data = new HashMap<>();
                    data.put("key1", "value1");
                    data.put("key2", "value2");
                    return data;
                }
            }""";

        List<Map<String, Object>> parsedElements = List.of(
            createCodeElement("NarrativeText", longJavaCode, "java")
        );

        // When
        List<StructuredChunk> chunks = chunkingService.createChunks(testDocumentId, parsedElements);

        // Then: Should split by methods, not arbitrary character boundaries
        // Each chunk should contain complete methods
        for (StructuredChunk chunk : chunks) {
            String content = chunk.getContent();
            // Each chunk should have balanced braces if it contains method definitions
            if (content.contains("public void method")) {
                long openBraces = content.chars().filter(ch -> ch == '{').count();
                long closeBraces = content.chars().filter(ch -> ch == '}').count();
                // Should have balanced braces for complete methods
                if (openBraces > 0) {
                    assertThat(openBraces).isEqualTo(closeBraces);
                }
            }
        }
    }

    @Test
    @DisplayName("Should maintain original behavior for non-code content")
    void shouldMaintainOriginalBehaviorForNonCode() {
        // Given: Regular text content (not code)
        String regularText = "This is a regular paragraph of text that should be processed " +
                           "using the original chunking logic without any special code handling. " +
                           "It should split based on character limits and word boundaries.";

        List<Map<String, Object>> parsedElements = List.of(
            Map.of(
                "type", "NarrativeText",
                "text", regularText,
                "metadata", Map.of()
            )
        );

        // When
        List<StructuredChunk> chunks = chunkingService.createChunks(testDocumentId, parsedElements);

        // Then: Should use original chunking behavior
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getContent()).isEqualTo(regularText);
    }

    @Test
    @DisplayName("Should handle mixed content types correctly")
    void shouldHandleMixedContentTypes() {
        // Given: Mixed content with both code and regular text
        String regularText = "Here is some documentation explaining the code:";
        String javaCode = """
            public void example() {
                System.out.println("Hello, world!");
            }""";
        String moreText = "This concludes the code example.";

        List<Map<String, Object>> parsedElements = List.of(
            Map.of("type", "NarrativeText", "text", regularText, "metadata", Map.of()),
            createCodeElement("NarrativeText", javaCode, "java"),
            Map.of("type", "NarrativeText", "text", moreText, "metadata", Map.of())
        );

        // When
        List<StructuredChunk> chunks = chunkingService.createChunks(testDocumentId, parsedElements);

        // Then: Should handle each type appropriately
        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).getContent()).isEqualTo(regularText);
        assertThat(chunks.get(1).getContent()).contains("public void example");
        assertThat(chunks.get(2).getContent()).isEqualTo(moreText);
    }

    /**
     * Helper method to create a code element with language metadata
     */
    private Map<String, Object> createCodeElement(String type, String text, String language) {
        return Map.of(
            "type", type,
            "text", text,
            "metadata", Map.of(
                "filename", "test." + getFileExtension(language),
                "language", language
            )
        );
    }

    private String getFileExtension(String language) {
        return switch (language.toLowerCase()) {
            case "java" -> "java";
            case "python" -> "py";
            case "javascript" -> "js";
            case "typescript" -> "ts";
            case "c++" -> "cpp";
            case "c" -> "c";
            case "go" -> "go";
            case "rust" -> "rs";
            default -> "txt";
        };
    }
}