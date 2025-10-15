#!/bin/bash

# Navigate to backend directory
cd "$(dirname "$0")/backend"

echo "================================"
echo "Starting EasyLuxury Backend"
echo "================================"
echo ""

# Check if .env exists
if [ ! -f ".env" ]; then
    echo "ERROR: .env file not found!"
    echo "Please copy .env.example to .env and configure it"
    exit 1
fi

# Source environment variables
set -a
source .env
set +a

echo "Environment loaded"
echo "Database: $DATABASE_URL"
echo "Supabase: $SUPABASE_URL"
echo ""

# Run Liquibase migrations
echo "Running database migrations..."
mvn liquibase:update

echo ""
echo "Starting Spring Boot application..."
echo "Backend will be available at: http://localhost:8080"
echo "Swagger UI: http://localhost:8080/swagger-ui.html"
echo ""
echo "Press Ctrl+C to stop"
echo ""

# Start Spring Boot
mvn spring-boot:run
