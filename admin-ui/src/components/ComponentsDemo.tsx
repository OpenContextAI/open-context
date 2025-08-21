import React, { useState } from 'react'
import { Upload, File, Search, Trash2, RefreshCw, Eye } from 'lucide-react'
import { Button } from './ui/Button'
import { Card, CardHeader, CardTitle, CardContent, CardFooter } from './ui/Card'
import { Badge } from './ui/Badge'
import { Input } from './ui/Input'
import { FileUpload } from './ui/FileUpload'
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from './ui/Table'
import { formatFileSize, formatRelativeTime, getStatusColor } from '../lib/utils'

/**
 * Demo page showcasing all reusable UI components
 * This serves as both a style guide and component testing environment
 */
export const ComponentsDemo: React.FC = () => {
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [loading, setLoading] = useState(false)

  // Mock data for demonstration
  const mockDocuments = [
    {
      id: '1',
      originalFilename: 'spring-security-guide.pdf',
      fileType: 'PDF' as const,
      fileSize: 2048576,
      ingestionStatus: 'COMPLETED' as const,
      errorMessage: null,
      lastIngestedAt: '2025-08-18T10:30:00Z',
      createdAt: '2025-08-18T10:00:00Z',
      updatedAt: '2025-08-18T10:30:00Z',
    },
    {
      id: '2',
      originalFilename: 'api-documentation.md',
      fileType: 'MARKDOWN' as const,
      fileSize: 51200,
      ingestionStatus: 'PARSING' as const,
      errorMessage: null,
      lastIngestedAt: null,
      createdAt: '2025-08-18T11:00:00Z',
      updatedAt: '2025-08-18T11:05:00Z',
    },
    {
      id: '3',
      originalFilename: 'troubleshooting.txt',
      fileType: 'TXT' as const,
      fileSize: 10240,
      ingestionStatus: 'ERROR' as const,
      errorMessage: 'Failed to parse document structure',
      lastIngestedAt: null,
      createdAt: '2025-08-18T09:30:00Z',
      updatedAt: '2025-08-18T09:35:00Z',
    },
  ]

  const handleFileSelect = (file: File) => {
    setSelectedFile(file)
  }

  const handleClearFile = () => {
    setSelectedFile(null)
  }

  const handleUpload = async () => {
    if (!selectedFile) return
    
    setLoading(true)
    // Simulate upload
    await new Promise(resolve => setTimeout(resolve, 2000))
    setLoading(false)
    setSelectedFile(null)
    alert('File uploaded successfully!')
  }

  const handleAction = async (action: string, id?: string) => {
    setLoading(true)
    await new Promise(resolve => setTimeout(resolve, 1000))
    setLoading(false)
    alert(`${action} executed${id ? ` for document ${id}` : ''}`)
  }

  return (
    <div className="min-h-screen bg-gray-50 py-8">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 space-y-8">
        {/* Header */}
        <div className="text-center">
          <h1 className="text-4xl font-bold text-gray-900 mb-2">
            OpenContext Admin Components
          </h1>
          <p className="text-lg text-gray-600">
            Reusable UI components for the admin dashboard
          </p>
        </div>

        {/* Button Variants */}
        <Card>
          <CardHeader>
            <CardTitle>Button Components</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex flex-wrap gap-4">
              <Button variant="default">Default Button</Button>
              <Button variant="destructive">Destructive</Button>
              <Button variant="outline">Outline</Button>
              <Button variant="secondary">Secondary</Button>
              <Button variant="ghost">Ghost</Button>
              <Button loading>Loading...</Button>
              <Button disabled>Disabled</Button>
            </div>
            
            <div className="flex flex-wrap gap-4 items-center">
              <Button size="sm">Small</Button>
              <Button size="default">Default</Button>
              <Button size="lg">Large</Button>
              <Button size="icon">
                <Search className="h-4 w-4" />
              </Button>
            </div>
          </CardContent>
        </Card>

        {/* Badge Variants */}
        <Card>
          <CardHeader>
            <CardTitle>Badge Components</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex flex-wrap gap-4">
              <Badge variant="default">Default</Badge>
              <Badge variant="secondary">Secondary</Badge>
              <Badge variant="success">Success</Badge>
              <Badge variant="warning">Warning</Badge>
              <Badge variant="destructive">Error</Badge>
              <Badge variant="outline">Outline</Badge>
            </div>
            <div className="mt-4 space-y-2">
              <h4 className="text-sm font-medium">Status Badges</h4>
              <div className="flex flex-wrap gap-2">
                <Badge className={getStatusColor('COMPLETED')}>COMPLETED</Badge>
                <Badge className={getStatusColor('PARSING')}>PARSING</Badge>
                <Badge className={getStatusColor('ERROR')}>ERROR</Badge>
                <Badge className={getStatusColor('PENDING')}>PENDING</Badge>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Input Components */}
        <Card>
          <CardHeader>
            <CardTitle>Input Components</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <Input
                label="API Key"
                placeholder="Enter your API key"
                type="password"
              />
              <Input
                label="Search Query"
                placeholder="Search documents..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
              <Input
                label="Error Example"
                placeholder="This field has an error"
                error="This field is required"
              />
              <Input
                label="Disabled Field"
                placeholder="Cannot edit this"
                disabled
              />
            </div>
          </CardContent>
        </Card>

        {/* File Upload Component */}
        <Card>
          <CardHeader>
            <CardTitle>File Upload Component</CardTitle>
          </CardHeader>
          <CardContent>
            <FileUpload
              onFileSelect={handleFileSelect}
              selectedFile={selectedFile}
              onClearFile={handleClearFile}
            />
            {selectedFile && (
              <div className="mt-4 flex justify-end">
                <Button onClick={handleUpload} loading={loading}>
                  <Upload className="mr-2 h-4 w-4" />
                  Upload File
                </Button>
              </div>
            )}
          </CardContent>
        </Card>

        {/* Table Component */}
        <Card>
          <CardHeader>
            <CardTitle>Table Component</CardTitle>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Filename</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Size</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Created</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {mockDocuments.map((doc) => (
                  <TableRow key={doc.id}>
                    <TableCell className="font-medium">
                      <div className="flex items-center space-x-2">
                        <File className="h-4 w-4 text-gray-500" />
                        <span>{doc.originalFilename}</span>
                      </div>
                    </TableCell>
                    <TableCell>{doc.fileType}</TableCell>
                    <TableCell>{formatFileSize(doc.fileSize)}</TableCell>
                    <TableCell>
                      <Badge className={getStatusColor(doc.ingestionStatus)}>
                        {doc.ingestionStatus}
                      </Badge>
                    </TableCell>
                    <TableCell>{formatRelativeTime(doc.createdAt)}</TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end space-x-2">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleAction('View', doc.id)}
                        >
                          <Eye className="h-4 w-4" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleAction('Resync', doc.id)}
                        >
                          <RefreshCw className="h-4 w-4" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleAction('Delete', doc.id)}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>

        {/* Card Layouts */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          <Card>
            <CardHeader>
              <CardTitle>Statistics Card</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-3xl font-bold text-primary-600">42</div>
              <p className="text-sm text-muted-foreground">Total Documents</p>
            </CardContent>
            <CardFooter>
              <Button variant="outline" size="sm" className="w-full">
                View All
              </Button>
            </CardFooter>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Status Overview</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              <div className="flex justify-between items-center">
                <span className="text-sm">Completed</span>
                <Badge variant="success">28</Badge>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-sm">Processing</span>
                <Badge variant="warning">12</Badge>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-sm">Failed</span>
                <Badge variant="destructive">2</Badge>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Quick Actions</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              <Button variant="outline" size="sm" className="w-full">
                <Upload className="mr-2 h-4 w-4" />
                Upload Document
              </Button>
              <Button variant="outline" size="sm" className="w-full">
                <RefreshCw className="mr-2 h-4 w-4" />
                Refresh All
              </Button>
              <Button variant="outline" size="sm" className="w-full">
                <Search className="mr-2 h-4 w-4" />
                Search Knowledge
              </Button>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}