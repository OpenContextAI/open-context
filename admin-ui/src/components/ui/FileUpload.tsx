import React, { useCallback } from 'react'
import { Upload, File, X } from 'lucide-react'
import { cn } from '../../lib/utils'
import { Button } from './Button'

interface FileUploadProps {
  onFileSelect: (file: File) => void
  accept?: string
  maxSize?: number // in bytes
  disabled?: boolean
  className?: string
  selectedFile?: File | null
  onClearFile?: () => void
}

/**
 * File upload component with drag-and-drop support
 * 
 * @example
 * ```tsx
 * <FileUpload
 *   onFileSelect={handleFileSelect}
 *   accept=".pdf,.md,.txt"
 *   maxSize={100 * 1024 * 1024} // 100MB
 *   selectedFile={file}
 *   onClearFile={handleClearFile}
 * />
 * ```
 */
export const FileUpload: React.FC<FileUploadProps> = ({
  onFileSelect,
  accept = '.pdf,.md,.txt',
  maxSize = 100 * 1024 * 1024, // 100MB default
  disabled = false,
  className,
  selectedFile,
  onClearFile,
}) => {
  const [dragOver, setDragOver] = React.useState(false)
  const fileInputRef = React.useRef<HTMLInputElement>(null)

  const handleDrop = useCallback(
    (e: React.DragEvent<HTMLDivElement>) => {
      e.preventDefault()
      setDragOver(false)

      if (disabled) return

      const files = Array.from(e.dataTransfer.files)
      if (files.length > 0) {
        const file = files[0]
        if (file.size <= maxSize) {
          onFileSelect(file)
        } else {
          alert(`File size exceeds ${Math.round(maxSize / (1024 * 1024))}MB limit`)
        }
      }
    },
    [onFileSelect, maxSize, disabled]
  )

  const handleDragOver = useCallback((e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    if (!disabled) {
      setDragOver(true)
    }
  }, [disabled])

  const handleDragLeave = useCallback((e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setDragOver(false)
  }, [])

  const handleFileInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const files = e.target.files
      if (files && files.length > 0) {
        const file = files[0]
        if (file.size <= maxSize) {
          onFileSelect(file)
        } else {
          alert(`File size exceeds ${Math.round(maxSize / (1024 * 1024))}MB limit`)
        }
      }
    },
    [onFileSelect, maxSize]
  )

  const handleBrowseClick = () => {
    fileInputRef.current?.click()
  }

  const formatFileSize = (bytes: number): string => {
    const sizes = ['B', 'KB', 'MB', 'GB']
    if (bytes === 0) return '0 B'
    const i = Math.floor(Math.log(bytes) / Math.log(1024))
    return Math.round(bytes / Math.pow(1024, i) * 100) / 100 + ' ' + sizes[i]
  }

  return (
    <div className={cn('space-y-4', className)}>
      <div
        className={cn(
          'relative border-2 border-dashed rounded-lg p-6 text-center transition-colors',
          dragOver && !disabled
            ? 'border-primary-500 bg-primary-50'
            : 'border-gray-300 hover:border-gray-400',
          disabled && 'opacity-50 cursor-not-allowed'
        )}
        onDrop={handleDrop}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
      >
        <input
          ref={fileInputRef}
          type="file"
          accept={accept}
          onChange={handleFileInputChange}
          className="hidden"
          disabled={disabled}
        />

        <div className="flex flex-col items-center space-y-4">
          <Upload
            className={cn(
              'h-10 w-10',
              dragOver && !disabled ? 'text-primary-500' : 'text-gray-400'
            )}
          />
          
          <div>
            <p className="text-lg font-medium">
              {dragOver && !disabled
                ? 'Drop your file here'
                : 'Drag and drop your file here'}
            </p>
            <p className="text-sm text-muted-foreground mt-1">
              or{' '}
              <Button
                variant="ghost"
                size="sm"
                onClick={handleBrowseClick}
                disabled={disabled}
                className="p-0 h-auto font-medium text-primary-600 hover:text-primary-700"
              >
                browse to upload
              </Button>
            </p>
          </div>

          <div className="text-xs text-muted-foreground">
            <p>Supported formats: PDF, Markdown, Plain Text</p>
            <p>Maximum file size: {Math.round(maxSize / (1024 * 1024))}MB</p>
          </div>
        </div>
      </div>

      {selectedFile && (
        <div className="flex items-center justify-between p-3 bg-gray-50 rounded-md">
          <div className="flex items-center space-x-3">
            <File className="h-5 w-5 text-gray-500" />
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-gray-900 truncate">
                {selectedFile.name}
              </p>
              <p className="text-xs text-gray-500">
                {formatFileSize(selectedFile.size)}
              </p>
            </div>
          </div>
          {onClearFile && (
            <Button
              variant="ghost"
              size="sm"
              onClick={onClearFile}
              className="h-8 w-8 p-0"
            >
              <X className="h-4 w-4" />
            </Button>
          )}
        </div>
      )}
    </div>
  )
}