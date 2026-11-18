"""SpendMate AI proxy.

Giữ API key Gemini ở server, nhận lịch sử hội thoại từ app, gọi Gemini với
danh sách tool, rồi trả về text hoặc yêu cầu gọi hàm. Các hàm nghiệp vụ chạy
trên app (dữ liệu nằm trong Room của người dùng), nên server không đọc và
không lưu dữ liệu tài chính.
"""
import os
import time
from collections import defaultdict, deque

import httpx
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel

GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "")
GEMINI_MODEL = os.environ.get("GEMINI_MODEL", "gemini-2.5-flash")
APP_TOKEN = os.environ.get("APP_TOKEN", "")          # token dùng chung giữa app và server
MAX_CONTENTS = int(os.environ.get("MAX_CONTENTS", "24"))   # giới hạn độ dài hội thoại
RATE_LIMIT_PER_MIN = int(os.environ.get("RATE_LIMIT_PER_MIN", "20"))

URL = f"https://generativelanguage.googleapis.com/v1beta/models/{GEMINI_MODEL}:generateContent"

SYSTEM_PROMPT = """Bạn là SpendMate, trợ lý tài chính cá nhân trong ứng dụng quản lý chi tiêu. Luôn trả lời bằng tiếng Việt, ngắn gọn, thân thiện, đúng trọng tâm.
Quy tắc:
- Mọi con số (tổng chi, thu nhập, ngân sách, số dư mục tiêu) PHẢI lấy từ kết quả của các hàm. Không tự đoán hay tự tính khi chưa có dữ liệu.
- Hôm nay là {today}. Tự đổi "hôm nay", "tuần này", "tháng trước"... thành start_date/end_date dạng YYYY-MM-DD khi gọi hàm.
- Thêm giao dịch: nếu thiếu số tiền thì hỏi lại, không tự bịa. Gọi create_transaction để tạo bản nháp, người dùng sẽ xác nhận trên thẻ giao dịch.
- Sửa hoặc xóa giao dịch: gọi find_transactions để xác định đúng giao dịch, nói rõ giao dịch nào sẽ bị thay đổi và hỏi người dùng xác nhận. Chỉ gọi update_transaction/delete_transaction với confirmed=true sau khi người dùng đồng ý.
- Khi tư vấn tiết kiệm: dựa trên dữ liệu thật (chi theo danh mục, ngân sách, mục tiêu tiết kiệm), nêu khoản nào có thể cắt giảm và gợi ý con số cụ thể.
- Tiền tệ là VNĐ, định dạng 50.000 đ. Không dùng bảng markdown, tối đa vài dòng gạch đầu dòng.
- Từ chối lịch sự các yêu cầu không liên quan đến tài chính cá nhân."""

DATE = {"type": "STRING", "description": "YYYY-MM-DD"}

TOOLS = [{"functionDeclarations": [
    {"name": "get_total_expenses",
     "description": "Tổng chi tiêu trong khoảng thời gian, có thể lọc theo danh mục.",
     "parameters": {"type": "OBJECT", "properties": {
         "start_date": DATE, "end_date": DATE,
         "category": {"type": "STRING", "description": "Tên danh mục (tùy chọn)"}},
         "required": ["start_date", "end_date"]}},
    {"name": "get_total_income",
     "description": "Tổng thu nhập trong khoảng thời gian.",
     "parameters": {"type": "OBJECT", "properties": {"start_date": DATE, "end_date": DATE},
                    "required": ["start_date", "end_date"]}},
    {"name": "get_expenses_by_category",
     "description": "Thống kê chi tiêu theo từng danh mục trong khoảng thời gian, sắp xếp giảm dần.",
     "parameters": {"type": "OBJECT", "properties": {"start_date": DATE, "end_date": DATE},
                    "required": ["start_date", "end_date"]}},
    {"name": "get_budget_status",
     "description": "Tình trạng ngân sách tháng hiện tại: hạn mức, đã dùng, còn lại, % sử dụng.",
     "parameters": {"type": "OBJECT", "properties": {
         "category": {"type": "STRING", "description": "Tên danh mục (tùy chọn, bỏ trống để xem tất cả)"}}}},
    {"name": "get_savings_goals",
     "description": "Danh sách mục tiêu tiết kiệm và tiến độ.",
     "parameters": {"type": "OBJECT", "properties": {}}},
    {"name": "find_transactions",
     "description": "Tìm giao dịch (trả về id) theo từ khóa, khoảng ngày, loại. Dùng trước khi sửa/xóa.",
     "parameters": {"type": "OBJECT", "properties": {
         "keyword": {"type": "STRING"}, "start_date": DATE, "end_date": DATE,
         "type": {"type": "STRING", "enum": ["EXPENSE", "INCOME"]},
         "limit": {"type": "INTEGER"}}}},
    {"name": "create_transaction",
     "description": "Tạo bản nháp giao dịch để người dùng xác nhận. Chưa lưu vào database.",
     "parameters": {"type": "OBJECT", "properties": {
         "title": {"type": "STRING", "description": "Nội dung, ví dụ 'Ăn trưa'"},
         "amount": {"type": "NUMBER", "description": "Số tiền VNĐ, ví dụ 50000"},
         "type": {"type": "STRING", "enum": ["EXPENSE", "INCOME"]},
         "category": {"type": "STRING", "description": "Tên danh mục phù hợp nhất"},
         "date": DATE},
         "required": ["title", "amount", "type"]}},
    {"name": "update_transaction",
     "description": "Sửa giao dịch đã có. Chỉ gọi với confirmed=true sau khi người dùng đồng ý.",
     "parameters": {"type": "OBJECT", "properties": {
         "id": {"type": "INTEGER"}, "title": {"type": "STRING"}, "amount": {"type": "NUMBER"},
         "category": {"type": "STRING"}, "date": DATE, "confirmed": {"type": "BOOLEAN"}},
         "required": ["id", "confirmed"]}},
    {"name": "delete_transaction",
     "description": "Xóa giao dịch. Chỉ gọi với confirmed=true sau khi người dùng đồng ý.",
     "parameters": {"type": "OBJECT", "properties": {
         "id": {"type": "INTEGER"}, "confirmed": {"type": "BOOLEAN"}},
         "required": ["id", "confirmed"]}},
]}]

app = FastAPI(title="SpendMate AI Proxy")
_hits: dict[str, deque] = defaultdict(deque)


class ChatRequest(BaseModel):
    contents: list[dict]      # lịch sử theo định dạng Gemini (role + parts)
    today: str                # yyyy-MM-dd theo giờ thiết bị
    client_id: str = "anon"   # chỉ dùng để giới hạn tần suất


def _rate_limit(client_id: str):
    now = time.time()
    q = _hits[client_id]
    while q and now - q[0] > 60:
        q.popleft()
    if len(q) >= RATE_LIMIT_PER_MIN:
        raise HTTPException(429, "Bạn gửi quá nhanh, vui lòng thử lại sau ít phút.")
    q.append(now)


def _trim(contents: list[dict]) -> list[dict]:
    """Giữ N lượt gần nhất, bắt đầu từ tin nhắn text của user để không làm vỡ cặp functionCall/Response."""
    if len(contents) <= MAX_CONTENTS:
        return contents
    cut = contents[-MAX_CONTENTS:]
    for i, c in enumerate(cut):
        parts = c.get("parts", [])
        if c.get("role") == "user" and parts and "text" in parts[0]:
            return cut[i:]
    return cut


@app.get("/health")
def health():
    return {"ok": True, "model": GEMINI_MODEL}


@app.post("/chat")
async def chat(req: ChatRequest, x_app_token: str = Header(default="")):
    if not GEMINI_API_KEY:
        raise HTTPException(500, "Server chưa cấu hình GEMINI_API_KEY.")
    if APP_TOKEN and x_app_token != APP_TOKEN:
        raise HTTPException(401, "Sai token.")
    if not req.contents:
        raise HTTPException(400, "contents rỗng.")
    _rate_limit(req.client_id)

    body = {
        "systemInstruction": {"parts": [{"text": SYSTEM_PROMPT.format(today=req.today)}]},
        "contents": _trim(req.contents),
        "tools": TOOLS,
        "generationConfig": {"temperature": 0.3, "maxOutputTokens": 800},
    }
    try:
        async with httpx.AsyncClient(timeout=30) as client:
            r = await client.post(URL, json=body, headers={"x-goog-api-key": GEMINI_API_KEY})
    except httpx.HTTPError:
        raise HTTPException(504, "Không kết nối được tới Gemini.")
    if r.status_code != 200:
        raise HTTPException(502, f"Gemini lỗi {r.status_code}")

    data = r.json()
    cands = data.get("candidates") or []
    if not cands or "content" not in cands[0]:
        raise HTTPException(502, "Gemini không trả về nội dung (có thể bị chặn bởi bộ lọc).")
    content = cands[0]["content"]
    parts = content.get("parts", [])
    return {
        "content": content,  # app lưu nguyên để gửi lại (giữ thought signature nếu có)
        "text": "".join(p.get("text", "") for p in parts if "functionCall" not in p),
        "function_calls": [p["functionCall"] for p in parts if "functionCall" in p],
        "tokens": data.get("usageMetadata", {}).get("totalTokenCount"),
    }
