#!/bin/bash
echo "🔥 FociRealm AI Setup 🔥"

# 1. Check if Ollama is installed
if ! command -v ollama &> /dev/null
then
    echo "🤖 Ollama is not installed. Downloading now..."
    curl -fsSL https://ollama.com/install.sh | sh
else
    echo "✅ Ollama is already installed."
fi

# 2. Pull the required lightweight model
# (Change 'phi3' to whatever model your AIService actually uses!)
echo "🧠 Downloading the FociRealm AI Model..."
ollama pull llama3.2

echo "✨ Setup Complete! You can now run the FociRealm app."
