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
 * Tests to understand current chunking behavior and identify the issue.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChunkingService Current Behavior Analysis")
class ChunkingServiceCurrentBehaviorTest {

    @InjectMocks
    private ChunkingService chunkingService;

    private UUID testDocumentId;

    @BeforeEach
    void setUp() {
        testDocumentId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Show how current chunking breaks large code at word boundaries")
    void showCurrentChunkingBreaksLargeCode() {
        // Given: A long Java method that exceeds MAX_CHUNK_SIZE (1000 characters)
        StringBuilder longJavaCode = new StringBuilder();
        longJavaCode.append("public static void main(String[] args) {\n");
        longJavaCode.append("    // This is a long method that will exceed 1000 characters\n");
        longJavaCode.append("    int sum = numbers.stream()\n");
        longJavaCode.append("        .mapToInt(Integer::intValue)\n");
        longJavaCode.append("        .sum();\n");
        longJavaCode.append("    System.out.println(\"Sum: \" + sum);\n");
        
        // Add more content to exceed the 1000 character limit
        for (int i = 0; i < 20; i++) {
            longJavaCode.append("    System.out.println(\"This is line ").append(i)
                    .append(" of additional content to make this method very long and exceed the maximum chunk size limit\");\n");
        }
        
        longJavaCode.append("    // More processing logic\n");
        longJavaCode.append("    List<String> items = new ArrayList<>();\n");
        for (int i = 0; i < 10; i++) {
            longJavaCode.append("    items.add(\"item").append(i).append("\");\n");
        }
        longJavaCode.append("    return items.stream().collect(Collectors.toList());\n");
        longJavaCode.append("}\n");

        String code = longJavaCode.toString();
        System.out.println("Code length: " + code.length() + " characters");
        
        List<Map<String, Object>> parsedElements = List.of(
            Map.of(
                "type", "NarrativeText",
                "text", code,
                "metadata", Map.of()
            )
        );

        // When
        List<StructuredChunk> chunks = chunkingService.createChunks(testDocumentId, parsedElements);

        // Then: Show current behavior
        System.out.println("Number of chunks created: " + chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            String content = chunks.get(i).getContent();
            System.out.println("Chunk " + i + " length: " + content.length() + " characters");
            System.out.println("Chunk " + i + " content preview: " + content.substring(0, Math.min(100, content.length())));
            System.out.println("Chunk " + i + " ends with: " + content.substring(Math.max(0, content.length() - 50)));
            System.out.println("---");
        }

        // Current behavior: should split at word boundaries, potentially breaking method structure
        if (chunks.size() > 1) {
            System.out.println("ISSUE CONFIRMED: Code was split into multiple chunks");
            
            // Check if any chunk has unbalanced braces
            for (int i = 0; i < chunks.size(); i++) {
                String content = chunks.get(i).getContent();
                long openBraces = content.chars().filter(ch -> ch == '{').count();
                long closeBraces = content.chars().filter(ch -> ch == '}').count();
                
                if (openBraces != closeBraces) {
                    System.out.println("Chunk " + i + " has unbalanced braces: " + openBraces + " open, " + closeBraces + " close");
                }
            }
        }
        
        // This test documents current behavior - it may pass or fail depending on implementation
        assertThat(chunks).isNotEmpty();
    }

    @Test
    @DisplayName("Test line-by-line code splitting problem described in issue")
    void testLineByLineCodeSplittingProblem() {
        // Given: The exact example from the issue description
        String javaCodeWithLineBreaks = """
            public static void main(String[] args) {
            int sum = numbers.stream()
            .mapToInt(Integer::intValue)
            .sum();
            System.out.println("Sum: " + sum);
            }""";

        // Split this into separate parsed elements to simulate line-by-line parsing
        String[] lines = javaCodeWithLineBreaks.split("\n");
        List<Map<String, Object>> parsedElements = List.of();
        java.util.List<Map<String, Object>> elementList = new java.util.ArrayList<>();
        
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                elementList.add(Map.of(
                    "type", "NarrativeText",
                    "text", line.trim(),
                    "metadata", Map.of()
                ));
            }
        }
        
        parsedElements = elementList;

        // When
        List<StructuredChunk> chunks = chunkingService.createChunks(testDocumentId, parsedElements);

        // Then: Show how each line becomes a separate chunk
        System.out.println("Number of elements: " + parsedElements.size());
        System.out.println("Number of chunks created: " + chunks.size());
        
        for (int i = 0; i < chunks.size(); i++) {
            System.out.println("Chunk " + i + ": \"" + chunks.get(i).getContent() + "\"");
        }
        
        // This demonstrates the problem described in the issue
        assertThat(chunks.size()).isGreaterThan(1);
        
        // Verify individual lines become separate chunks
        boolean hasFragmentedCode = chunks.stream()
                .anyMatch(chunk -> {
                    String content = chunk.getContent();
                    return content.equals("public static void main(String[] args) {") ||
                           content.equals("int sum = numbers.stream()") ||
                           content.equals(".mapToInt(Integer::intValue)") ||
                           content.equals(".sum();") ||
                           content.equals("System.out.println(\"Sum: \" + sum);") ||
                           content.equals("}");
                });
        
        assertThat(hasFragmentedCode).isTrue();
        System.out.println("CONFIRMED: Code is fragmented into individual lines/chunks");
    }

    @Test
    @DisplayName("Test regular text chunking behavior for comparison")
    void testRegularTextChunkingBehavior() {
        // Given: Regular text content
        StringBuilder longText = new StringBuilder();
        longText.append("This is a regular paragraph of text. ");
        for (int i = 0; i < 50; i++) {
            longText.append("This is sentence ").append(i).append(" in a long paragraph. ");
        }
        
        String text = longText.toString();
        System.out.println("Text length: " + text.length() + " characters");
        
        List<Map<String, Object>> parsedElements = List.of(
            Map.of(
                "type", "NarrativeText",
                "text", text,
                "metadata", Map.of()
            )
        );

        // When
        List<StructuredChunk> chunks = chunkingService.createChunks(testDocumentId, parsedElements);

        // Then: Show how regular text is chunked
        System.out.println("Regular text - Number of chunks: " + chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            String content = chunks.get(i).getContent();
            System.out.println("Text chunk " + i + " length: " + content.length());
            System.out.println("Text chunk " + i + " starts: " + content.substring(0, Math.min(50, content.length())));
            System.out.println("Text chunk " + i + " ends: " + content.substring(Math.max(0, content.length() - 50)));
        }
        
        assertThat(chunks).isNotEmpty();
    }
}