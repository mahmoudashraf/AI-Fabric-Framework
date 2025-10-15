#!/bin/bash

# Social Login Quick Test Script
# This script helps test the social login functionality

echo "🚀 Social Login Quick Test"
echo "=========================="
echo ""

# Check if frontend is running
echo "📋 Checking frontend status..."
if curl -s http://localhost:3000 > /dev/null; then
    echo "✅ Frontend is running on http://localhost:3000"
else
    echo "❌ Frontend is not running. Please start it with:"
    echo "   cd frontend && npm run dev"
    echo ""
fi

# Check if backend is running
echo "📋 Checking backend status..."
if curl -s http://localhost:8080/api/health > /dev/null; then
    echo "✅ Backend is running on http://localhost:8080"
else
    echo "❌ Backend is not running. Please start it with:"
    echo "   cd backend && ./mvnw spring-boot:run"
    echo ""
fi

echo ""
echo "🔧 Supabase Configuration Check:"
echo "================================"

# Check if Supabase environment variables are set
if [ -f "frontend/.env.local" ]; then
    echo "✅ Frontend .env.local exists"
    
    if grep -q "NEXT_PUBLIC_SUPABASE_URL" frontend/.env.local; then
        echo "✅ NEXT_PUBLIC_SUPABASE_URL is configured"
    else
        echo "❌ NEXT_PUBLIC_SUPABASE_URL is missing"
    fi
    
    if grep -q "NEXT_PUBLIC_SUPABASE_ANON_KEY" frontend/.env.local; then
        echo "✅ NEXT_PUBLIC_SUPABASE_ANON_KEY is configured"
    else
        echo "❌ NEXT_PUBLIC_SUPABASE_ANON_KEY is missing"
    fi
else
    echo "❌ Frontend .env.local not found"
    echo "   Create it with your Supabase credentials"
fi

echo ""
echo "🧪 Testing Instructions:"
echo "======================="
echo "1. Make sure both frontend and backend are running"
echo "2. Navigate to http://localhost:3000/login"
echo "3. You should see social login buttons below the email/password form"
echo "4. Click on any social login button to test OAuth flow"
echo ""
echo "📚 For detailed setup instructions, see:"
echo "   docs/SOCIAL_LOGIN_SETUP_GUIDE.md"
echo ""
echo "🔍 To test social login components in isolation:"
echo "   Add <SocialLoginTest /> to any page temporarily"
echo ""

# Check if test component exists
if [ -f "frontend/src/components/testing/SocialLoginTest.tsx" ]; then
    echo "✅ SocialLoginTest component is available"
else
    echo "❌ SocialLoginTest component not found"
fi

echo ""
echo "🎯 Next Steps:"
echo "=============="
echo "1. Configure OAuth providers in Supabase Dashboard"
echo "2. Add OAuth credentials for each provider"
echo "3. Test the OAuth flow with each provider"
echo "4. Verify user creation in your database"
echo ""
