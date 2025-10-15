#!/bin/bash

# Simple script to start a PHP development server for the test webapp

echo "🚀 Starting PHP development server for InAppBrowser test webapp..."
echo "📁 Serving from: $(pwd)"
echo "🌐 Server will be available at: http://localhost:8000"
echo ""
echo "📝 Make sure to:"
echo "   1. Copy url.js.example to url.js in the example-app/src/js/ directory"
echo "   2. Update the URL in url.js to: http://localhost:8000/index.php"
echo "   3. Build and run the example app"
echo ""
echo "Press Ctrl+C to stop the server"
echo ""

# Start PHP built-in server
php -S localhost:8000
