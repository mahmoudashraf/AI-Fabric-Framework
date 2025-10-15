#!/bin/bash

# Development Frontend Runner
# This script runs the frontend in development mode with mock authentication enabled

echo "🚀 Starting Frontend in Development Mode with Mock Authentication..."

# Set environment variables for development
export NODE_ENV=development
export NEXT_PUBLIC_ENVIRONMENT=development
export NEXT_PUBLIC_MOCK_AUTH_ENABLED=true
export NEXT_PUBLIC_API_URL=http://localhost:8080/api
export NEXT_PUBLIC_MOCK_USERS_ENDPOINT=/mock/users
export NEXT_PUBLIC_MOCK_AUTH_ENDPOINT=/mock/auth
export NEXT_PUBLIC_ENABLE_MOCK_USER_TESTER=true
export NEXT_PUBLIC_DEBUG_MODE=true

# Disable Supabase in development
export NEXT_PUBLIC_SUPABASE_URL=
export NEXT_PUBLIC_SUPABASE_ANON_KEY=

echo "📋 Development Configuration:"
echo "  - Environment: $NEXT_PUBLIC_ENVIRONMENT"
echo "  - Mock Auth: $NEXT_PUBLIC_MOCK_AUTH_ENABLED"
echo "  - API URL: $NEXT_PUBLIC_API_URL"
echo "  - Debug Mode: $NEXT_PUBLIC_DEBUG_MODE"
echo "  - Mock User Tester: $NEXT_PUBLIC_ENABLE_MOCK_USER_TESTER"
echo ""

# Change to frontend directory
cd /Users/mahmoudashraf/Downloads/easy-luxury/frontend

# Install dependencies if needed
if [ ! -d "node_modules" ]; then
    echo "📦 Installing dependencies..."
    npm install
fi

# Start the development server
echo "🌐 Starting Next.js development server..."
npm run dev
