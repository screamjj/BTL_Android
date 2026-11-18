package com.example.smartspend.ui.ai

import com.example.smartspend.data.local.entity.CategoryEntity
import com.example.smartspend.data.local.entity.TransactionEntity
import com.example.smartspend.data.repository.SmartSpendRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object SpendMateConfig {
    // Emulator: 10.0.2.2 trỏ về máy chạy server. Máy thật: đổi thành IP LAN hoặc URL https đã deploy.
    const val BASE_URL = "http://10.0.2.2:8000"
    // Phải trùng APP_TOKEN trên server. Đây chỉ là chốt chặn nhẹ, KHÔNG phải nơi giữ API key Gemini.
    const val APP_TOKEN = "doi-chuoi-nay"
    const val MAX_TOOL_ROUNDS = 5
    const val MAX_HISTORY = 30
}

class AiTurn(val content: JSONObject, val text: String, val calls: List<Pair<String, JSONObject>>)

/** Gọi backend proxy. Không chứa API key của Gemini. */
class SpendMateAiClient {
    suspend fun chat(contents: JSONArray, today: String): AiTurn = withContext(Dispatchers.IO) {
        val conn = (URL("${SpendMateConfig.BASE_URL}/chat").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 35_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("X-App-Token", SpendMateConfig.APP_TOKEN)
        }
        try {
            val body = JSONObject().put("contents", contents).put("today", today).put("client_id", "android")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode != 200) {
                val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                throw RuntimeException("AI backend ${conn.responseCode}: $err")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val calls = json.optJSONArray("function_calls") ?: JSONArray()
            AiTurn(
                content = json.getJSONObject("content"),
                text = json.optString("text", ""),
                calls = (0 until calls.length()).map {
                    val c = calls.getJSONObject(it)
                    c.getString("name") to (c.optJSONObject("args") ?: JSONObject())
                }
            )
        } finally {
            conn.disconnect()
        }
    }
}

class ToolResult(val json: JSONObject, val draft: ChatMessage.DraftCard? = null)

/**
 * Các hàm nghiệp vụ mà AI được phép gọi. Mọi số liệu đều tính từ Repository (Room);
 * AI không có đường nào chạy SQL tùy ý.
 */
class SpendMateTools(private val repository: SmartSpendRepository) {
    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }

    private fun today() = dayFmt.format(Date())
    private fun currentMonth() = SimpleDateFormat("yyyy-MM", Locale.US).format(Calendar.getInstance().time)
    private fun validDate(s: String?) = s?.takeIf { runCatching { dayFmt.parse(it) }.getOrNull() != null }

    private fun List<TransactionEntity>.between(s: String, e: String) =
        filter { it.date.take(10) in s..e }

    private fun findCategory(name: String?, cats: List<CategoryEntity>, type: String? = null): CategoryEntity? {
        if (name.isNullOrBlank()) return null
        val pool = if (type != null) cats.filter { it.type == type }.ifEmpty { cats } else cats
        val n = name.trim().lowercase()
        return pool.firstOrNull { it.name.lowercase() == n }
            ?: pool.firstOrNull { it.name.lowercase().contains(n) || n.contains(it.name.lowercase()) }
    }

    private fun txJson(t: TransactionEntity, cats: List<CategoryEntity>) = JSONObject()
        .put("id", t.id).put("title", t.title).put("amount", t.amount.toLong()).put("type", t.type)
        .put("category", cats.firstOrNull { it.id == t.categoryId }?.name ?: "Khác").put("date", t.date)

    suspend fun execute(name: String, a: JSONObject): ToolResult = try {
        run(name, a)
    } catch (e: Exception) {
        ToolResult(JSONObject().put("error", "Không thực hiện được: ${e.message}"))
    }

    private suspend fun run(name: String, a: JSONObject): ToolResult {
        val txs = repository.allTransactions.first().filter { !it.isAiDraft }
        val cats = repository.allCategories.first()
        val start = validDate(a.optString("start_date")) ?: "0000-01-01"
        val end = validDate(a.optString("end_date")) ?: "9999-12-31"

        return when (name) {
            "get_total_expenses" -> {
                val cat = a.optString("category").takeIf { it.isNotBlank() }
                val catEntity = findCategory(cat, cats, "EXPENSE")
                if (cat != null && catEntity == null) return ToolResult(JSONObject().put("error", "Không có danh mục \"$cat\"."))
                val list = txs.filter { it.type == "EXPENSE" }.between(start, end)
                    .filter { catEntity == null || it.categoryId == catEntity.id }
                ToolResult(JSONObject().put("total", list.sumOf { it.amount }.toLong())
                    .put("count", list.size).put("category", catEntity?.name ?: "tất cả")
                    .put("from", start).put("to", end))
            }
            "get_total_income" -> {
                val list = txs.filter { it.type == "INCOME" }.between(start, end)
                ToolResult(JSONObject().put("total", list.sumOf { it.amount }.toLong())
                    .put("count", list.size).put("from", start).put("to", end))
            }
            "get_expenses_by_category" -> {
                val list = txs.filter { it.type == "EXPENSE" }.between(start, end)
                val total = list.sumOf { it.amount }
                val arr = JSONArray()
                list.groupBy { it.categoryId }.entries
                    .sortedByDescending { e -> e.value.sumOf { it.amount } }
                    .forEach { (id, l) ->
                        val s = l.sumOf { it.amount }
                        arr.put(JSONObject().put("category", cats.firstOrNull { it.id == id }?.name ?: "Khác")
                            .put("total", s.toLong()).put("count", l.size)
                            .put("percent", if (total > 0) (s / total * 100).toInt() else 0))
                    }
                ToolResult(JSONObject().put("total", total.toLong()).put("categories", arr)
                    .put("from", start).put("to", end))
            }
            "get_budget_status" -> {
                val month = currentMonth()
                val budgets = repository.getBudgetsByMonth(month).first()
                if (budgets.isEmpty()) return ToolResult(JSONObject().put("month", month)
                    .put("note", "Người dùng chưa thiết lập ngân sách cho tháng này."))
                val spent = txs.filter { it.type == "EXPENSE" && it.date.startsWith(month) }
                    .groupBy { it.categoryId }.mapValues { e -> e.value.sumOf { it.amount } }
                val only = findCategory(a.optString("category").takeIf { it.isNotBlank() }, cats, "EXPENSE")
                val arr = JSONArray()
                budgets.filter { only == null || it.categoryId == only.id }.forEach { b ->
                    val s = spent[b.categoryId] ?: 0.0
                    arr.put(JSONObject().put("category", cats.firstOrNull { it.id == b.categoryId }?.name ?: "Khác")
                        .put("limit", b.monthlyLimit.toLong()).put("spent", s.toLong())
                        .put("remaining", (b.monthlyLimit - s).toLong())
                        .put("percent_used", if (b.monthlyLimit > 0) (s / b.monthlyLimit * 100).toInt() else 0))
                }
                if (arr.length() == 0) return ToolResult(JSONObject().put("month", month)
                    .put("note", "Chưa có ngân sách cho danh mục này."))
                ToolResult(JSONObject().put("month", month).put("budgets", arr))
            }
            "get_savings_goals" -> {
                val arr = JSONArray()
                repository.allSavingsGoals.first().forEach { g ->
                    arr.put(JSONObject().put("title", g.title).put("target", g.targetAmount.toLong())
                        .put("current", g.currentAmount.toLong()).put("target_date", g.targetDate)
                        .put("percent", if (g.targetAmount > 0) (g.currentAmount / g.targetAmount * 100).toInt() else 0))
                }
                ToolResult(JSONObject().put("goals", arr))
            }
            "find_transactions" -> {
                val kw = a.optString("keyword").lowercase()
                val type = a.optString("type").takeIf { it == "EXPENSE" || it == "INCOME" }
                val limit = a.optInt("limit", 10).coerceIn(1, 20)
                val arr = JSONArray()
                txs.between(start, end)
                    .filter { type == null || it.type == type }
                    .filter { kw.isBlank() || it.title.lowercase().contains(kw) }
                    .sortedByDescending { it.date }.take(limit)
                    .forEach { arr.put(txJson(it, cats)) }
                ToolResult(JSONObject().put("transactions", arr))
            }
            "create_transaction" -> {
                val amount = a.optDouble("amount", 0.0)
                if (amount <= 0) return ToolResult(JSONObject().put("error", "Thiếu hoặc sai số tiền, hãy hỏi lại người dùng."))
                val type = if (a.optString("type") == "INCOME") "INCOME" else "EXPENSE"
                val cat = findCategory(a.optString("category"), cats, type)
                    ?: cats.firstOrNull { it.type == type && it.name.contains("Khác", true) }
                    ?: cats.firstOrNull { it.type == type }
                    ?: return ToolResult(JSONObject().put("error", "Chưa có danh mục nào cho loại giao dịch này."))
                val date = validDate(a.optString("date")) ?: today()
                val title = a.optString("title").ifBlank { cat.name }
                val draft = ChatMessage.DraftCard(
                    transaction = TransactionEntity(
                        title = title, amount = amount, type = type, categoryId = cat.id,
                        date = date, note = "SpendMate AI Chatbot Draft", isAiDraft = true
                    ),
                    categoryName = cat.name
                )
                ToolResult(JSONObject().put("status", "draft_created")
                    .put("note", "Đã hiển thị thẻ xác nhận cho người dùng, chưa lưu.")
                    .put("title", title).put("amount", amount.toLong()).put("type", type)
                    .put("category", cat.name).put("date", date), draft)
            }
            "update_transaction" -> {
                val tx = txs.firstOrNull { it.id == a.optLong("id") }
                    ?: return ToolResult(JSONObject().put("error", "Không tìm thấy giao dịch."))
                if (!a.optBoolean("confirmed", false)) return ToolResult(JSONObject()
                    .put("status", "needs_confirmation").put("transaction", txJson(tx, cats)))
                val cat = findCategory(a.optString("category"), cats, tx.type)
                val updated = tx.copy(
                    title = a.optString("title").ifBlank { tx.title },
                    amount = if (a.has("amount") && a.optDouble("amount") > 0) a.optDouble("amount") else tx.amount,
                    categoryId = cat?.id ?: tx.categoryId,
                    date = validDate(a.optString("date")) ?: tx.date,
                    isAiDraft = false
                )
                repository.updateTransaction(updated)
                ToolResult(JSONObject().put("status", "updated").put("transaction", txJson(updated, cats)))
            }
            "delete_transaction" -> {
                val tx = txs.firstOrNull { it.id == a.optLong("id") }
                    ?: return ToolResult(JSONObject().put("error", "Không tìm thấy giao dịch."))
                if (!a.optBoolean("confirmed", false)) return ToolResult(JSONObject()
                    .put("status", "needs_confirmation").put("transaction", txJson(tx, cats)))
                repository.deleteTransaction(tx)
                ToolResult(JSONObject().put("status", "deleted").put("transaction", txJson(tx, cats)))
            }
            else -> ToolResult(JSONObject().put("error", "Hàm không tồn tại: $name"))
        }
    }
}
