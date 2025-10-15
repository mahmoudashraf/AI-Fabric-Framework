#!/bin/bash

# Production Frontend Runner
# This script runs the frontend in production mode with Supabase authentication

echo "🚀 Starting Frontend in Production Mode with Supabase Authentication..."

# Set environment variables for production
export NODE_ENV=production
export NEXT_PUBLIC_ENVIRONMENT=production
export NEXT_PUBLIC_MOCK_AUTH_ENABLED=false
export NEXT_PUBLIC_API_URL=https://api.easyluxury.com
export NEXT_PUBLIC_ENABLE_MOCK_USER_TESTER=false
export NEXT_PUBLIC_DEBUG_MODE=false

# Enable Supabase in production (you'll need to set these)
export NEXT_PUBLIC_SUPABASE_URL=${NEXT_PUBLIC_SUPABASE_URL:-"https://your-project.supabase.co"}
export NEXT_PUBLIC_SUPABASE_ANON_KEY=${NEXT_PUBLIC_SUPABASE_ANON_KEY:-"your-anon-key"}

echo "📋 Production Configuration:"
echo "  - Environment: $NEXT_PUBLIC_ENVIRONMENT"
echo "  - Mock Auth: $NEXT_PUBLIC_MOCK_AUTH_ENABLED"
echo "  - API URL: $NEXT_PUBLIC_API_URL"
echo "  - Debug Mode: $NEXT_PUBLIC_DEBUG_MODE"
echo "  - Mock User Tester: $NEXT_PUBLIC_ENABLE_MOCK_USER_TESTER"
echo "  - Supabase URL: $NEXT_PUBLIC_SUPABASE_URL"
echo ""

# Change to frontend directory
cd /Users/mahmoudashraf/Downloads/easy-luxury/frontend

# Install dependencies if needed
if [ ! -d "node_modules" ]; then
    echo "📦 Installing dependencies..."
    npm install
fi

# Build and start the production server
echo "🏗️ Building Next.js application..."
npm run build

echo "🌐 Starting Next.js production server..."
npm start
