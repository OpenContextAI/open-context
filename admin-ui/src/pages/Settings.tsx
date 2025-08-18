import React, { useState } from 'react'
import { Button } from '../components/ui/Button'
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/Card'
import { Input } from '../components/ui/Input'
import { useAuthStore } from '../store/authStore'
import { Key, Save, Trash2, Eye, EyeOff, CheckCircle, AlertCircle } from 'lucide-react'

export const Settings: React.FC = () => {
  const { apiKey, setApiKey, clearAuth } = useAuthStore()
  const [inputApiKey, setInputApiKey] = useState(apiKey || '')
  const [showApiKey, setShowApiKey] = useState(false)
  const [saveMessage, setSaveMessage] = useState<{ type: 'success' | 'error', text: string } | null>(null)

  const handleSave = () => {
    try {
      if (!inputApiKey.trim()) {
        setSaveMessage({ type: 'error', text: 'API key cannot be empty' })
        return
      }

      setApiKey(inputApiKey.trim())
      setSaveMessage({ type: 'success', text: 'API key saved successfully' })
      
      // Clear message after 3 seconds
      setTimeout(() => setSaveMessage(null), 3000)
    } catch (error) {
      setSaveMessage({ type: 'error', text: 'Failed to save API key' })
      setTimeout(() => setSaveMessage(null), 3000)
    }
  }

  const handleClear = () => {
    if (confirm('Are you sure you want to remove the API key? You will need to re-enter it to access document management features.')) {
      clearAuth()
      setInputApiKey('')
      setSaveMessage({ type: 'success', text: 'API key cleared successfully' })
      setTimeout(() => setSaveMessage(null), 3000)
    }
  }

  const handleTestConnection = async () => {
    if (!inputApiKey.trim()) {
      setSaveMessage({ type: 'error', text: 'Please enter an API key first' })
      return
    }

    try {
      // Test API connection by making a simple request to the correct backend URL
      const response = await fetch('http://localhost:8080/api/v1/sources?page=0&size=1', {
        headers: {
          'X-API-KEY': inputApiKey.trim(),
          'Accept': 'application/json'
        }
      })

      if (response.ok) {
        setSaveMessage({ type: 'success', text: 'API key is valid and connection successful' })
      } else if (response.status === 403) {
        setSaveMessage({ type: 'error', text: 'Invalid API key or insufficient permissions' })
      } else {
        setSaveMessage({ type: 'error', text: 'Connection failed - please check your API key' })
      }
    } catch (error) {
      setSaveMessage({ type: 'error', text: 'Failed to connect to API server' })
    }

    setTimeout(() => setSaveMessage(null), 5000)
  }

  return (
    <div className="space-y-6 p-6">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Settings</h1>
        <p className="text-gray-600">
          Configure your OpenContext admin interface preferences and API access.
        </p>
      </div>

      {/* API Key Configuration */}
      <Card>
        <CardHeader>
          <div className="flex items-center space-x-2">
            <Key className="h-5 w-5 text-blue-600" />
            <CardTitle>API Key Configuration</CardTitle>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          <div>
            <p className="text-sm text-gray-600 mb-4">
              Enter your OpenContext API key to access document management features. 
              This key is stored locally in your browser and is required for uploading, 
              viewing, and managing documents.
            </p>
            
            <div className="space-y-4">
              <div className="flex space-x-2">
                <div className="flex-1 relative">
                  <Input
                    type={showApiKey ? 'text' : 'password'}
                    placeholder="Enter your API key..."
                    value={inputApiKey}
                    onChange={(e) => setInputApiKey(e.target.value)}
                    className="pr-10"
                  />
                  <button
                    type="button"
                    onClick={() => setShowApiKey(!showApiKey)}
                    className="absolute right-2 top-1/2 transform -translate-y-1/2 text-gray-400 hover:text-gray-600"
                  >
                    {showApiKey ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
              </div>

              {/* Action Buttons */}
              <div className="flex space-x-3">
                <Button onClick={handleSave} variant="default">
                  <Save className="h-4 w-4 mr-2" />
                  Save API Key
                </Button>
                
                <Button onClick={handleTestConnection} variant="outline">
                  <CheckCircle className="h-4 w-4 mr-2" />
                  Test Connection
                </Button>
                
                {apiKey && (
                  <Button onClick={handleClear} variant="destructive">
                    <Trash2 className="h-4 w-4 mr-2" />
                    Clear Key
                  </Button>
                )}
              </div>

              {/* Status Message */}
              {saveMessage && (
                <div className={`flex items-center space-x-2 p-3 rounded-md ${
                  saveMessage.type === 'success' 
                    ? 'bg-green-50 text-green-800 border border-green-200' 
                    : 'bg-red-50 text-red-800 border border-red-200'
                }`}>
                  {saveMessage.type === 'success' ? (
                    <CheckCircle className="h-4 w-4" />
                  ) : (
                    <AlertCircle className="h-4 w-4" />
                  )}
                  <span className="text-sm">{saveMessage.text}</span>
                </div>
              )}
            </div>
          </div>

          {/* Current Status */}
          <div className="pt-4 border-t">
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium text-gray-700">Current Status:</span>
              <div className="flex items-center space-x-2">
                {apiKey ? (
                  <>
                    <div className="h-2 w-2 bg-green-500 rounded-full"></div>
                    <span className="text-sm text-green-600">API Key Configured</span>
                  </>
                ) : (
                  <>
                    <div className="h-2 w-2 bg-red-500 rounded-full"></div>
                    <span className="text-sm text-red-600">No API Key Set</span>
                  </>
                )}
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* System Information */}
      <Card>
        <CardHeader>
          <CardTitle>System Information</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
            <div>
              <span className="font-medium text-gray-700">Application Version:</span>
              <span className="ml-2 text-gray-600">1.0.0</span>
            </div>
            <div>
              <span className="font-medium text-gray-700">Backend API:</span>
              <span className="ml-2 text-gray-600">{import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'}</span>
            </div>
            <div>
              <span className="font-medium text-gray-700">Environment:</span>
              <span className="ml-2 text-gray-600">{import.meta.env.MODE}</span>
            </div>
            <div>
              <span className="font-medium text-gray-700">Storage:</span>
              <span className="ml-2 text-gray-600">Browser LocalStorage</span>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Usage Guidelines */}
      <Card>
        <CardHeader>
          <CardTitle>API Key Guidelines</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-2 text-sm text-gray-600">
            <p>• Your API key is stored securely in your browser's local storage</p>
            <p>• The API key is required for all document management operations</p>
            <p>• Keep your API key confidential and do not share it with others</p>
            <p>• If you suspect your API key has been compromised, generate a new one immediately</p>
            <p>• The API key will persist across browser sessions until manually cleared</p>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}