# Migration Environment Setup

## Feature Flags Configuration

Create a `.env.local` file in the frontend directory with the following settings:

```env
# React Query + Context API Migration Feature Flags

# Phase 2: Simple State Migration
NEXT_PUBLIC_MIGRATED_NOTIFICATIONS=false
NEXT_PUBLIC_MIGRATED_THEME=false
NEXT_PUBLIC_MIGRATED_MENU=false

# Phase 3: Complex State Migration
NEXT_PUBLIC_MIGRATED_CART=false
NEXT_PUBLIC_MIGRATED_KANBAN=false

# Development Features
NEXT_PUBLIC_DEBUG_MIGRATION=true
```

## Migration Testing Instructions

### Testing Snackbar Migration

1. Set `NEXT_PUBLIC_MIGRATED_NOTIFICATIONS=true` in `.env.local`
2. Restart the development server
3. Test snackbar functionality:
   - Navigate to components that trigger notifications
   - Verify notifications appear and function correctly
   - Check browser console for migration debug logs

### Testing Theme Migration

1. Set `NEXT_PUBLIC_MIGRATED_THEME=true` in `.env.local`
2. Restart the development server
3. Test theme customization:
   - Navigate to theme selector in the dashboard
   - Change theme settings (dark/light mode, colors)
   - Verify settings persist correctly

### Enabling All Migrations

For comprehensive testing, you can enable all completed migrations:

```env
NEXT_PUBLIC_MIGRATED_NOTIFICATIONS=true
NEXT_PUBLIC_MIGRATED_THEME=true
NEXT_PUBLIC_MIGRATED_MENU=true
NEXT_PUBLIC_DEBUG_MIGRATION=true
```

## Troubleshooting

### Common Issues

1. **Notifications not appearing after migration**
   - Check that NotificationProvider is wrapping the app correctly
   - Verify environment variable is set correctly
   - Check console for errors

2. **Theme settings not persisting**
   - Ensure localStorage is available
   - Check ThemeContext is properly configured
   - Verify migration flag is enabled

3. **Performance issues**
   - Monitor re-render counts with migration debug logging
   - Check for unnecessary context re-renders
   - Ensure memoization is working correctly

### Debug Information

Enable debug logging to see migration activity:
- Console logs show which implementation is being used
- State comparison utilities track migration progress
- Performance monitoring shows render counts

## Production Deployment

For production, set environment variables in your deployment environment:

```env
NEXT_PUBLIC_MIGRATED_NOTIFICATIONS=true
NEXT_PUBLIC_MIGRATED_THEME=true
NEXT_PUBLIC_DEBUG_MIGRATION=false
```

This enables migrations while disabling debug logging for production performance.
