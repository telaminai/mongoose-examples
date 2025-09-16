#!/bin/bash

# Get the directory where the script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Change to the script directory
cd "$SCRIPT_DIR"

echo "Cleaning up Mongoose data files..."

# Delete all *.readPointer files in data-in directory
echo "Deleting *.readPointer files from data-in directory..."
rm -f data-in/*.readPointer

# Remove all files from data-out directory
echo "Removing all files from data-out directory..."
rm -f data-out/*

echo "Cleanup completed successfully."