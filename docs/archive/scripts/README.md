# Archived Startup Scripts

This directory contains the **deprecated startup scripts** that have been replaced by the new unified script structure.

## Archived Scripts

### `run.sh`
- **Purpose**: Comprehensive development startup script
- **Replaced by**: `./dev.sh`
- **Reason**: New script has better error handling and clearer output

### `RUN_BACKEND.sh`
- **Purpose**: Simple backend startup script
- **Replaced by**: `./dev.sh` or `./prod.sh`
- **Reason**: Integrated into unified startup scripts

### `RUN_FRONTEND.sh`
- **Purpose**: Simple frontend startup script
- **Replaced by**: `./dev.sh` or `./prod.sh`
- **Reason**: Integrated into unified startup scripts

### `RUN_FRONTEND_DEV.sh`
- **Purpose**: Frontend development startup with mock auth
- **Replaced by**: `./dev.sh`
- **Reason**: Integrated into unified development script

### `RUN_FRONTEND_PROD.sh`
- **Purpose**: Frontend production startup with Supabase auth
- **Replaced by**: `./prod.sh`
- **Reason**: Integrated into unified production script

### `START_SERVICES.sh`
- **Purpose**: Manual instructions for starting services
- **Replaced by**: `./dev.sh` or `./prod.sh`
- **Reason**: New scripts actually start services instead of just providing instructions

## Current Active Scripts

The following scripts are now the **active startup scripts** in the root directory:

- **`./dev.sh`** - Start everything in development mode
- **`./prod.sh`** - Start everything in production mode
- **`./status.sh`** - Check service status
- **`./stop.sh`** - Stop services
- **`./CLEAN_DATABASE.sh`** - Database cleanup utility

## Migration Guide

If you were using any of the archived scripts, here's how to migrate:

| Old Script | New Command |
|------------|-------------|
| `./run.sh` | `./dev.sh` |
| `./RUN_FRONTEND_DEV.sh` | `./dev.sh` |
| `./RUN_FRONTEND_PROD.sh` | `./prod.sh` |
| `./RUN_BACKEND.sh` | `./dev.sh` or `./prod.sh` |
| `./RUN_FRONTEND.sh` | `./dev.sh` or `./prod.sh` |
| `./START_SERVICES.sh` | `./dev.sh` or `./prod.sh` |

## Why Archive?

The old scripts were archived because:

1. **Redundancy**: Multiple scripts did similar things
2. **Confusion**: Hard to know which script to use
3. **Inconsistency**: Different environment variable handling
4. **Maintenance**: Multiple scripts to maintain
5. **User Experience**: Simplified to just 4 core scripts

## Recovery

If you need to recover any of these scripts, they are preserved here with their original functionality intact. However, we recommend using the new unified scripts for better reliability and user experience.
