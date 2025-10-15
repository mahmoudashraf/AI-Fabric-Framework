#!/bin/bash

# EasyLuxury Database Cleanup Script
# This script clears all data from the database for development purposes

echo "================================="
echo "🧹 EasyLuxury Database Cleanup"
echo "================================="

# Find the PostgreSQL container name
POSTGRES_CONTAINER=$(docker ps --format "table {{.Names}}" | grep -E "easyluxury.*db" | head -1)

if [ -z "$POSTGRES_CONTAINER" ]; then
    echo "❌ PostgreSQL container is not running. Please start it first:"
    echo "   ./dev.sh (to start development environment)"
    echo "   or: docker-compose up -d postgres"
    exit 1
fi

echo "📦 Found PostgreSQL container: $POSTGRES_CONTAINER"

echo "⚠️  WARNING: This will delete ALL data from the database!"
echo "Are you sure you want to continue? (y/N)"
read -r response

if [[ ! "$response" =~ ^[Yy]$ ]]; then
    echo "❌ Operation cancelled."
    exit 0
fi

echo "🗑️  Cleaning database..."

# Connect to PostgreSQL and clean all tables
docker exec -i "$POSTGRES_CONTAINER" psql -U postgres -d easyluxury << EOF
-- Disable foreign key checks temporarily
SET session_replication_role = replica;

-- Clear all tables in reverse dependency order
DELETE FROM property_media;
DELETE FROM properties;
DELETE FROM projects;
DELETE FROM listings;
DELETE FROM bids;
DELETE FROM agency_members;
DELETE FROM agencies;
DELETE FROM style_images;
DELETE FROM style_packages;
DELETE FROM users;

-- Re-enable foreign key checks
SET session_replication_role = DEFAULT;

-- Reset sequences (if any)
-- Note: PostgreSQL doesn't have sequences for UUID primary keys

-- Show table counts
SELECT 'users' as table_name, COUNT(*) as count FROM users
UNION ALL
SELECT 'properties', COUNT(*) FROM properties
UNION ALL
SELECT 'projects', COUNT(*) FROM projects
UNION ALL
SELECT 'listings', COUNT(*) FROM listings
UNION ALL
SELECT 'agencies', COUNT(*) FROM agencies
UNION ALL
SELECT 'agency_members', COUNT(*) FROM agency_members
UNION ALL
SELECT 'style_packages', COUNT(*) FROM style_packages
UNION ALL
SELECT 'style_images', COUNT(*) FROM style_images
UNION ALL
SELECT 'property_media', COUNT(*) FROM property_media
UNION ALL
SELECT 'bids', COUNT(*) FROM bids;
EOF

if [ $? -eq 0 ]; then
    echo "✅ Database cleaned successfully!"
    echo ""
    echo "📝 Next steps:"
    echo "   1. Restart the backend to reload mock users"
    echo "   2. Use the UsersFeedService to populate mock data"
    echo "   3. Test your application with clean data"
else
    echo "❌ Database cleanup failed!"
    exit 1
fi

echo "================================="
echo "🎉 Cleanup Complete!"
echo "================================="