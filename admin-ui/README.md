# OpenContext Admin UI

React-based administration interface for OpenContext knowledge management system.

## Features

- **Dashboard**: Real-time statistics and overview of document processing status
- **Document Manager**: Upload, manage, and monitor document ingestion pipeline
- **Search Interface**: Test MCP search tools (find_knowledge and get_content)
- **Settings**: API key management and system configuration

## Tech Stack

- React 19.1.1
- TypeScript 5.8.3
- Vite 7.1.2
- Tailwind CSS 3.4.17
- React Router DOM 7.8.1
- React Query 5.85.3
- Zustand 5.0.7
- Axios 1.11.0
- Lucide React 0.539.0

## Development

### Prerequisites

- Node.js 18+ 
- npm or yarn
- OpenContext backend running on localhost:8080

### Setup

```bash
# Install dependencies
npm install

# Start development server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

### Environment Variables

```bash
# Optional - defaults to localhost:8080
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

## Usage

1. Start the backend server (OpenContext Core)
2. Run the frontend development server
3. Navigate to http://localhost:5173
4. Configure API key in Settings page
5. Upload documents and test search functionality

## API Integration

The admin UI integrates with OpenContext backend APIs:

- **Admin APIs**: Require X-API-KEY header authentication
- **Search APIs**: Public endpoints for MCP tool testing
- **File Upload**: Multipart form data with progress tracking

## Architecture

- **Components**: Reusable UI components in `/src/components/ui/`
- **Pages**: Main application views in `/src/pages/`
- **API Client**: Centralized HTTP client in `/src/lib/api.ts`
- **State Management**: Zustand store for authentication state
- **Types**: TypeScript definitions in `/src/types/`