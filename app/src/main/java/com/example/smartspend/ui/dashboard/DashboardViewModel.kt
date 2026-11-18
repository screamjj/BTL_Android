package com.example.smartspend.ui.dashboard
import com.example.smartspend.util.CurrentMonth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartspend.data.local.entity.BudgetEntity
import com.example.smartspend.data.local.entity.CategoryEntity
import com.example.smartspend.data.local.entity.TransactionEntity
import com.example.smartspend.data.repository.SmartSpendRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val balance: Double = 0.0,
    val monthlyBudgetLimit: Double = 5800000.0,
    val monthlyBudgetSpent: Double = 1528000.0,
    val aiSuggestion: String = "SpendMate gợi ý: Bạn đang chi tiêu hợp lý trong tuần này, thấp hơn 15% so với tháng trước.",
    val isLoading: Boolean = false
)

class DashboardViewModel(
    private val repository: SmartSpendRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            combine(
                repository.allTransactions,
                repository.allCategories,
                repository.getBudgetsByMonth(CurrentMonth.key())
            ) { transactions, categories, budgets ->
                val income = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
                val expense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
                val balance = income - expense

                // Calculate budget spent if needed, or fallback to mock
                val totalLimit = if (budgets.isNotEmpty()) budgets.sumOf { it.monthlyLimit } else 5800000.0
                val totalSpent = if (transactions.isNotEmpty()) {
                    transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
                } else {
                    1528000.0
                }

                DashboardUiState(
                    transactions = transactions,
                    categories = categories,
                    budgets = budgets,
                    totalIncome = if (income > 0) income else 10000000.0,
                    totalExpense = if (expense > 0) expense else 1528000.0,
                    balance = if (balance != 0.0) balance else 8472000.0,
                    monthlyBudgetLimit = if (totalLimit > 0) totalLimit else 5800000.0,
                    monthlyBudgetSpent = totalSpent,
                    isLoading = false
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }
}
