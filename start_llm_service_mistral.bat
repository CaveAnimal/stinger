@echo off
REM Start Ministral 3B Model Service
REM Replaces the 7B version
REM Restricted to 4GB VRAM (3B model fits easily in 4GB with full offload)

set MODEL_PATH=E:\MyProjects\MyGitHubCopilot\firestick\fstk-001\models\mistral\models\Ministral-3-3B-Instruct-2512-Q4_K_M.gguf

if not exist "%MODEL_PATH%" (
    echo Error: Model file not found at %MODEL_PATH%
    echo Please verify the path.
    pause
    exit /b 1
)

echo Starting LLM Service with Ministral 3B...
echo Model: %MODEL_PATH%

REM --n_gpu_layers -1 offloads all layers to GPU. 
REM Ministral 3B Q4 is ~2GB, so it fits within the 4GB limit requested.
.\.venv\Scripts\python.exe -m llama_cpp.server --model "%MODEL_PATH%" --n_gpu_layers -1 --chat_format chatml --port 8081
pause
