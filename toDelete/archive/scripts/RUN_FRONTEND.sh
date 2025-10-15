#!/bin/bash

# Navigate to frontend directory
cd "$(dirname "$0")/frontend"

echo "================================"
echo "Starting EasyLuxury Frontend"
echo "================================"
echo ""

# Check if .env.local exists
if [ ! -f ".env.local" ]; then
    echo "ERROR: .env.local file not found!"
    echo "Please copy .env.example to .env.local and configure it"
    exit 1
fi

echo "Environment configured"
echo ""

# Check if node_modules exists
if [ ! -d "node_modules" ]; then
    echo "Installing dependencies..."
    npm install
    echo ""
fi

echo "Starting Next.js development server..."
echo "Frontend will be available at: http://localhost:3000"
echo ""
echo "Press Ctrl+C to stop"
echo ""

# Start Next.js
npm run dev
