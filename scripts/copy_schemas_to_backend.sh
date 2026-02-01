#!/bin/bash
# scripts/copy_schemas_to_backend.sh

# Get project root (parent of script dir)
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE_DIR="$PROJECT_ROOT/ndc_schemas"
BACKEND_RES_DIR="$PROJECT_ROOT/backend/src/main/resources/schemas"

# Create destination directory
mkdir -p "$BACKEND_RES_DIR"

echo "Copying schemas from $SOURCE_DIR to $BACKEND_RES_DIR..."

# Copy recursively
cp -r "$SOURCE_DIR"/* "$BACKEND_RES_DIR"/

echo "Schemas copied successfully."
