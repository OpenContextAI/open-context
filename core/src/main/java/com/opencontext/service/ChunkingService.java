package com.opencontext.service;

import com.opencontext.dto.StructuredChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkingService {

    private static final int MAX_CHUNK_SIZE = 1000; // Maximum chunk size (number of characters)
    private static final int CHUNK_OVERLAP = 200;   // Overlap size between chunks
    private static final int MIN_CODE_CHUNK_SIZE = 100; // Minimum chunk size for code to prevent tiny fragments
    private static final int MAX_CODE_CHUNK_SIZE = 2000; // Larger max size for code to preserve semantic units

    /**
     * Converts parsed elements into structured chunks.
     * 
     * @param documentId Document ID
     * @param parsedElements List of elements parsed by the Unstructured API
     * @return List of structured chunks
     */
    public List<StructuredChunk> createChunks(UUID documentId, List<Map<String, Object>> parsedElements) {
        long startTime = System.currentTimeMillis();
        int totalElements = parsedElements.size();
        
        log.info("🧩 [CHUNKING] Starting chunking process: documentId={}, elements={}", documentId, totalElements);

        List<StructuredChunk> chunks = new ArrayList<>();
        Stack<ChunkContext> hierarchyStack = new Stack<>();
        int chunkIndex = 0;
        int processedElements = 0;
        
        log.debug("📄 [CHUNKING] Processing {} elements for hierarchical chunking", totalElements);

        for (Map<String, Object> element : parsedElements) {
            processedElements++;
            String elementType = (String) element.get("type");
            String text = (String) element.get("text");
            
            if (processedElements % 50 == 0 || processedElements == totalElements) {
                log.debug("📊 [CHUNKING] Progress: {}/{} elements processed", processedElements, totalElements);
            }
            
            if (text == null || text.trim().isEmpty()) {
                log.debug("⚠️ [CHUNKING] Skipping empty element: type={}", elementType);
                continue;
            }

            // 요소 타입에 따른 처리
            switch (elementType) {
                case "Title" -> {
                    // 새로운 섹션 시작 - 계층 구조 업데이트
                    ChunkContext titleContext = new ChunkContext(text, 1, null);
                    hierarchyStack.clear();
                    hierarchyStack.push(titleContext);
                    
                    // 제목을 별도 청크로 생성
                    chunks.add(createChunk(documentId, chunkIndex++, text, titleContext, element));
                }
                case "Header" -> {
                    // 헤더 레벨 결정 (metadata에서 추출 또는 기본값 사용)
                    int level = determineHeaderLevel(element);
                    
                    // 계층 구조 조정
                    adjustHierarchyStack(hierarchyStack, level);
                    
                    ChunkContext headerContext = new ChunkContext(text, level, 
                            hierarchyStack.isEmpty() ? null : hierarchyStack.peek());
                    hierarchyStack.push(headerContext);
                    
                    // 헤더를 별도 청크로 생성
                    chunks.add(createChunk(documentId, chunkIndex++, text, headerContext, element));
                }
                case "NarrativeText", "ListItem", "Table" -> {
                    // 현재 계층 구조에서 내용 청크 생성
                    ChunkContext currentContext = hierarchyStack.isEmpty() ? null : hierarchyStack.peek();
                    
                    // Check if this is code content
                    if (isCodeContent(text, element)) {
                        // Use code-aware chunking
                        List<String> codeChunks = splitCodeText(text, element);
                        for (String codeChunk : codeChunks) {
                            chunks.add(createChunk(documentId, chunkIndex++, codeChunk, currentContext, element));
                        }
                    } else {
                        // 긴 텍스트는 여러 청크로 분할
                        List<String> textChunks = splitLongText(text);
                        for (String textChunk : textChunks) {
                            chunks.add(createChunk(documentId, chunkIndex++, textChunk, currentContext, element));
                        }
                    }
                }
                default -> {
                    // 기타 요소들은 현재 컨텍스트에서 처리
                    ChunkContext currentContext = hierarchyStack.isEmpty() ? null : hierarchyStack.peek();
                    chunks.add(createChunk(documentId, chunkIndex++, text, currentContext, element));
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        int finalChunkCount = chunks.size();
        
        log.info("🎉 [CHUNKING] Chunking completed successfully: documentId={}, elements={}, chunks={}, duration={}ms", 
                documentId, totalElements, finalChunkCount, duration);
        
        // 청크 통계 로깅
        if (finalChunkCount > 0) {
            double avgChunkLength = chunks.stream()
                    .mapToInt(c -> c.getContent().length())
                    .average()
                    .orElse(0.0);
            
            int maxHierarchyLevel = chunks.stream()
                    .mapToInt(c -> c.getHierarchyLevel() != null ? c.getHierarchyLevel() : 0)
                    .max()
                    .orElse(0);
            
            log.debug("📊 [CHUNKING] Statistics: avgChunkLength={}, maxHierarchyLevel={}, maxChunkSize={}", 
                    Math.round(avgChunkLength), maxHierarchyLevel, MAX_CHUNK_SIZE);
        }
        
        return chunks;
    }

    /**
     * StructuredChunk 객체를 생성합니다.
     */
    private StructuredChunk createChunk(UUID documentId, int chunkIndex, String text, 
                                      ChunkContext context, Map<String, Object> element) {
        return StructuredChunk.builder()
                .documentId(documentId.toString())
                .chunkId(generateChunkId(documentId, chunkIndex))
                .content(text)
                .title(context != null ? context.title : null)
                .hierarchyLevel(context != null ? context.level : 0)
                .parentChunkId(context != null && context.parent != null ? 
                        generateChunkId(documentId, context.parent.chunkIndex) : null)
                .elementType((String) element.get("type"))
                .metadata(extractMetadata(element))
                .build();
    }

    /**
     * 청크 ID를 생성합니다.
     */
    private String generateChunkId(UUID documentId, int chunkIndex) {
        return documentId.toString() + "-chunk-" + chunkIndex;
    }

    /**
     * Determines if the given text content is code based on file extension or content patterns.
     */
    private boolean isCodeContent(String text, Map<String, Object> element) {
        // Check metadata for file information
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) element.get("metadata");
        
        if (metadata != null) {
            // Check for filename with code extensions
            String filename = (String) metadata.get("filename");
            if (filename != null && isCodeFileExtension(filename)) {
                log.debug("📝 [CHUNKING] Detected code content from filename: {}", filename);
                return true;
            }
            
            // Check for explicit language metadata
            String language = (String) metadata.get("language");
            if (language != null && isCodeLanguage(language)) {
                log.debug("📝 [CHUNKING] Detected code content from language metadata: {}", language);
                return true;
            }
        }
        
        // Check content patterns for code indicators
        if (hasCodePatterns(text)) {
            log.debug("📝 [CHUNKING] Detected code content from content patterns");
            return true;
        }
        
        return false;
    }
    
    /**
     * Checks if a filename has a code file extension.
     */
    private boolean isCodeFileExtension(String filename) {
        String extension = getFileExtension(filename).toLowerCase();
        return switch (extension) {
            case "java", "py", "js", "ts", "cpp", "c", "h", "hpp", "go", "rs", 
                 "rb", "php", "cs", "swift", "kt", "scala", "sh", "sql", "r", 
                 "m", "mm", "pl", "pm", "lua", "dart", "jl", "f90", "f95", 
                 "f03", "f08", "pas", "pp", "inc" -> true;
            default -> false;
        };
    }
    
    /**
     * Checks if a language string indicates code content.
     */
    private boolean isCodeLanguage(String language) {
        String lang = language.toLowerCase();
        return switch (lang) {
            case "java", "python", "javascript", "typescript", "c++", "c", "go", 
                 "rust", "ruby", "php", "csharp", "c#", "swift", "kotlin", 
                 "scala", "shell", "bash", "sql", "r", "objective-c", "perl", 
                 "lua", "dart", "julia", "fortran", "pascal" -> true;
            default -> false;
        };
    }
    
    /**
     * Detects code patterns in text content.
     */
    private boolean hasCodePatterns(String text) {
        // Common code patterns across languages
        String[] codePatterns = {
            // Function/method declarations
            "public\\s+\\w+\\s+\\w+\\s*\\(",  // Java public methods
            "private\\s+\\w+\\s+\\w+\\s*\\(", // Java private methods
            "def\\s+\\w+\\s*\\(",             // Python functions
            "function\\s+\\w+\\s*\\(",        // JavaScript functions
            "\\w+\\s*\\(.*\\)\\s*\\{",        // General function with braces
            
            // Class declarations
            "class\\s+\\w+",                  // Class definitions
            "public\\s+class\\s+\\w+",        // Java class
            
            // Import/include statements
            "import\\s+[\\w\\.]+",            // Java/Python imports
            "#include\\s*<[^>]+>",            // C/C++ includes
            "using\\s+\\w+",                  // C# using
            
            // Control structures
            "if\\s*\\([^)]+\\)\\s*\\{",       // If statements with braces
            "for\\s*\\([^)]+\\)\\s*\\{",      // For loops with braces
            "while\\s*\\([^)]+\\)\\s*\\{",    // While loops with braces
            
            // Variable declarations with types
            "\\w+\\s+\\w+\\s*=",              // Type variable = value
            "\\w+\\[\\]\\s+\\w+",             // Array declarations
            
            // Common programming punctuation patterns
            "\\{[^}]*\\}",                    // Code blocks
            ";$",                             // Statement endings
            "\\->|=>",                        // Arrow functions/lambdas
        };
        
        for (String pattern : codePatterns) {
            if (text.matches(".*" + pattern + ".*")) {
                return true;
            }
        }
        
        // Check for high density of programming characters
        long programmingChars = text.chars()
                .filter(c -> c == '{' || c == '}' || c == '(' || c == ')' || 
                           c == '[' || c == ']' || c == ';' || c == '=' || c == ':')
                .count();
        
        double programmingCharRatio = (double) programmingChars / text.length();
        return programmingCharRatio > 0.05; // 5% threshold for programming character density
    }
    
    /**
     * Extracts file extension from filename.
     */
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1) : "";
    }
    
    /**
     * Splits code text while preserving semantic units and syntactic completeness.
     */
    private List<String> splitCodeText(String text, Map<String, Object> element) {
        // If the code is already within acceptable size limits, return as is
        if (text.length() <= MAX_CODE_CHUNK_SIZE) {
            return List.of(text);
        }
        
        log.debug("📝 [CHUNKING] Splitting large code text: {} characters", text.length());
        
        // Try to split by logical code boundaries
        List<String> chunks = splitByCodeBoundaries(text);
        
        // If chunks are still too large, use enhanced text splitting with code awareness
        List<String> finalChunks = new ArrayList<>();
        for (String chunk : chunks) {
            if (chunk.length() <= MAX_CODE_CHUNK_SIZE) {
                finalChunks.add(chunk);
            } else {
                finalChunks.addAll(splitLongCodeChunk(chunk));
            }
        }
        
        // Filter out chunks that are too small unless they're complete statements
        return finalChunks.stream()
                .filter(chunk -> chunk.length() >= MIN_CODE_CHUNK_SIZE || isCompleteStatement(chunk))
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Splits code by logical boundaries like method definitions, class boundaries, etc.
     */
    private List<String> splitByCodeBoundaries(String text) {
        List<String> chunks = new ArrayList<>();
        String[] lines = text.split("\n");
        StringBuilder currentChunk = new StringBuilder();
        int braceLevel = 0;
        boolean inFunction = false;
        boolean inClass = false;
        
        for (String line : lines) {
            String trimmedLine = line.trim();
            
            // Track brace levels for balanced splitting
            for (char c : line.toCharArray()) {
                if (c == '{') braceLevel++;
                else if (c == '}') braceLevel--;
            }
            
            // Detect function/method starts
            if (isMethodDeclaration(trimmedLine)) {
                // If we have accumulated content and we're starting a new method, finalize current chunk
                if (currentChunk.length() > 0 && braceLevel <= 1) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                }
                inFunction = true;
            }
            
            // Detect class declarations
            if (isClassDeclaration(trimmedLine)) {
                if (currentChunk.length() > 0 && braceLevel <= 1) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                }
                inClass = true;
            }
            
            currentChunk.append(line).append("\n");
            
            // If we've closed all braces at a function/class level, consider ending the chunk
            if (braceLevel == 0 && (inFunction || inClass) && currentChunk.length() > MIN_CODE_CHUNK_SIZE) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
                inFunction = false;
                inClass = false;
            }
            
            // Prevent chunks from becoming too large
            if (currentChunk.length() > MAX_CODE_CHUNK_SIZE) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
                inFunction = false;
                inClass = false;
            }
        }
        
        // Add any remaining content
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }
        
        return chunks.isEmpty() ? List.of(text) : chunks;
    }
    
    /**
     * Checks if a line contains a method/function declaration.
     */
    private boolean isMethodDeclaration(String line) {
        return line.matches(".*\\b(public|private|protected|static|def|function)\\b.*\\(.*\\).*\\{?.*") ||
               line.matches(".*\\w+\\s*\\([^)]*\\)\\s*\\{?.*");
    }
    
    /**
     * Checks if a line contains a class declaration.
     */
    private boolean isClassDeclaration(String line) {
        return line.matches(".*\\b(class|interface|enum)\\s+\\w+.*");
    }
    
    /**
     * Splits a large code chunk using enhanced text splitting with code awareness.
     */
    private List<String> splitLongCodeChunk(String text) {
        List<String> chunks = new ArrayList<>();
        String[] lines = text.split("\n");
        StringBuilder currentChunk = new StringBuilder();
        
        for (String line : lines) {
            // If adding this line would exceed the limit, finalize current chunk
            if (currentChunk.length() + line.length() + 1 > MAX_CODE_CHUNK_SIZE) {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                }
            }
            
            currentChunk.append(line).append("\n");
        }
        
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }
        
        return chunks.isEmpty() ? List.of(text) : chunks;
    }
    
    /**
     * Checks if a code chunk represents a complete statement.
     */
    private boolean isCompleteStatement(String chunk) {
        String trimmed = chunk.trim();
        
        // Complete statements often end with specific characters
        if (trimmed.endsWith(";") || trimmed.endsWith("}") || trimmed.endsWith(":")) {
            return true;
        }
        
        // Import statements are usually complete
        if (trimmed.startsWith("import ") || trimmed.startsWith("#include") || 
            trimmed.startsWith("using ") || trimmed.startsWith("from ")) {
            return true;
        }
        
        // Single-line function calls or assignments
        if (trimmed.contains("=") && !trimmed.contains("{") && trimmed.length() < 200) {
            return true;
        }
        
        return false;
    }

    /**
     * 헤더의 레벨을 결정합니다.
     */
    private int determineHeaderLevel(Map<String, Object> element) {
        // metadata에서 레벨 정보 추출 시도
                    @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) element.get("metadata");
        if (metadata != null && metadata.containsKey("category_depth")) {
            try {
                return ((Number) metadata.get("category_depth")).intValue();
            } catch (Exception e) {
                log.debug("Failed to extract header level from metadata", e);
            }
        }
        
        // 기본값 반환
        return 2;
    }

    /**
     * 계층 구조 스택을 조정합니다.
     */
    private void adjustHierarchyStack(Stack<ChunkContext> stack, int newLevel) {
        // 새로운 레벨보다 깊거나 같은 레벨의 요소들을 제거
        while (!stack.isEmpty() && stack.peek().level >= newLevel) {
            stack.pop();
        }
    }

    /**
     * 긴 텍스트를 여러 청크로 분할합니다.
     */
    private List<String> splitLongText(String text) {
        if (text.length() <= MAX_CHUNK_SIZE) {
            return List.of(text);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + MAX_CHUNK_SIZE, text.length());
            
            // 단어 경계에서 자르기
            if (end < text.length()) {
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > start) {
                    end = lastSpace;
                }
            }

            chunks.add(text.substring(start, end).trim());
            start = Math.max(start + 1, end - CHUNK_OVERLAP);
        }

        return chunks;
    }



    /**
     * 요소에서 메타데이터를 추출합니다.
     */
    private Map<String, Object> extractMetadata(Map<String, Object> element) {
        Map<String, Object> metadata = new HashMap<>();
        
        // 기본 메타데이터 복사
        if (element.containsKey("metadata")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> originalMetadata = (Map<String, Object>) element.get("metadata");
            metadata.putAll(originalMetadata);
        }

        // 좌표 정보 추가
        if (element.containsKey("coordinates")) {
            metadata.put("coordinates", element.get("coordinates"));
        }

        return metadata;
    }

    /**
     * 청킹 컨텍스트를 관리하는 내부 클래스
     */
    private static class ChunkContext {
        final String title;
        final int level;
        final ChunkContext parent;
        final int chunkIndex;
        private static int globalChunkIndex = 0;

        ChunkContext(String title, int level, ChunkContext parent) {
            this.title = title;
            this.level = level;
            this.parent = parent;
            this.chunkIndex = globalChunkIndex++;
        }
    }
}