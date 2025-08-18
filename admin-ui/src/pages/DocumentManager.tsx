import React, { useState, useCallback } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Button } from '../components/ui/Button'
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/Card'
import { FileUpload } from '../components/ui/FileUpload'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/Table'
import { Badge } from '../components/ui/Badge'
import { Input } from '../components/ui/Input'
import { useAuthStore } from '../store/authStore'
import { uploadFile, getDocuments, deleteDocument, resyncDocument } from '../lib/api'
import { formatFileSize, formatRelativeTime, getStatusColor } from '../lib/utils'
import type { SourceDocumentDto, UploadFileRequest } from '../types/api'

export const DocumentManager: React.FC = () => {
  const { apiKey } = useAuthStore()
  const queryClient = useQueryClient()
  
  // Local state
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [currentPage, setCurrentPage] = useState(0)
  const [pageSize] = useState(20)
  const [searchQuery, setSearchQuery] = useState('')

  // Queries
  const {
    data: documentsPage,
    isLoading: isLoadingDocuments,
    error: documentsError,
    refetch: refetchDocuments
  } = useQuery({
    queryKey: ['documents', currentPage, pageSize, apiKey],
    queryFn: () => getDocuments({
      page: currentPage,
      size: pageSize,
      sort: 'createdAt,desc',
      apiKey: apiKey!
    }),
    enabled: !!apiKey,
    refetchInterval: 5000, // Refetch every 5 seconds to update status
  })

  // Mutations
  const uploadMutation = useMutation({
    mutationFn: (request: UploadFileRequest) => uploadFile(request),
    onSuccess: () => {
      setSelectedFile(null)
      queryClient.invalidateQueries({ queryKey: ['documents'] })
    },
    onError: (error) => {
      console.error('Upload failed:', error)
    }
  })

  const deleteMutation = useMutation({
    mutationFn: ({ sourceId }: { sourceId: string }) => 
      deleteDocument({ sourceId, apiKey: apiKey! }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents'] })
    },
    onError: (error) => {
      console.error('Delete failed:', error)
    }
  })

  const resyncMutation = useMutation({
    mutationFn: ({ sourceId }: { sourceId: string }) => 
      resyncDocument({ sourceId, apiKey: apiKey! }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents'] })
    },
    onError: (error) => {
      console.error('Resync failed:', error)
    }
  })

  // Event handlers
  const handleFileSelect = useCallback((file: File) => {
    setSelectedFile(file)
  }, [])

  const handleClearFile = useCallback(() => {
    setSelectedFile(null)
  }, [])

  const handleUpload = useCallback(() => {
    if (!selectedFile || !apiKey) return
    
    uploadMutation.mutate({
      file: selectedFile,
      apiKey
    })
  }, [selectedFile, apiKey, uploadMutation])

  const handleDelete = useCallback((sourceId: string) => {
    if (!confirm('Are you sure you want to delete this document? This action cannot be undone.')) {
      return
    }
    deleteMutation.mutate({ sourceId })
  }, [deleteMutation])

  const handleResync = useCallback((sourceId: string) => {
    resyncMutation.mutate({ sourceId })
  }, [resyncMutation])

  const handleRefresh = useCallback(() => {
    refetchDocuments()
  }, [refetchDocuments])

  const handlePageChange = useCallback((newPage: number) => {
    setCurrentPage(newPage)
  }, [])

  // Filter documents based on search query
  const filteredDocuments = documentsPage?.content.filter(doc =>
    doc.originalFilename.toLowerCase().includes(searchQuery.toLowerCase())
  ) || []

  // Check if API key is available
  if (!apiKey) {
    return (
      <div className="p-6">
        <Card>
          <CardHeader>
            <CardTitle>Authentication Required</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-gray-600">
              Please configure your API key in the Settings page to access document management features.
            </p>
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="space-y-6 p-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Document Manager</h1>
          <p className="text-gray-600">
            Upload and manage your knowledge documents. Monitor ingestion status and troubleshoot issues.
          </p>
        </div>
        <Button onClick={handleRefresh} variant="outline" disabled={isLoadingDocuments}>
          {isLoadingDocuments ? 'Refreshing...' : 'Refresh'}
        </Button>
      </div>

      {/* File Upload Section */}
      <Card>
        <CardHeader>
          <CardTitle>Upload New Document</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <FileUpload
            onFileSelect={handleFileSelect}
            accept=".pdf,.md,.markdown,.txt"
            maxSize={100 * 1024 * 1024} // 100MB
            selectedFile={selectedFile}
            onClearFile={handleClearFile}
          />
          
          {selectedFile && (
            <div className="flex justify-between items-center pt-4">
              <div className="text-sm text-gray-600">
                <span className="font-medium">{selectedFile.name}</span>
                <span className="ml-2">({formatFileSize(selectedFile.size)})</span>
              </div>
              <Button 
                onClick={handleUpload} 
                disabled={uploadMutation.isPending}
              >
                {uploadMutation.isPending ? 'Uploading...' : 'Upload Document'}
              </Button>
            </div>
          )}

          {uploadMutation.error && (
            <div className="text-red-600 text-sm">
              Upload failed: {uploadMutation.error.message}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Document List Section */}
      <Card>
        <CardHeader>
          <div className="flex justify-between items-center">
            <CardTitle>Documents ({documentsPage?.totalElements || 0})</CardTitle>
            <div className="w-64">
              <Input
                placeholder="Search documents..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {documentsError && (
            <div className="text-red-600 mb-4">
              Failed to load documents: {documentsError.message}
            </div>
          )}

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Filename</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Size</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Last Updated</TableHead>
                <TableHead>Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoadingDocuments ? (
                <TableRow>
                  <TableCell colSpan={6} className="text-center py-8">
                    Loading documents...
                  </TableCell>
                </TableRow>
              ) : filteredDocuments.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} className="text-center py-8 text-gray-500">
                    {searchQuery ? 'No documents match your search.' : 'No documents uploaded yet.'}
                  </TableCell>
                </TableRow>
              ) : (
                filteredDocuments.map((doc: SourceDocumentDto) => (
                  <TableRow key={doc.id}>
                    <TableCell className="font-medium">
                      <div>
                        <div className="truncate max-w-xs" title={doc.originalFilename}>
                          {doc.originalFilename}
                        </div>
                        {doc.errorMessage && (
                          <div className="text-xs text-red-600 truncate max-w-xs" title={doc.errorMessage}>
                            Error: {doc.errorMessage}
                          </div>
                        )}
                      </div>
                    </TableCell>
                    <TableCell>{doc.fileType}</TableCell>
                    <TableCell>{formatFileSize(doc.fileSize)}</TableCell>
                    <TableCell>
                      <Badge className={getStatusColor(doc.ingestionStatus)}>
                        {doc.ingestionStatus}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <div className="text-sm">
                        <div>{formatRelativeTime(new Date(doc.updatedAt))}</div>
                        {doc.lastIngestedAt && (
                          <div className="text-xs text-gray-500">
                            Completed: {formatRelativeTime(new Date(doc.lastIngestedAt))}
                          </div>
                        )}
                      </div>
                    </TableCell>
                    <TableCell>
                      <div className="flex gap-2">
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => handleResync(doc.id)}
                          disabled={resyncMutation.isPending || ['PARSING', 'CHUNKING', 'EMBEDDING', 'INDEXING', 'DELETING'].includes(doc.ingestionStatus)}
                        >
                          {resyncMutation.isPending ? 'Resyncing...' : 'Resync'}
                        </Button>
                        <Button
                          size="sm"
                          variant="destructive"
                          onClick={() => handleDelete(doc.id)}
                          disabled={deleteMutation.isPending || doc.ingestionStatus === 'DELETING'}
                        >
                          {deleteMutation.isPending ? 'Deleting...' : 'Delete'}
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>

          {/* Pagination */}
          {documentsPage && documentsPage.totalPages > 1 && (
            <div className="flex justify-between items-center mt-6">
              <div className="text-sm text-gray-600">
                Showing {currentPage * pageSize + 1} to{' '}
                {Math.min((currentPage + 1) * pageSize, documentsPage.totalElements)} of{' '}
                {documentsPage.totalElements} documents
              </div>
              <div className="flex gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => handlePageChange(currentPage - 1)}
                  disabled={!documentsPage.hasPrevious}
                >
                  Previous
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => handlePageChange(currentPage + 1)}
                  disabled={!documentsPage.hasNext}
                >
                  Next
                </Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}