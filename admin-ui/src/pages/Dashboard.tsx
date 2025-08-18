import React from 'react'
import { useQuery } from '@tanstack/react-query'
import { Card, CardHeader, CardTitle, CardContent, CardFooter } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { useAuthStore } from '../store/authStore'
import { getDocuments } from '../lib/api'
import { formatFileSize, formatRelativeTime } from '../lib/utils'
import { 
  FileText, 
  CheckCircle, 
  AlertCircle,
  Clock,
  TrendingUp,
  Server,
  Search
} from 'lucide-react'

export const Dashboard: React.FC = () => {
  const { apiKey } = useAuthStore()
  
  // Fetch all documents to calculate statistics
  const { data: documentsPage, isLoading } = useQuery({
    queryKey: ['dashboard-documents', apiKey],
    queryFn: () => getDocuments({
      page: 0,
      size: 1000, // Get all documents for stats
      sort: 'createdAt,desc',
      apiKey: apiKey!
    }),
    enabled: !!apiKey,
    refetchInterval: 30000, // Refetch every 30 seconds
  })

  // Calculate statistics from real data
  const stats = React.useMemo(() => {
    if (!documentsPage?.content) {
      return {
        totalDocuments: 0,
        completedDocuments: 0,
        processingDocuments: 0,
        errorDocuments: 0,
        totalChunks: 0, // TODO: Add chunk count API
        indexedChunks: 0
      }
    }

    const documents = documentsPage.content
    const totalDocuments = documents.length
    const completedDocuments = documents.filter(doc => doc.ingestionStatus === 'COMPLETED').length
    const processingDocuments = documents.filter(doc => 
      ['PENDING', 'PARSING', 'CHUNKING', 'EMBEDDING', 'INDEXING'].includes(doc.ingestionStatus)
    ).length
    const errorDocuments = documents.filter(doc => doc.ingestionStatus === 'ERROR').length

    return {
      totalDocuments,
      completedDocuments,
      processingDocuments,
      errorDocuments,
      totalChunks: 0, // TODO: Add chunk count API
      indexedChunks: completedDocuments // Approximation
    }
  }, [documentsPage?.content])

  // Get recent documents (latest 3)
  const recentDocuments = React.useMemo(() => {
    if (!documentsPage?.content) return []
    
    return documentsPage.content
      .slice(0, 3)
      .map(doc => ({
        id: doc.id,
        filename: doc.originalFilename,
        status: doc.ingestionStatus,
        createdAt: formatRelativeTime(new Date(doc.createdAt)),
        size: formatFileSize(doc.fileSize)
      }))
  }, [documentsPage?.content])

  const getStatusColor = (status: string) => {
    const colors = {
      COMPLETED: 'success',
      PENDING: 'warning',
      PARSING: 'warning',
      CHUNKING: 'warning', 
      EMBEDDING: 'warning',
      INDEXING: 'warning',
      ERROR: 'destructive'
    } as const
    
    return colors[status as keyof typeof colors] || 'default'
  }

  // Show message if no API key is configured
  if (!apiKey) {
    return (
      <div className="space-y-6 p-6">
        <div className="border-b border-gray-200 pb-4">
          <h1 className="text-3xl font-bold tracking-tight">Dashboard</h1>
          <p className="text-gray-600">
            Overview of your OpenContext knowledge base
          </p>
        </div>
        <Card>
          <CardHeader>
            <CardTitle>Configuration Required</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-gray-600">
              Please configure your API key in the Settings page to view dashboard statistics.
            </p>
          </CardContent>
          <CardFooter>
            <Button variant="outline" onClick={() => window.location.href = '/settings'}>
              Go to Settings
            </Button>
          </CardFooter>
        </Card>
      </div>
    )
  }

  return (
    <div className="space-y-6 p-6">
      {/* Page Header */}
      <div className="border-b border-gray-200 pb-4">
        <h1 className="text-3xl font-bold tracking-tight">Dashboard</h1>
        <p className="text-gray-600">
          Overview of your OpenContext knowledge base
          {isLoading && <span className="ml-2 text-sm text-blue-600">(Loading...)</span>}
        </p>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-gray-600">
              Total Documents
            </CardTitle>
            <FileText className="h-4 w-4 text-gray-400" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{stats.totalDocuments}</div>
            <p className="text-xs text-gray-600 mt-1">
              Total uploaded documents
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-gray-600">
              Completed
            </CardTitle>
            <CheckCircle className="h-4 w-4 text-green-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-green-600">
              {stats.completedDocuments}
            </div>
            <p className="text-xs text-gray-600 mt-1">
              {stats.totalDocuments > 0 
                ? `${Math.round((stats.completedDocuments / stats.totalDocuments) * 100)}% success rate`
                : 'No documents yet'
              }
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-gray-600">
              Processing
            </CardTitle>
            <Clock className="h-4 w-4 text-yellow-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-yellow-600">
              {stats.processingDocuments}
            </div>
            <p className="text-xs text-gray-600 mt-1">
              Currently being processed
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-gray-600">
              Errors
            </CardTitle>
            <AlertCircle className="h-4 w-4 text-red-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-red-600">
              {stats.errorDocuments}
            </div>
            <p className="text-xs text-gray-600 mt-1">
              Need attention
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-gray-600">
              Total Chunks
            </CardTitle>
            <TrendingUp className="h-4 w-4 text-blue-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{stats.totalChunks.toLocaleString()}</div>
            <p className="text-xs text-gray-600 mt-1">
              Knowledge segments
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-gray-600">
              Indexed
            </CardTitle>
            <Server className="h-4 w-4 text-purple-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-purple-600">
              {stats.indexedChunks.toLocaleString()}
            </div>
            <p className="text-xs text-gray-600 mt-1">
              Ready for search
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Recent Activity */}
      <Card>
        <CardHeader>
          <CardTitle>Recent Documents</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            {recentDocuments.map((doc) => (
              <div key={doc.id} className="flex items-center justify-between p-4 border rounded-lg">
                <div className="flex items-center space-x-4">
                  <FileText className="h-8 w-8 text-gray-400" />
                  <div>
                    <p className="font-medium text-gray-900">{doc.filename}</p>
                    <p className="text-sm text-gray-500">{doc.size} • {doc.createdAt}</p>
                  </div>
                </div>
                <Badge variant={getStatusColor(doc.status)}>
                  {doc.status}
                </Badge>
              </div>
            ))}
          </div>
        </CardContent>
        <CardFooter>
          <Button 
            variant="outline" 
            className="w-full"
            onClick={() => window.location.href = '/documents'}
          >
            View All Documents
          </Button>
        </CardFooter>
      </Card>

      {/* Quick Actions */}
      <Card>
        <CardHeader>
          <CardTitle>Quick Actions</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            <Button 
              className="h-auto p-6 flex flex-col items-center space-y-2"
              onClick={() => window.location.href = '/documents'}
            >
              <FileText className="h-8 w-8" />
              <span className="font-medium">Upload Document</span>
              <span className="text-xs opacity-75">Add new files to knowledge base</span>
            </Button>
            
            <Button 
              variant="outline" 
              className="h-auto p-6 flex flex-col items-center space-y-2"
              onClick={() => window.location.href = '/search'}
            >
              <Search className="h-8 w-8" />
              <span className="font-medium">Test Search</span>
              <span className="text-xs opacity-75">Try MCP search tools</span>
            </Button>
            
            <Button 
              variant="outline" 
              className="h-auto p-6 flex flex-col items-center space-y-2"
              onClick={() => window.location.href = '/settings'}
            >
              <Server className="h-8 w-8" />
              <span className="font-medium">System Status</span>
              <span className="text-xs opacity-75">Check service health</span>
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}