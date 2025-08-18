import axios, { type AxiosResponse } from 'axios'
import type {
  CommonResponse,
  PageResponse,
  SourceDocumentDto,
  SourceUploadResponse,
  GetDocumentsRequest,
  UploadFileRequest,
  DeleteDocumentRequest,
  ResyncDocumentRequest,
  SearchResultsResponse,
  GetContentRequest,
  GetContentResponse,
} from '../types/api'

// API Base URL - can be configured via environment variables
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'

// Create axios instance with default configuration
const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 300000, // 5 minutes for file processing
  headers: {
    'Content-Type': 'application/json',
  },
})

// Response interceptor to handle common errors
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    console.error('API Error:', error)
    return Promise.reject(error)
  }
)

/**
 * Upload a file to start the ingestion pipeline
 */
export const uploadFile = async (request: UploadFileRequest): Promise<SourceUploadResponse> => {
  const formData = new FormData()
  formData.append('file', request.file)

  const response: AxiosResponse<CommonResponse<SourceUploadResponse>> = await apiClient.post(
    '/sources/upload',
    formData,
    {
      headers: {
        'Content-Type': 'multipart/form-data',
        'X-API-KEY': request.apiKey,
      },
    }
  )

  if (!response.data.success) {
    throw new Error(response.data.message || 'Upload failed')
  }

  return response.data.data!
}

/**
 * Get paginated list of source documents
 */
export const getDocuments = async (
  request: GetDocumentsRequest
): Promise<PageResponse<SourceDocumentDto>> => {
  const params = new URLSearchParams()
  if (request.page !== undefined) params.append('page', request.page.toString())
  if (request.size !== undefined) params.append('size', request.size.toString())
  if (request.sort) params.append('sort', request.sort)

  const response: AxiosResponse<CommonResponse<PageResponse<SourceDocumentDto>>> = 
    await apiClient.get(`/sources?${params.toString()}`, {
      headers: {
        'X-API-KEY': request.apiKey,
      },
    })

  if (!response.data.success) {
    throw new Error(response.data.message || 'Failed to fetch documents')
  }

  return response.data.data!
}

/**
 * Delete a source document
 */
export const deleteDocument = async (request: DeleteDocumentRequest): Promise<void> => {
  const response: AxiosResponse<CommonResponse<void>> = await apiClient.delete(
    `/sources/${request.sourceId}`,
    {
      headers: {
        'X-API-KEY': request.apiKey,
      },
    }
  )

  if (!response.data.success) {
    throw new Error(response.data.message || 'Delete failed')
  }
}

/**
 * Resync (re-process) a source document
 */
export const resyncDocument = async (request: ResyncDocumentRequest): Promise<void> => {
  const response: AxiosResponse<CommonResponse<void>> = await apiClient.post(
    `/sources/${request.sourceId}/resync`,
    {},
    {
      headers: {
        'X-API-KEY': request.apiKey,
      },
    }
  )

  if (!response.data.success) {
    throw new Error(response.data.message || 'Resync failed')
  }
}

/**
 * Search for knowledge chunks (MCP find_knowledge tool)
 */
export const searchKnowledge = async (
  query: string,
  topK: number = 50
): Promise<SearchResultsResponse> => {
  const params = new URLSearchParams()
  params.append('query', query)
  params.append('topK', topK.toString())

  const response: AxiosResponse<CommonResponse<SearchResultsResponse>> = await apiClient.get(
    `/search?${params.toString()}`
  )

  if (!response.data.success) {
    throw new Error(response.data.message || 'Search failed')
  }

  return response.data.data!
}

/**
 * Get content for a specific chunk (MCP get_content tool)
 */
export const getContent = async (request: GetContentRequest): Promise<GetContentResponse> => {
  const response: AxiosResponse<CommonResponse<GetContentResponse>> = await apiClient.post(
    '/get-content',
    request
  )

  if (!response.data.success) {
    throw new Error(response.data.message || 'Get content failed')
  }

  return response.data.data!
}