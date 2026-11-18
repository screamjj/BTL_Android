# SpendMate AI backend

    pip install -r requirements.txt
    export GEMINI_API_KEY=...   APP_TOKEN=...      # Windows: set / $env:
    uvicorn main:app --host 0.0.0.0 --port 8000

Kiểm tra: http://localhost:8000/health
Emulator Android gọi qua http://10.0.2.2:8000 ; máy thật dùng IP LAN của máy chạy server
(hoặc deploy lên Render/Railway/Cloud Run rồi dùng https).
