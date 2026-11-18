package com.example.smartspend.ui.budget
import com.example.smartspend.util.CurrentMonth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartspend.data.local.entity.BudgetEntity
import com.example.smartspend.data.local.entity.CategoryEntity
import com.example.smartspend.data.local.entity.TransactionEntity
import com.example.smartspend.data.repository.SmartSpendRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CategoryBudgetUiModel(
    val categoryId: Long,
    val categoryName: String,
    val icon: String,
    val spent: Double,
    val limit: Double
)

data class BudgetUiState(
    val currentMonth: String = CurrentMonth.key(),
    val warningThreshold: Int = 80,
    val totalLimit: Double = 0.0,
    val totalSpent: Double = 0.0,
    val remaining: Double = 0.0,
    val categoryBudgets: List<CategoryBudgetUiModel> = emptyList()
)

class BudgetViewModel(
    private val repository: SmartSpendRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    init {
        loadBudgets()
    }

    private fun loadBudgets() {
        viewModelScope.launch {
            combine(
                repository.allCategories,
                repository.allTransactions,
                repository.getBudgetsByMonth(CurrentMonth.key())
            ) { categories, transactions, budgets ->
                val expenseCategories = categories.filter { it.type == "EXPENSE" }
                val octoberTxs = transactions.filter { it.date.startsWith(CurrentMonth.key()) && it.type == "EXPENSE" }
                val spentMap = octoberTxs.groupBy { it.categoryId }.mapValues { entry -> entry.value.sumOf { it.amount } }
                val budgetMap = budgets.associateBy { it.categoryId }

                val categoryBudgetModels = expenseCategories.map { cat ->
                    val spent = spentMap[cat.id] ?: 0.0
                    val limit = budgetMap[cat.id]?.monthlyLimit ?: when (cat.name) {
                        "Ăn uống" -> 2500000.0
                        "Di chuyển" -> 600000.0
                        "Mua sắm" -> 1000000.0
                        "Hóa đơn & Tiện ích", "Hóa đơn" -> 800000.0
                        "Giải trí" -> 400000.0
                        "Học tập" -> 500000.0
                        else -> 1000000.0
                    }
                    CategoryBudgetUiModel(
                        categoryId = cat.id,
                        categoryName = cat.name,
                        icon = cat.icon,
                        spent = spent,
                        limit = limit
                    )
                }

                val totalL = categoryBudgetModels.sumOf { it.limit }
                val totalS = categoryBudgetModels.sumOf { it.spent }
                val rem = (totalL - totalS).coerceAtLeast(0.0)

                _uiState.value = _uiState.value.copy(
                    totalLimit = totalL,
                    totalSpent = totalS,
                    remaining = rem,
                    categoryBudgets = categoryBudgetModels
                )
            }.collect()
        }
    }

    fun updateWarningThreshold(threshold: Int) {
        _uiState.value = _uiState.value.copy(warningThreshold = threshold)
    }

    fun updateCategoryLimit(categoryId: Long, newLimit: Double) {
        viewModelScope.launch {
            repository.insertBudget(
                BudgetEntity(
                    categoryId = categoryId,
                    monthlyLimit = newLimit,
                    month = CurrentMonth.key()
                )
            )
        }
    }
}
