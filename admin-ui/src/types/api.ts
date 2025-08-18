/**
 * API Types for OpenContext Admin UI
 * These types match the backend DTOs and API specifications
 */

export interface CommonResponse<T = any> {
  success: boolean
  data: T | null
  message: string
  errorCode: string | null
  timestamp: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
  hasNext: boolean
  hasPrevious: boolean
}

export interface SourceDocumentDto {
  id: string
  originalFilename: string
  fileType: 'PDF' | 'MARKDOWN' | 'TXT'
  fileSize: number
  ingestionStatus: IngestionStatus
  errorMessage: string | null
  lastIngestedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface SourceUploadResponse {
  sourceDocumentId: string
  originalFilename: string
  ingestionStatus: IngestionStatus
  message: string
}

export type IngestionStatus = 
  | 'PENDING'
  | 'PARSING' 
  | 'CHUNKING'
  | 'EMBEDDING'
  | 'INDEXING'
  | 'COMPLETED'
  | 'ERROR'
  | 'DELETING'

export interface SearchResultItem {
  chunkId: string
  title: string | null
  snippet: string | null
  relevanceScore: number
  breadcrumbs?: string[]
}

export interface SearchResultsResponse {
  results: SearchResultItem[]
}

export interface GetContentRequest {
  chunkId: string
  maxTokens?: number
}

export interface GetContentResponse {
  content: string
  tokenInfo: {
    tokenizer: string
    actualTokens: number
  }
}

export interface ApiError {
  success: false
  data: null
  message: string
  errorCode: string
  timestamp: string
}

// API Request/Response interfaces for React Query
export interface UploadFileRequest {
  file: File
  apiKey: string
}

export interface GetDocumentsRequest {
  page?: number
  size?: number
  sort?: string
  apiKey: string
}

export interface DeleteDocumentRequest {
  sourceId: string
  apiKey: string
}

export interface ResyncDocumentRequest {
  sourceId: string
  apiKey: string
}