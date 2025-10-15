# Social Login Configuration Guide

This guide explains how to configure social login providers (Apple, Google, LinkedIn, Facebook) for the EasyLuxury application using Supabase.

## Overview

The application now supports social login through Supabase's OAuth integration. Users can sign in using their Apple, Google, LinkedIn, or Facebook accounts.

## Frontend Implementation

### Components Added

1. **SocialLoginButtons Component** (`/frontend/src/components/authentication/auth-forms/SocialLoginButtons.tsx`)
   - Provides OAuth buttons for all supported providers
   - Handles OAuth flow initiation
   - Includes proper error handling and loading states

2. **OAuth Callback Handler** (`/frontend/src/app/auth/callback/page.tsx`)
   - Processes OAuth authentication responses
   - Redirects users to dashboard on success
   - Handles authentication errors gracefully

3. **Updated Login Form** (`/frontend/src/components/authentication/auth-forms/AuthSupabaseLogin.tsx`)
   - Integrated social login buttons
   - Maintains existing email/password functionality

### Supabase Client Configuration

The Supabase client has been updated with:
- `detectSessionInUrl: true` - Automatically detects session from URL parameters
- `flowType: 'pkce'` - Uses PKCE flow for enhanced security

## Backend Configuration

### CORS Updates

The backend CORS configuration has been updated to support:
- HTTPS localhost URLs for OAuth callbacks
- Proper credential handling for OAuth flows

### JWT Authentication

The existing JWT authentication filter (`SupabaseJwtAuthenticationFilter`) already supports:
- Token validation with Supabase
- User creation from OAuth providers
- Proper session management

## Supabase Configuration

### Required Setup Steps

1. **Enable OAuth Providers in Supabase Dashboard**

   Go to Authentication > Providers in your Supabase dashboard and enable:

   #### Google OAuth
   - Enable Google provider
   - Add your Google OAuth credentials:
     - Client ID
     - Client Secret
   - Set redirect URL: `https://your-project.supabase.co/auth/v1/callback`

   #### Apple OAuth
   - Enable Apple provider
   - Add your Apple OAuth credentials:
     - Client ID
     - Client Secret
   - Set redirect URL: `https://your-project.supabase.co/auth/v1/callback`

   #### LinkedIn OAuth
   - Enable LinkedIn provider
   - Add your LinkedIn OAuth credentials:
     - Client ID
     - Client Secret
   - Set redirect URL: `https://your-project.supabase.co/auth/v1/callback`

   #### Facebook OAuth
   - Enable Facebook provider
   - Add your Facebook OAuth credentials:
     - Client ID
     - Client Secret
   - Set redirect URL: `https://your-project.supabase.co/auth/v1/callback`

2. **Configure Site URL**

   In Supabase Dashboard > Authentication > URL Configuration:
   - Set Site URL to your frontend URL (e.g., `http://localhost:3000` for development)
   - Add redirect URLs for OAuth callbacks

3. **OAuth Provider Setup**

   Each provider requires specific setup:

   #### Google OAuth Setup
   1. Go to [Google Cloud Console](https://console.cloud.google.com/)
   2. Create a new project or select existing
   3. Enable Google+ API
   4. Create OAuth 2.0 credentials
   5. Add authorized redirect URIs:
      - `https://your-project.supabase.co/auth/v1/callback`
   6. Copy Client ID and Client Secret to Supabase

   #### Apple OAuth Setup
   1. Go to [Apple Developer Console](https://developer.apple.com/)
   2. Create a new App ID
   3. Enable Sign In with Apple capability
   4. Create a Service ID
   5. Configure domains and redirect URLs
   6. Generate a private key
   7. Copy credentials to Supabase

   #### LinkedIn OAuth Setup
   1. Go to [LinkedIn Developer Portal](https://www.linkedin.com/developers/)
   2. Create a new app
   3. Add OAuth 2.0 redirect URLs:
      - `https://your-project.supabase.co/auth/v1/callback`
   4. Request access to required scopes
   5. Copy Client ID and Client Secret to Supabase

   #### Facebook OAuth Setup
   1. Go to [Facebook Developers](https://developers.facebook.com/)
   2. Create a new app
   3. Add Facebook Login product
   4. Configure OAuth redirect URIs:
      - `https://your-project.supabase.co/auth/v1/callback`
   5. Set app domains and privacy policy URL
   6. Copy App ID and App Secret to Supabase

## Environment Variables

### Frontend (.env.local)
```env
NEXT_PUBLIC_SUPABASE_URL=https://your-project.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=your-anon-key
NEXT_PUBLIC_API_URL=http://localhost:8080/api
```

### Backend (.env.prod)
```env
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_API_KEY=your-anon-key
SUPABASE_SERVICE_ROLE_KEY=your-service-role-key
```

## Testing Social Login

1. **Start the application**
   ```bash
   # Frontend
   cd frontend
   npm run dev

   # Backend
   cd backend
   ./mvnw spring-boot:run
   ```

2. **Navigate to login page**
   - Go to `http://localhost:3000/login`
   - You should see social login buttons below the email/password form

3. **Test OAuth flow**
   - Click on any social login button
   - You'll be redirected to the provider's OAuth page
   - After authentication, you'll be redirected back to the app
   - The callback handler will process the authentication and redirect to dashboard

## Troubleshooting

### Common Issues

1. **OAuth Provider Not Configured**
   - Ensure the provider is enabled in Supabase dashboard
   - Verify OAuth credentials are correct
   - Check redirect URLs match exactly

2. **CORS Errors**
   - Verify frontend URL is in allowed origins
   - Check that HTTPS is used for production OAuth callbacks

3. **Authentication Failures**
   - Check Supabase logs for detailed error messages
   - Verify JWT token validation in backend logs
   - Ensure user creation logic handles OAuth users properly

4. **Redirect Issues**
   - Verify callback URL is correctly configured
   - Check that the callback page exists and is accessible
   - Ensure proper error handling in callback component

### Debug Mode

Enable debug logging by setting:
```env
NEXT_PUBLIC_DEBUG_MODE=true
```

This will provide detailed logs for authentication flows.

## Security Considerations

1. **OAuth Credentials**
   - Never commit OAuth credentials to version control
   - Use environment variables for all sensitive data
   - Rotate credentials regularly

2. **Redirect URLs**
   - Only allow trusted domains in redirect URLs
   - Use HTTPS in production
   - Validate redirect URLs on the server side

3. **Token Handling**
   - JWT tokens are automatically validated by Supabase
   - Tokens are stored securely in browser storage
   - Automatic token refresh is enabled

## Production Deployment

1. **Update CORS Configuration**
   - Add production frontend URL to allowed origins
   - Update Supabase site URL for production
   - Configure production OAuth redirect URLs

2. **Environment Variables**
   - Set production Supabase credentials
   - Configure production API URLs
   - Enable proper logging levels

3. **OAuth Provider Configuration**
   - Update OAuth apps with production URLs
   - Configure production redirect URIs
   - Test OAuth flow in production environment

## Support

For issues with social login implementation:
1. Check Supabase documentation for OAuth setup
2. Verify OAuth provider configuration
3. Review application logs for detailed error messages
4. Test OAuth flow step by step
