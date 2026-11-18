package com.example.smartspend.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartspend.data.local.entity.CategoryEntity
import com.example.smartspend.data.local.entity.TransactionEntity
import com.example.smartspend.data.repository.SmartSpendRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONArray
import org.json.JSONObject

sealed interface ChatMessage {
    data class Text(val text: String, val isAi: Boolean, val timestamp: String = getCurrentTime()) : ChatMessage
    data class DraftCard(
        val transaction: TransactionEntity,
        val categoryName: String,
        val status: DraftStatus = DraftStatus.PENDING,
        val timestamp: String = getCurrentTime()
    ) : ChatMessage
}

enum class DraftStatus {
    PENDING, CONFIRMED, CANCELLED
}

data class SpendMateState(
    val messages: List<ChatMessage> = emptyList(),
    val isTyping: Boolean = false
)

private fun getCurrentTime(): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
}

class SpendMateViewModel(
    private val repository: SmartSpendRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpendMateState())
    val uiState: StateFlow<SpendMateState> = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(
                messages = listOf(
                    ChatMessage.Text(
                        text = "Xin chào! Tôi là SpendMate, trợ lý tài chính AI của bạn. Bạn muốn tôi giúp tra cứu chi tiêu, xem ngân sách hay thêm giao dịch bằng ngôn ngữ tự nhiên không?",
                        isAi = true
                    )
                )
            )
        }
    }

    private val aiClient = SpendMateAiClient()
    private val tools = SpendMateTools(repository)
    private val history = JSONArray()   // ngữ cảnh hội thoại gửi cho Gemini

    private fun userContent(text: String) = JSONObject().put("role", "user")
        .put("parts", JSONArray().put(JSONObject().put("text", text)))

    fun sendMessage(text: String) {
        if (text.isBlank() || _uiState.value.isTyping) return

        val userMsg = ChatMessage.Text(text = text, isAi = false)
        _uiState.update { it.copy(messages = it.messages + userMsg, isTyping = true) }

        viewModelScope.launch {
            val replies = try {
                askAi(text.trim())
            } catch (e: Exception) {
                // AI/backend lỗi: quay về xử lý theo quy tắc để chatbot vẫn dùng được
                val fallback = try { processAiResponse(text.trim()) }
                catch (_: Exception) { ChatMessage.Text("Xin lỗi, mình gặp lỗi khi xử lý yêu cầu này. Bạn thử lại giúp mình nhé.", isAi = true) }
                val note = ChatMessage.Text("(Trợ lý AI đang không khả dụng, mình trả lời ở chế độ cơ bản.)", isAi = true)
                listOf(note, fallback)
            }
            _uiState.update { it.copy(messages = it.messages + replies, isTyping = false) }
        }
    }

    /** Vòng lặp tool calling: Gemini yêu cầu hàm -> app chạy trên Room -> gửi kết quả lại. */
    private suspend fun askAi(query: String): List<ChatMessage> {
        val snapshot = history.length()
        val todayStr = dayFmt.format(Date())
        history.put(userContent(query))
        try {
            var draft: ChatMessage.DraftCard? = null
            var finalText = ""
            for (round in 0 until SpendMateConfig.MAX_TOOL_ROUNDS) {
                val turn = aiClient.chat(history, todayStr)
                history.put(turn.content)
                if (turn.calls.isEmpty()) { finalText = turn.text; break }

                val responses = JSONArray()
                for ((name, args) in turn.calls) {
                    val r = tools.execute(name, args)
                    if (r.draft != null) draft = r.draft
                    responses.put(JSONObject().put("functionResponse",
                        JSONObject().put("name", name).put("response", JSONObject().put("result", r.json))))
                }
                history.put(JSONObject().put("role", "user").put("parts", responses))
            }
            if (finalText.isBlank()) {
                finalText = if (draft != null) "Mình đã soạn bản nháp giao dịch, bạn kiểm tra và xác nhận nhé."
                else "Mình chưa trả lời được câu này, bạn thử diễn đạt lại giúp mình nhé."
            }
            trimHistory()
            return listOfNotNull(ChatMessage.Text(finalText.trim(), isAi = true), draft)
        } catch (e: Exception) {
            // bỏ phần dở dang để ngữ cảnh không bị lệch cặp functionCall/functionResponse
            while (history.length() > snapshot) history.remove(history.length() - 1)
            throw e
        }
    }

    private fun trimHistory() {
        // giới hạn độ dài ngữ cảnh để kiểm soát chi phí; cắt tại tin nhắn text của user
        while (history.length() > SpendMateConfig.MAX_HISTORY) {
            history.remove(0)
            while (history.length() > 0) {
                val first = history.getJSONObject(0)
                val p0 = first.getJSONArray("parts").getJSONObject(0)
                if (first.getString("role") == "user" && p0.has("text")) break
                history.remove(0)
            }
        }
    }

    // ---------- Thời gian ----------

    private data class DateRange(val label: String, val start: String, val end: String)

    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val monthFmt = SimpleDateFormat("yyyy-MM", Locale.US)

    private fun fmt(cal: Calendar) = dayFmt.format(cal.time)

    /** Xác định khoảng thời gian từ câu hỏi; mặc định là tháng hiện tại. */
    private fun parseRange(lower: String): DateRange {
        val now = Calendar.getInstance()
        fun monthRange(c: Calendar, label: String): DateRange {
            val s = c.clone() as Calendar
            s.set(Calendar.DAY_OF_MONTH, 1)
            val e = c.clone() as Calendar
            e.set(Calendar.DAY_OF_MONTH, e.getActualMaximum(Calendar.DAY_OF_MONTH))
            return DateRange(label, fmt(s), fmt(e))
        }
        return when {
            "hôm qua" in lower || "hôm trước" in lower -> {
                val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                DateRange("hôm qua", fmt(c), fmt(c))
            }
            "hôm nay" in lower || "ngày hôm nay" in lower -> DateRange("hôm nay", fmt(now), fmt(now))
            "tuần trước" in lower -> {
                val s = Calendar.getInstance().apply {
                    firstDayOfWeek = Calendar.MONDAY
                    add(Calendar.WEEK_OF_YEAR, -1)
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                }
                val e = (s.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 6) }
                DateRange("tuần trước", fmt(s), fmt(e))
            }
            "tuần này" in lower || "tuần nay" in lower -> {
                val s = Calendar.getInstance().apply {
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                }
                val e = (s.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 6) }
                DateRange("tuần này", fmt(s), fmt(e))
            }
            "tháng trước" in lower || "tháng rồi" in lower -> {
                val c = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                monthRange(c, "tháng ${c.get(Calendar.MONTH) + 1}/${c.get(Calendar.YEAR)}")
            }
            "năm nay" in lower || "năm này" in lower -> {
                val y = now.get(Calendar.YEAR)
                DateRange("năm $y", "$y-01-01", "$y-12-31")
            }
            else -> monthRange(now, "tháng ${now.get(Calendar.MONTH) + 1}/${now.get(Calendar.YEAR)}")
        }
    }

    private fun List<TransactionEntity>.inRange(r: DateRange) =
        filter { it.date.take(10) >= r.start && it.date.take(10) <= r.end }

    // ---------- Xử lý câu hỏi ----------

    private suspend fun processAiResponse(query: String): ChatMessage {
        val lower = query.lowercase()
        val money = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN"))
        fun vnd(v: Double) = "${money.format(v.toLong())} đ"

        val transactions = try { repository.allTransactions.first().filter { !it.isAiDraft } } catch (_: Exception) { emptyList() }
        val categories = try { repository.allCategories.first() } catch (_: Exception) { emptyList() }
        val range = parseRange(lower)
        val amount = extractAmount(lower)

        val isQuestion = listOf("bao nhiêu", "tổng", "?", "thống kê", "còn", "nhiều nhất", "so với", "so sánh")
            .any { it in lower }
        val mentionedCategory = categories.firstOrNull { it.name.lowercase().let { n -> n in lower || n.split(" ", "&").filter { w -> w.length > 2 }.any { w -> w in lower } } }

        return when {
            // Ngân sách
            "ngân sách" in lower || "hạn mức" in lower -> budgetReply(lower, range, transactions, categories, mentionedCategory, ::vnd)

            // So sánh với kỳ trước
            "so với" in lower || "so sánh" in lower -> compareReply(transactions, ::vnd)

            // Danh mục chi nhiều nhất / thống kê theo danh mục
            "nhiều nhất" in lower || "theo danh mục" in lower || "thống kê" in lower -> {
                val exp = transactions.filter { it.type == "EXPENSE" }.inRange(range)
                if (exp.isEmpty()) return text("Chưa có khoản chi nào trong ${range.label}.")
                val byCat = exp.groupBy { it.categoryId }
                    .mapValues { e -> e.value.sumOf { it.amount } }
                    .entries.sortedByDescending { it.value }
                val total = exp.sumOf { it.amount }
                val lines = byCat.take(5).joinToString("\n") { (id, sum) ->
                    val name = categories.firstOrNull { it.id == id }?.name ?: "Khác"
                    "• $name: ${vnd(sum)} (${(sum / total * 100).toInt()}%)"
                }
                text("Chi tiêu ${range.label} theo danh mục (tổng ${vnd(total)}):\n$lines")
            }

            // Thu nhập (câu hỏi)
            ("thu nhập" in lower || "kiếm được" in lower || "thu về" in lower) && (isQuestion || amount == null) -> {
                val total = transactions.filter { it.type == "INCOME" }.inRange(range).sumOf { it.amount }
                text("Tổng thu nhập ${range.label} của bạn là ${vnd(total)}.")
            }

            // Tổng chi / chi theo danh mục (câu hỏi)
            isQuestion && ("chi" in lower || "tiêu" in lower || "tốn" in lower || "mất" in lower) && amount == null -> {
                val exp = transactions.filter { it.type == "EXPENSE" }.inRange(range)
                    .let { l -> if (mentionedCategory != null) l.filter { it.categoryId == mentionedCategory.id } else l }
                val total = exp.sumOf { it.amount }
                val scope = mentionedCategory?.let { " cho \"${it.name}\"" } ?: ""
                if (exp.isEmpty()) text("Bạn chưa có khoản chi nào$scope trong ${range.label}.")
                else text("Tổng chi$scope ${range.label} là ${vnd(total)} (${exp.size} giao dịch).")
            }

            // Thêm giao dịch
            isTransactionCommand(lower) -> {
                if (amount == null) {
                    return text("Mình chưa thấy số tiền trong câu của bạn. Bạn cho mình biết số tiền nhé (VD: \"Ăn trưa 50k\").")
                }
                val isIncome = listOf("lương", "thưởng", "nhận được", "thu nhập", "được cho", "lãi").any { it in lower } &&
                    listOf("tiêu", "mua", "trả", "chi").none { it in lower }
                val type = if (isIncome) "INCOME" else "EXPENSE"
                val category = matchCategory(lower, categories, type)
                    ?: return text("Mình chưa có danh mục phù hợp để lưu giao dịch này. Bạn hãy tạo danh mục trước nhé.")

                val date = when {
                    "hôm qua" in lower -> range.start
                    else -> fmt(Calendar.getInstance())
                }
                ChatMessage.DraftCard(
                    transaction = TransactionEntity(
                        title = cleanTitle(query),
                        amount = amount,
                        type = type,
                        categoryId = category.id,
                        date = date,
                        note = "SpendMate AI Chatbot Draft",
                        isAiDraft = true
                    ),
                    categoryName = category.name
                )
            }

            else -> text(
                "Mình chưa hiểu rõ ý bạn. Bạn có thể hỏi về chi tiêu, thu nhập, ngân sách theo hôm nay/tuần này/tháng này, " +
                    "hoặc nhờ mình ghi giao dịch (VD: \"Hôm nay ăn trưa 50k\")."
            )
        }
    }

    private fun text(s: String) = ChatMessage.Text(text = s, isAi = true)

    private fun isTransactionCommand(lower: String): Boolean {
        val verbs = listOf("thêm", "ghi", "mua", "đổ", "trả", "ăn", "uống", "grab", "cà phê", "đóng", "nạp", "lương", "thưởng", "nhận", "tiêu", "xem phim")
        return verbs.any { it in lower }
    }

    private fun cleanTitle(query: String): String {
        val t = query.replace(Regex("^(thêm|ghi|ghi lại)\\s+", RegexOption.IGNORE_CASE), "").trim()
        return t.replaceFirstChar { it.uppercase() }
    }

    private suspend fun budgetReply(
        lower: String,
        range: DateRange,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        mentioned: CategoryEntity?,
        vnd: (Double) -> String
    ): ChatMessage {
        val month = monthFmt.format(Calendar.getInstance().time)
        val budgets = try { repository.getBudgetsByMonth(month).first() } catch (_: Exception) { emptyList() }
        if (budgets.isEmpty()) return text("Bạn chưa thiết lập ngân sách cho tháng $month. Hãy vào mục Ngân sách để đặt hạn mức nhé.")

        val spentByCat = transactions.filter { it.type == "EXPENSE" && it.date.startsWith(month) }
            .groupBy { it.categoryId }.mapValues { e -> e.value.sumOf { it.amount } }

        fun line(catId: Long, limit: Double): String {
            val spent = spentByCat[catId] ?: 0.0
            val name = categories.firstOrNull { it.id == catId }?.name ?: "Khác"
            val pct = if (limit > 0) (spent / limit * 100).toInt() else 0
            val remain = limit - spent
            val status = when {
                remain < 0 -> "⚠️ đã vượt ${vnd(-remain)}"
                pct >= 80 -> "⚠️ sắp hết, còn ${vnd(remain)}"
                else -> "còn ${vnd(remain)}"
            }
            return "• $name: ${vnd(spent)}/${vnd(limit)} ($pct%) – $status"
        }

        if (mentioned != null) {
            val b = budgets.firstOrNull { it.categoryId == mentioned.id }
                ?: return text("Bạn chưa đặt ngân sách cho \"${mentioned.name}\" trong tháng $month.")
            return text("Ngân sách tháng $month:\n${line(b.categoryId, b.monthlyLimit)}")
        }
        val totalLimit = budgets.sumOf { it.monthlyLimit }
        val totalSpent = budgets.sumOf { spentByCat[it.categoryId] ?: 0.0 }
        val pct = if (totalLimit > 0) (totalSpent / totalLimit * 100).toInt() else 0
        val detail = budgets.sortedByDescending { (spentByCat[it.categoryId] ?: 0.0) / it.monthlyLimit.coerceAtLeast(1.0) }
            .joinToString("\n") { line(it.categoryId, it.monthlyLimit) }
        return text("Ngân sách tháng $month: đã dùng ${vnd(totalSpent)}/${vnd(totalLimit)} ($pct%).\n$detail")
    }

    private fun compareReply(transactions: List<TransactionEntity>, vnd: (Double) -> String): ChatMessage {
        val cal = Calendar.getInstance()
        val cur = monthFmt.format(cal.time)
        cal.add(Calendar.MONTH, -1)
        val prev = monthFmt.format(cal.time)
        val curSum = transactions.filter { it.type == "EXPENSE" && it.date.startsWith(cur) }.sumOf { it.amount }
        val prevSum = transactions.filter { it.type == "EXPENSE" && it.date.startsWith(prev) }.sumOf { it.amount }
        if (prevSum == 0.0) return text("Tháng $prev chưa có dữ liệu chi tiêu để so sánh. Tháng $cur bạn đã chi ${vnd(curSum)}.")
        val diff = curSum - prevSum
        val pct = (kotlin.math.abs(diff) / prevSum * 100).toInt()
        val trend = if (diff >= 0) "tăng" else "giảm"
        return text("Chi tiêu tháng $cur là ${vnd(curSum)}, $trend ${vnd(kotlin.math.abs(diff))} ($pct%) so với tháng $prev (${vnd(prevSum)}).")
    }

    // ---------- Trích xuất số tiền / danh mục ----------

    /** Hỗ trợ: 50k, 50.000, 50,000đ, 1,5 triệu, 2tr, 1tr2 (không hỗ trợ). Trả null nếu không có số tiền. */
    private fun extractAmount(text: String): Double? {
        val regex = Regex("(\\d+(?:[.,]\\d+)*)\\s*(k|nghìn|ngàn|tr|triệu|củ|đ|đồng|vnd)?(?![\\p{L}\\d])", RegexOption.IGNORE_CASE)
        for (m in regex.findAll(text)) {
            val raw = m.groupValues[1]
            val unit = m.groupValues[2].lowercase()
            val isThousandSep = Regex("^\\d{1,3}([.,]\\d{3})+$").matches(raw)
            val num = if (isThousandSep) raw.replace(".", "").replace(",", "").toDoubleOrNull()
            else raw.replace(",", ".").toDoubleOrNull()
            if (num == null || num <= 0) continue
            return when (unit) {
                "triệu", "tr", "củ" -> num * 1_000_000.0
                "k", "nghìn", "ngàn" -> num * 1_000.0
                else -> if (num < 1000 && !isThousandSep) num * 1_000.0 else num
            }
        }
        return null
    }

    private fun matchCategory(text: String, categories: List<CategoryEntity>, type: String): CategoryEntity? {
        val pool = categories.filter { it.type == type }
        if (pool.isEmpty()) return null
        fun find(vararg keys: String) = pool.firstOrNull { c -> keys.any { c.name.contains(it, true) } }

        // Ưu tiên tên danh mục người dùng nói thẳng
        pool.firstOrNull { it.name.lowercase() in text }?.let { return it }

        val matched = if (type == "INCOME") when {
            "lương" in text -> find("lương")
            "thưởng" in text -> find("thưởng")
            else -> null
        } else when {
            listOf("grab", "xe", "xăng", "taxi", "bus", "gửi xe").any { it in text } -> find("Di chuyển")
            listOf("cà phê", "ăn", "cơm", "phở", "uống", "trà sữa", "bún").any { it in text } -> find("Ăn uống")
            listOf("shopee", "mua", "siêu thị", "quần áo").any { it in text } -> find("Mua sắm")
            listOf("điện", "nước", "internet", "wifi", "hóa đơn").any { it in text } -> find("Hóa đơn")
            listOf("phim", "game", "du lịch", "karaoke").any { it in text } -> find("Giải trí")
            listOf("sách", "học", "khóa học").any { it in text } -> find("Học tập")
            else -> null
        }
        return matched ?: pool.firstOrNull { it.name.contains("Khác", true) } ?: pool.first()
    }

    fun confirmDraftCard(draftCard: ChatMessage.DraftCard, index: Int) {
        viewModelScope.launch {
            val confirmedTx = draftCard.transaction.copy(isAiDraft = false)
            repository.insertTransaction(confirmedTx)

            _uiState.update { state ->
                val newMessages = state.messages.toMutableList()
                if (index in newMessages.indices) {
                    newMessages[index] = draftCard.copy(status = DraftStatus.CONFIRMED)
                    newMessages.add(ChatMessage.Text(text = "Đã xác nhận và lưu giao dịch vào hệ thống thành công!", isAi = true))
                }
                state.copy(messages = newMessages)
            }
        }
    }

    fun cancelDraftCard(draftCard: ChatMessage.DraftCard, index: Int) {
        _uiState.update { state ->
            val newMessages = state.messages.toMutableList()
            if (index in newMessages.indices) {
                newMessages[index] = draftCard.copy(status = DraftStatus.CANCELLED)
                newMessages.add(ChatMessage.Text(text = "Đã hủy bản nháp giao dịch.", isAi = true))
            }
            state.copy(messages = newMessages)
        }
    }
}
