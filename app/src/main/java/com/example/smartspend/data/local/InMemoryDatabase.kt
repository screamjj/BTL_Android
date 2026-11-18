package com.example.smartspend.data.local

import com.example.smartspend.data.local.dao.*
import com.example.smartspend.data.local.entity.*
import com.example.smartspend.data.repository.SmartSpendRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * DATABASE GIẢ (lưu trong bộ nhớ, mất khi tắt app) để chạy thử giao diện và chatbot.
 * Sau này thay bằng Room: giữ nguyên các interface DAO, chỉ đổi hàm createRepository().
 */
object InMemoryDatabase {
    private val ids = AtomicLong(1000)
    private fun nextId() = ids.incrementAndGet()

    private val users = MutableStateFlow<List<UserEntity>>(emptyList())
    private val categories = MutableStateFlow<List<CategoryEntity>>(emptyList())
    private val transactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    private val budgets = MutableStateFlow<List<BudgetEntity>>(emptyList())
    private val goals = MutableStateFlow<List<SavingsGoalEntity>>(emptyList())
    private val rules = MutableStateFlow<List<AiRuleEntity>>(emptyList())

    private val userDao = object : UserDao {
        override suspend fun getUserByEmail(email: String) = users.value.firstOrNull { it.email.equals(email, true) }
        override fun getUserById(id: Long): Flow<UserEntity?> = users.map { l -> l.firstOrNull { it.id == id } }
        override suspend fun insertUser(user: UserEntity): Long {
            val u = if (user.id == 0L) user.copy(id = nextId()) else user
            users.update { it + u }; return u.id
        }
    }

    private val categoryDao = object : CategoryDao {
        override fun getAllCategories(): Flow<List<CategoryEntity>> = categories
        override fun getCategoriesByType(type: String) = categories.map { l -> l.filter { it.type == type } }
        override suspend fun getCategoryById(id: Long) = categories.value.firstOrNull { it.id == id }
        override suspend fun insertCategory(category: CategoryEntity): Long {
            val c = if (category.id == 0L) category.copy(id = nextId()) else category
            categories.update { it.filter { x -> x.id != c.id } + c }; return c.id
        }
        override suspend fun insertCategories(categories: List<CategoryEntity>) { categories.forEach { insertCategory(it) } }
    }

    private val transactionDao = object : TransactionDao {
        override fun getAllTransactions(): Flow<List<TransactionEntity>> =
            transactions.map { l -> l.sortedWith(compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.id }) }
        override fun getTransactionsByMonth(yearMonth: String) =
            getAllTransactions().map { l -> l.filter { it.date.startsWith(yearMonth) } }
        override fun getAiDraftTransactions() = getAllTransactions().map { l -> l.filter { it.isAiDraft } }
        override suspend fun insertTransaction(transaction: TransactionEntity): Long {
            val t = if (transaction.id == 0L) transaction.copy(id = nextId()) else transaction
            transactions.update { it.filter { x -> x.id != t.id } + t }; return t.id
        }
        override suspend fun insertTransactions(transactions: List<TransactionEntity>) { transactions.forEach { insertTransaction(it) } }
        override suspend fun updateTransaction(transaction: TransactionEntity) {
            transactions.update { l -> l.map { if (it.id == transaction.id) transaction else it } }
        }
        override suspend fun deleteTransaction(transaction: TransactionEntity) { deleteTransactionById(transaction.id) }
        override suspend fun deleteTransactionById(id: Long) { transactions.update { l -> l.filter { it.id != id } } }
    }

    private val budgetDao = object : BudgetDao {
        override fun getBudgetsByMonth(month: String) = budgets.map { l -> l.filter { it.month == month } }
        override suspend fun insertBudget(budget: BudgetEntity): Long {
            // thay thế ngân sách cùng danh mục + tháng (giống OnConflictStrategy.REPLACE)
            val b = budget.copy(id = if (budget.id == 0L) nextId() else budget.id)
            budgets.update { l -> l.filterNot { it.categoryId == b.categoryId && it.month == b.month } + b }; return b.id
        }
        override suspend fun insertBudgets(budgets: List<BudgetEntity>) { budgets.forEach { insertBudget(it) } }
        override suspend fun updateBudget(budget: BudgetEntity) { budgets.update { l -> l.map { if (it.id == budget.id) budget else it } } }
    }

    private val savingsGoalDao = object : SavingsGoalDao {
        override fun getAllSavingsGoals(): Flow<List<SavingsGoalEntity>> = goals
        override suspend fun insertSavingsGoal(goal: SavingsGoalEntity): Long {
            val g = if (goal.id == 0L) goal.copy(id = nextId()) else goal
            goals.update { it + g }; return g.id
        }
        override suspend fun insertSavingsGoals(goals: List<SavingsGoalEntity>) { goals.forEach { insertSavingsGoal(it) } }
        override suspend fun updateSavingsGoal(goal: SavingsGoalEntity) { goals.update { l -> l.map { if (it.id == goal.id) goal else it } } }
        override suspend fun deleteSavingsGoal(goal: SavingsGoalEntity) { goals.update { l -> l.filter { it.id != goal.id } } }
    }

    private val aiRuleDao = object : AiRuleDao {
        override fun getAllAiRules(): Flow<List<AiRuleEntity>> = rules
        override suspend fun insertAiRule(rule: AiRuleEntity): Long {
            val r = if (rule.id == 0L) rule.copy(id = nextId()) else rule
            rules.update { it + r }; return r.id
        }
        override suspend fun insertAiRules(rules: List<AiRuleEntity>) { rules.forEach { insertAiRule(it) } }
        override suspend fun deleteAiRule(rule: AiRuleEntity) { rules.update { l -> l.filter { it.id != rule.id } } }
        override suspend fun deleteAiRuleById(ruleId: Long) { rules.update { l -> l.filter { it.id != ruleId } } }
    }

    val repository: SmartSpendRepository by lazy {
        seed()
        SmartSpendRepository(userDao, categoryDao, transactionDao, budgetDao, savingsGoalDao, aiRuleDao)
    }

    // ---------- Dữ liệu mẫu (ngày tính theo hôm nay) ----------
    private fun seed() {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fun daysAgo(n: Int) = fmt.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -n) }.time)
        fun firstOfMonth(monthsAgo: Int) = fmt.format(Calendar.getInstance().apply {
            add(Calendar.MONTH, -monthsAgo); set(Calendar.DAY_OF_MONTH, 1)
        }.time)
        val thisMonth = SimpleDateFormat("yyyy-MM", Locale.US).format(Calendar.getInstance().time)

        categories.value = listOf(
            CategoryEntity(1, "Ăn uống", "restaurant", "EXPENSE", "#FF5722"),
            CategoryEntity(2, "Di chuyển", "directions_car", "EXPENSE", "#2196F3"),
            CategoryEntity(3, "Mua sắm", "shopping_bag", "EXPENSE", "#9C27B0"),
            CategoryEntity(4, "Hóa đơn & Tiện ích", "receipt", "EXPENSE", "#FFC107"),
            CategoryEntity(5, "Giải trí", "movie", "EXPENSE", "#E91E63"),
            CategoryEntity(6, "Học tập", "school", "EXPENSE", "#3F51B5"),
            CategoryEntity(7, "Khác", "more_horiz", "EXPENSE", "#607D8B"),
            CategoryEntity(8, "Lương", "payments", "INCOME", "#4CAF50"),
            CategoryEntity(9, "Thưởng", "redeem", "INCOME", "#8BC34A"),
            CategoryEntity(10, "Thu nhập khác", "attach_money", "INCOME", "#009688")
        )

        var tid = 1L
        fun exp(ago: Int, title: String, amount: Double, cat: Long, draft: Boolean = false) =
            TransactionEntity(tid++, title, amount, "EXPENSE", cat, daysAgo(ago), if (draft) "SpendMate AI Chatbot Draft" else "", draft)
        transactions.value = listOf(
            exp(0, "Ăn trưa cơm tấm", 45000.0, 1), exp(0, "Cà phê", 30000.0, 1),
            exp(1, "Grab đi làm", 65000.0, 2), exp(1, "Ăn tối với bạn", 180000.0, 1),
            exp(2, "Đổ xăng", 70000.0, 2), exp(3, "Mua sách lập trình", 220000.0, 6),
            exp(4, "Xem phim", 120000.0, 5), exp(5, "Bún chả", 55000.0, 1),
            exp(6, "Tiền điện", 450000.0, 4), exp(7, "Mua áo", 350000.0, 3),
            exp(8, "Trà sữa", 50000.0, 1), exp(9, "Internet", 220000.0, 4),
            exp(10, "Grab", 80000.0, 2), exp(12, "Siêu thị", 420000.0, 3),
            exp(14, "Ăn trưa", 50000.0, 1), exp(16, "Cà phê", 35000.0, 1),
            exp(18, "Game", 100000.0, 5), exp(20, "Khóa học online", 500000.0, 6),
            exp(22, "Ăn tối", 150000.0, 1), exp(25, "Đổ xăng", 70000.0, 2),
            exp(28, "Tiền nước", 120000.0, 4), exp(31, "Ăn uống cuối tuần", 400000.0, 1),
            exp(34, "Mua giày", 800000.0, 3), exp(37, "Grab", 90000.0, 2),
            exp(40, "Tiền điện", 430000.0, 4), exp(43, "Xem phim", 130000.0, 5),
            exp(2, "Grab về nhà", 60000.0, 2, draft = true), exp(1, "Cà phê Highlands", 55000.0, 1, draft = true)
        ) + listOf(
            TransactionEntity(tid++, "Lương tháng", 15000000.0, "INCOME", 8, firstOfMonth(0), "", false),
            TransactionEntity(tid++, "Lương tháng trước", 15000000.0, "INCOME", 8, firstOfMonth(1), "", false),
            TransactionEntity(tid++, "Thưởng dự án", 2000000.0, "INCOME", 9, daysAgo(35), "", false)
        )

        budgets.value = listOf(
            BudgetEntity(1, 1, 3000000.0, thisMonth), BudgetEntity(2, 2, 600000.0, thisMonth),
            BudgetEntity(3, 3, 1000000.0, thisMonth), BudgetEntity(4, 4, 900000.0, thisMonth),
            BudgetEntity(5, 5, 400000.0, thisMonth), BudgetEntity(6, 6, 500000.0, thisMonth)
        )
        goals.value = listOf(
            SavingsGoalEntity(1, "Mua laptop", 20000000.0, 6500000.0, "2027-06-30", "laptop"),
            SavingsGoalEntity(2, "Du lịch Đà Nẵng", 8000000.0, 3000000.0, "2027-03-15", "flight"),
            SavingsGoalEntity(3, "Quỹ khẩn cấp", 30000000.0, 5000000.0, "2027-12-31", "savings")
        )
        rules.value = listOf(
            AiRuleEntity(1, "grab", 2), AiRuleEntity(2, "cà phê", 1), AiRuleEntity(3, "cơm", 1)
        )
        // demo@smartspend.vn / password123 đã được LoginViewModel hỗ trợ sẵn
    }
}
