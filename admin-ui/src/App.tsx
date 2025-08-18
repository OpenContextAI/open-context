import { BrowserRouter as Router, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Layout } from './components/Layout'
import { Dashboard } from './pages/Dashboard'
import { DocumentManager } from './pages/DocumentManager'
import { Search } from './pages/Search'
import { Settings } from './pages/Settings'
import { ComponentsDemo } from './components/ComponentsDemo'

// Create a query client instance
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
})

/**
 * Main App component with routing and React Query setup
 */
function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <Router>
        <div className="App">
          <Routes>
            {/* Layout wrapper for main application routes */}
            <Route path="/" element={<Layout><Dashboard /></Layout>} />
            <Route path="/documents" element={<Layout><DocumentManager /></Layout>} />
            <Route path="/search" element={<Layout><Search /></Layout>} />
            <Route path="/settings" element={<Layout><Settings /></Layout>} />
            
            {/* Components demo page (without layout) */}
            <Route path="/components" element={<ComponentsDemo />} />
          </Routes>
        </div>
      </Router>
    </QueryClientProvider>
  )
}

export default App
