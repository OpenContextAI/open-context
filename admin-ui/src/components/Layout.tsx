import React from 'react'
import { Link, useLocation } from 'react-router-dom'
import { 
  Home, 
  FileText, 
  Search, 
  Settings
} from 'lucide-react'
import { cn } from '../lib/utils'

interface LayoutProps {
  children: React.ReactNode
}

const navigation = [
  { name: 'Dashboard', href: '/', icon: Home },
  { name: 'Documents', href: '/documents', icon: FileText },
  { name: 'Search', href: '/search', icon: Search },
  { name: 'Settings', href: '/settings', icon: Settings },
]

export const Layout: React.FC<LayoutProps> = ({ children }) => {
  const location = useLocation()

  return (
    <div className="min-h-screen bg-gray-50 flex">
      {/* Sidebar */}
      <div className="fixed inset-y-0 left-0 w-64 bg-white shadow-lg z-30">
        <div className="flex h-full flex-col">
          {/* Logo */}
          <div className="flex h-16 items-center px-6 border-b">
            <Link to="/" className="flex items-center hover:opacity-80 transition-opacity">
              <div className="h-8 w-8 bg-blue-600 rounded-lg flex items-center justify-center">
                <span className="text-white font-bold text-sm">OC</span>
              </div>
              <span className="ml-3 text-xl font-semibold text-gray-900">
                OpenContext
              </span>
            </Link>
          </div>

          {/* Navigation */}
          <nav className="flex-1 px-4 py-6 space-y-2">
            {navigation.map((item) => {
              const Icon = item.icon
              const isActive = location.pathname === item.href
              
              return (
                <Link
                  key={item.name}
                  to={item.href}
                  className={cn(
                    'flex items-center px-3 py-2 text-sm font-medium rounded-md transition-colors',
                    isActive
                      ? 'bg-blue-50 text-blue-700 border-r-2 border-blue-700'
                      : 'text-gray-600 hover:text-gray-900 hover:bg-gray-50'
                  )}
                >
                  <Icon className="mr-3 h-5 w-5" />
                  {item.name}
                </Link>
              )
            })}
          </nav>

          {/* Footer */}
          <div className="px-6 py-4 border-t">
            <p className="text-xs text-gray-500">
              OpenContext Admin v1.0
            </p>
          </div>
        </div>
      </div>

      {/* Main content */}
      <div className="flex-1 ml-64">
        <main className="min-h-screen">
          <div className="max-w-7xl mx-auto">
            {children}
          </div>
        </main>
      </div>
    </div>
  )
}