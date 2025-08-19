import React, { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/Card'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { Badge } from '../components/ui/Badge'
import { searchKnowledge, getContent } from '../lib/api'
import { formatFileSize } from '../lib/utils'
import { 
  Search as SearchIcon, 
  FileText, 
  ChevronRight,
  Copy,
  Loader2
} from 'lucide-react'
import type { SearchResultsResponse, GetContentResponse } from '../types/api'

export const Search: React.FC = () => {
  const [searchQuery, setSearchQuery] = useState('')
  const [topK, setTopK] = useState(10)
  const [maxTokens, setMaxTokens] = useState(8000)
  const [selectedChunk, setSelectedChunk] = useState<string | null>(null)
  const [searchResults, setSearchResults] = useState<SearchResultsResponse | null>(null)
  const [contentData, setContentData] = useState<GetContentResponse | null>(null)
  const [isSearching, setIsSearching] = useState(false)
  const [isLoadingContent, setIsLoadingContent] = useState(false)
  const [searchError, setSearchError] = useState<string | null>(null)
  const [contentError, setContentError] = useState<string | null>(null)

  // Handle knowledge search (find_knowledge MCP tool)
  const handleSearch = async () => {
    if (!searchQuery.trim()) {
      setSearchError('Please enter a search query')
      return
    }

    setIsSearching(true)
    setSearchError(null)
    setSearchResults(null)
    setSelectedChunk(null)
    setContentData(null)

    try {
      const results = await searchKnowledge(searchQuery, topK)
      setSearchResults(results)
    } catch (error) {
      setSearchError(error instanceof Error ? error.message : 'Search failed')
    } finally {
      setIsSearching(false)
    }
  }

  // Handle content retrieval (get_content MCP tool)
  const handleGetContent = async (chunkId: string) => {
    setIsLoadingContent(true)
    setContentError(null)
    setSelectedChunk(chunkId)

    try {
      const content = await getContent({ chunkId, maxTokens })
      setContentData(content)
    } catch (error) {
      setContentError(error instanceof Error ? error.message : 'Failed to get content')
      setSelectedChunk(null)
    } finally {
      setIsLoadingContent(false)
    }
  }

  // Copy content to clipboard
  const handleCopyContent = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text)
      // TODO: Show toast notification
    } catch (error) {
      console.error('Failed to copy content:', error)
    }
  }

  // Clear all results
  const handleClear = () => {
    setSearchQuery('')
    setSearchResults(null)
    setSelectedChunk(null)
    setContentData(null)
    setSearchError(null)
    setContentError(null)
  }

  return (
    <div className="space-y-6 p-6">
      {/* Header */}
      <div className="border-b border-gray-200 pb-4">
        <h1 className="text-3xl font-bold tracking-tight">Search Interface</h1>
        <p className="text-gray-600">
          Test the MCP search tools: find_knowledge and get_content. Search your knowledge base and retrieve detailed content.
        </p>
      </div>

      {/* Search Section */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center space-x-2">
            <SearchIcon className="h-5 w-5" />
            <span>Knowledge Search (find_knowledge)</span>
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="md:col-span-2">
              <Input
                placeholder="Enter your search query..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
              />
            </div>
            <div className="flex space-x-2">
              <Input
                type="number"
                placeholder="Results"
                value={topK}
                onChange={(e) => setTopK(parseInt(e.target.value) || 10)}
                min={1}
                max={100}
                className="w-24"
              />
              <Button 
                onClick={handleSearch} 
                disabled={isSearching || !searchQuery.trim()}
                className="flex-1"
              >
                {isSearching ? (
                  <>
                    <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                    Searching...
                  </>
                ) : (
                  <>
                    <SearchIcon className="h-4 w-4 mr-2" />
                    Search
                  </>
                )}
              </Button>
            </div>
          </div>

          {searchError && (
            <div className="text-red-600 text-sm bg-red-50 p-3 rounded-md">
              Error: {searchError}
            </div>
          )}

          <div className="flex justify-between items-center">
            <div className="text-sm text-gray-600">
              {searchResults && (
                <span>Found {searchResults.results.length} results</span>
              )}
            </div>
            {(searchResults || searchError) && (
              <Button variant="outline" size="sm" onClick={handleClear}>
                Clear Results
              </Button>
            )}
          </div>
        </CardContent>
      </Card>

      {/* Search Results */}
      {searchResults && (
        <Card>
          <CardHeader>
            <CardTitle>Search Results</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-3">
              {searchResults.results.map((result, index) => (
                <div 
                  key={result.chunkId}
                  className={`p-4 border rounded-lg cursor-pointer transition-colors ${
                    selectedChunk === result.chunkId 
                      ? 'border-blue-500 bg-blue-50' 
                      : 'border-gray-200 hover:border-gray-300'
                  }`}
                  onClick={() => handleGetContent(result.chunkId)}
                >
                  <div className="flex justify-between items-start">
                    <div className="flex-1">
                      <div className="flex items-center space-x-2 mb-2">
                        <span className="text-sm font-medium text-gray-500">#{index + 1}</span>
                        <Badge variant="secondary">
                          Score: {(result.relevanceScore * 100).toFixed(1)}%
                        </Badge>
                      </div>
                      
                      <h3 className="font-medium text-gray-900 mb-1">
                        {result.title || 'Untitled'}
                      </h3>
                      
                      {result.snippet && (
                        <p className="text-sm text-gray-600 mb-2">
                          {result.snippet}
                        </p>
                      )}
                      
                      <div className="text-xs text-gray-500">
                        Chunk ID: {result.chunkId}
                      </div>
                    </div>
                    
                    <ChevronRight className="h-4 w-4 text-gray-400 ml-4" />
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {/* Content Viewer */}
      {(selectedChunk || contentData || contentError) && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center justify-between">
              <div className="flex items-center space-x-2">
                <FileText className="h-5 w-5" />
                <span>Content Viewer (get_content)</span>
              </div>
              <div className="flex items-center space-x-2">
                <Input
                  type="number"
                  placeholder="Max tokens"
                  value={maxTokens}
                  onChange={(e) => setMaxTokens(parseInt(e.target.value) || 8000)}
                  min={100}
                  max={25000}
                  className="w-32 text-sm"
                />
                {contentData && (
                  <Button 
                    variant="outline" 
                    size="sm"
                    onClick={() => handleCopyContent(contentData.content)}
                  >
                    <Copy className="h-4 w-4 mr-1" />
                    Copy
                  </Button>
                )}
              </div>
            </CardTitle>
          </CardHeader>
          <CardContent>
            {isLoadingContent && (
              <div className="flex items-center justify-center py-8">
                <Loader2 className="h-6 w-6 animate-spin mr-2" />
                <span>Loading content...</span>
              </div>
            )}

            {contentError && (
              <div className="text-red-600 text-sm bg-red-50 p-3 rounded-md">
                Error: {contentError}
              </div>
            )}

            {contentData && (
              <div className="space-y-4">
                {/* Content Metadata */}
                <div className="bg-gray-50 p-4 rounded-lg">
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
                    <div>
                      <span className="font-medium text-gray-700">Chunk ID:</span>
                      <div className="text-gray-600 font-mono text-xs break-all">
                        {selectedChunk}
                      </div>
                    </div>
                    <div>
                      <span className="font-medium text-gray-700">Tokens:</span>
                      <div className="text-gray-600">
                        {contentData.tokenInfo.actualTokens} / {maxTokens}
                      </div>
                    </div>
                  </div>
                </div>

                {/* Content Text */}
                <div className="bg-white border rounded-lg overflow-hidden">
                  <div className="p-4 max-h-96 overflow-y-auto">
                    <pre className="whitespace-pre-wrap text-sm text-gray-800 font-mono leading-relaxed overflow-hidden text-ellipsis" style={{ wordBreak: 'break-all', whiteSpace: 'pre-wrap' }}>
                      {contentData.content}
                    </pre>
                  </div>
                </div>

                {/* Content Actions */}
                <div className="flex justify-between items-center text-sm text-gray-600">
                  <div>
                    Content length: {contentData.content.length} characters
                  </div>
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* Usage Instructions */}
      <Card>
        <CardHeader>
          <CardTitle>How to Use</CardTitle>
        </CardHeader>
        <CardContent className="text-sm text-gray-600 space-y-2">
          <p><strong>1. Search Knowledge:</strong> Enter a query and click Search to find relevant chunks using the find_knowledge MCP tool.</p>
          <p><strong>2. View Content:</strong> Click on any search result to retrieve the full content using the get_content MCP tool.</p>
          <p><strong>3. Adjust Parameters:</strong> Modify the number of results (topK) and maximum tokens for content retrieval.</p>
          <p><strong>4. Copy Content:</strong> Use the Copy button to copy content to your clipboard for further use.</p>
        </CardContent>
      </Card>
    </div>
  )
}