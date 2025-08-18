# Build stage
FROM node:18-alpine AS builder

WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies
RUN npm ci

# Copy source code
COPY . .

# Build the application
RUN npx vite build

# Production stage
FROM node:18-alpine

# Install serve globally
RUN npm install -g serve

# Copy built files from builder stage
COPY --from=builder /app/dist /app/dist

# Set working directory
WORKDIR /app

# Expose port 80
EXPOSE 80

# Start serve with SPA support
CMD ["serve", "-s", "dist", "-l", "80", "--single"]
