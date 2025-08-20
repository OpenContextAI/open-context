# MinIO File Storage Fix

## Issue Description
Files uploaded through the OpenContext Admin UI were being stored as directories instead of files in MinIO storage, making the file content inaccessible.

## Root Cause
The issue was in the `FileStorageService.uploadFile()` method where the MinIO `PutObjectArgs` was configured with a fixed `partSize` of `-1` for all files. This could cause MinIO to create directory-like structures instead of storing files properly, especially when combined with certain file sizes or content types.

## Solution
The fix implements intelligent partSize selection based on file size:

- **Files < 5MB**: Use `partSize = -1` (auto-detect, single part upload)
- **Files ≥ 5MB**: Use `partSize = 5MB` (proper multipart upload)

## Code Changes
```java
// Before (problematic)
.stream(file.getInputStream(), file.getSize(), -1)

// After (fixed)
long partSize = fileSize < 5 * 1024 * 1024 ? -1 : 5 * 1024 * 1024;
.stream(inputStream, fileSize, partSize)
```

## Validation
- Files are now stored as proper objects in MinIO
- File content is downloadable and accessible
- Object keys are properly formatted without trailing slashes
- Comprehensive test coverage validates the fix

## Testing
The fix is validated with comprehensive tests in:
- `FileStorageServiceTest.java` - General service functionality
- `MinIOFileStorageBugFixTest.java` - Specific bug fix validation

Run tests with: `./gradlew test --tests "*FileStorage*"`