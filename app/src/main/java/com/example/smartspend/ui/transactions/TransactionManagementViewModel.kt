package com.example.smartspend.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartspend.data.local.entity.CategoryEntity
import com.example.smartspend.data.local.entity.TransactionEntity
import com.example.smartspend.data.repository.SmartSpendRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TransactionUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val filteredTransactions: List<TransactionEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedType: String? = null, // null for All, "INCOME", "EXPENSE"
    val selectedCategoryId: Long? = null,
    val isLoading: Boolean = false
)

class TransactionManagementViewModel(
    private val repository: SmartSpendRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionUiState())
    val uiState: StateFlow<TransactionUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            combine(
                repository.allTransactions,
                repository.allCategories
            ) { transactions, categories ->
                val currentState = _uiState.value
                val filtered = filterTransactions(
                    transactions,
                    currentState.searchQuery,
                    currentState.selectedType,
                    currentState.selectedCategoryId
                )
                currentState.copy(
                    transactions = transactions,
                    filteredTransactions = filtered,
                    categories = categories,
                    isLoading = false
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { state ->
            val filtered = filterTransactions(state.transactions, query, state.selectedType, state.selectedCategoryId)
            state.copy(searchQuery = query, filteredTransactions = filtered)
        }
    }

    fun updateSelectedType(type: String?) {
        _uiState.update { state ->
            val filtered = filterTransactions(state.transactions, state.searchQuery, type, state.selectedCategoryId)
            state.copy(selectedType = type, filteredTransactions = filtered)
        }
    }

    fun updateSelectedCategory(categoryId: Long?) {
        _uiState.update { state ->
            val filtered = filterTransactions(state.transactions, state.searchQuery, state.selectedType, categoryId)
            state.copy(selectedCategoryId = categoryId, filteredTransactions = filtered)
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    private fun filterTransactions(
        transactions: List<TransactionEntity>,
        query: String,
        type: String?,
        categoryId: Long?
    ): List<TransactionEntity> {
        return transactions.filter { tx ->
            val matchesQuery = query.isBlank() ||
                    tx.title.contains(query, ignoreCase = true) ||
                    tx.note?.contains(query, ignoreCase = true) == true ||
                    tx.amount.toString().contains(query)
            val matchesType = type == null || tx.type == type
            val matchesCategory = categoryId == null || tx.categoryId == categoryId
            matchesQuery && matchesType && matchesCategory
        }
    }
}
