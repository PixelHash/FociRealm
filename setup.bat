@echo off
echo =========================================
echo   🔥 FociRealm AI Setup for Windows 🔥
echo =========================================
echo.

:: 1. Check if Ollama is installed by looking for its command
where ollama >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [!] Ollama is not installed. Downloading the official installer...
    
    :: Windows 10/11 has curl built-in! Download the .exe silently
    curl -L -o OllamaSetup.exe https://ollama.com/download/OllamaSetup.exe
    
    echo [!] Launching the Ollama Installer...
    echo Please complete the setup wizard that pops up, then come back here!
    
    :: Wait for them to finish installing
    start /wait OllamaSetup.exe
    
    :: Clean up the downloaded file so we don't leave trash on their PC
    echo [!] Cleaning up installer...
    del OllamaSetup.exe
) else (
    echo [x] Awesome! Ollama is already installed on this machine.
)

echo.
:: 2. Pull the required lightweight model
:: (Make sure to change 'phi3' if your AIService uses llama3 or something else!)
echo 🧠 Downloading the FociRealm AI Model...
echo This might take a few minutes depending on your internet speed.
ollama pull phi3

echo.
echo ✨ Setup Complete! You can now run the FociRealm app.
pause
