#!/bin/bash
# Cleanup script for test temporary files
# Prevents disk space issues from Maven Surefire/Failsafe temp files
# and ONNX model extraction files

set -e

echo "🧹 Cleaning temporary test files..."

# Detect temp directory
if [ -n "$TMPDIR" ]; then
    TEMP_DIR="$TMPDIR"
elif [ -d "/tmp" ]; then
    TEMP_DIR="/tmp"
else
    TEMP_DIR=$(mktemp -d)
fi

echo "📁 Temp directory: $TEMP_DIR"

# Clean Maven Surefire temp files
echo "Cleaning Maven Surefire temp files..."
if [ -d "$TEMP_DIR" ]; then
    # Remove surefire directories older than 1 day
    find "$TEMP_DIR" -name "surefire-*" -type d -mtime +1 -exec rm -rf {} + 2>/dev/null || true
    
    # Remove stdout/stderr deferred files older than 1 day
    find "$TEMP_DIR" -name "stdout-*" -type f -mtime +1 -delete 2>/dev/null || true
    find "$TEMP_DIR" -name "stderr-*" -type f -mtime +1 -delete 2>/dev/null || true
    
    # Count remaining files
    REMAINING=$(find "$TEMP_DIR" -name "surefire-*" -o -name "stdout-*" -o -name "stderr-*" 2>/dev/null | wc -l | tr -d ' ')
    echo "   Remaining surefire files: $REMAINING"
fi

# Clean ONNX model temp files (keep recent ones for reuse)
echo "Cleaning ONNX model temp files..."
if [ -d "$TEMP_DIR" ]; then
    # Remove ONNX model files older than 1 day
    DELETED=$(find "$TEMP_DIR" -name "onnx-*" -type f -mtime +1 -delete -print 2>/dev/null | wc -l | tr -d ' ')
    echo "   Deleted $DELETED old ONNX model files"
    
    # Show remaining ONNX files
    REMAINING=$(find "$TEMP_DIR" -name "onnx-*" -type f 2>/dev/null | wc -l | tr -d ' ')
    if [ "$REMAINING" -gt 0 ]; then
        echo "   Remaining ONNX files: $REMAINING"
        find "$TEMP_DIR" -name "onnx-*" -type f -exec ls -lh {} \; 2>/dev/null | awk '{print "     " $5 " " $9}'
    fi
fi

# Clean embedded-pg temp files
echo "Cleaning embedded Postgres temp files..."
if [ -d "$TEMP_DIR" ]; then
    # Remove embedded-pg directories older than 1 day
    DELETED=$(find "$TEMP_DIR" -name "embedded-pg" -type d -mtime +1 -exec rm -rf {} + -print 2>/dev/null | wc -l | tr -d ' ')
    if [ "$DELETED" -gt 0 ]; then
        echo "   Deleted $DELETED embedded-pg directories"
    fi
fi

# Show disk space
echo ""
echo "💾 Disk space after cleanup:"
df -h / | tail -1 | awk '{print "   Available: " $4 " (" $5 " used)"}'

echo ""
echo "✅ Temporary file cleanup completed!"

