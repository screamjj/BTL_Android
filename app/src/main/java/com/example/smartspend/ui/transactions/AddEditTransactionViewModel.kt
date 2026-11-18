package com.example.smartspend.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartspend.data.local.entity.CategoryEntity
import com.example.smartspend.data.local.entity.TransactionEntity
import com.example.smartspend.data.repository.SmartSpendRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class AddEditTransactionState(
    val amount: String = "",
    val description: String = "",
    val type: String = "EXPENSE", // "EXPENSE" or "INCOME"
    val categoryId: Long = 3L, // default to "Ăn uống" or first category
    val date: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
    val account: String = "Ví chính",
    val aiSuggestion: String? = null,
    val suggestedCategoryId: Long? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val isSaved: Boolean = false,
    val isLoading: Boolean = false
)

class AddEditTransactionViewModel(
    private val repository: SmartSpendRepository,
    private val transactionId: Long? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditTransactionState())
    val uiState: StateFlow<AddEditTransactionState> = _uiState.asStateFlow()

    init {
        loadCategories()
        if (transactionId != null && transactionId > 0) {
            loadTransaction(transactionId)
        }
    }

    fun resetState() {
        _uiState.update {
            AddEditTransactionState(
                date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            )
        }
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            val categories = try { repository.allCategories.first() } catch (_: Exception) { emptyList() }
            _uiState.update { state ->
                val defaultCat = categories.firstOrNull { it.type == state.type }?.id ?: state.categoryId
                state.copy(
                    categories = categories,
                    categoryId = if (state.categoryId == 3L && categories.isNotEmpty()) defaultCat else state.categoryId
                )
            }
        }
    }

    private fun loadTransaction(id: Long) {
        viewModelScope.launch {
            val list = try { repository.allTransactions.first() } catch (_: Exception) { emptyList() }
            val tx = list.firstOrNull { it.id == id }
            if (tx != null) {
                _uiState.update {
                    it.copy(
                        amount = tx.amount.toString(),
                        description = tx.title,
                        type = tx.type,
                        categoryId = tx.categoryId,
                        date = tx.date,
                        account = "Ví chính"
                    )
                }
            }
        }
    }

    fun updateAmount(amount: String) {
        _uiState.update { it.copy(amount = amount) }
    }

    fun updateDescription(description: String) {
        _uiState.update { state ->
            // Check AI suggestion rule based on keywords
            val lower = description.lowercase()
            val matchedCategory = when {
                lower.contains("xăng") || lower.contains("grab") || lower.contains("xe") -> state.categories.firstOrNull { it.name.contains("Di chuyển", true) }
                lower.contains("cà phê") || lower.contains("ăn") || lower.contains("cơm") || lower.contains("phở") -> state.categories.firstOrNull { it.name.contains("Ăn uống", true) }
                lower.contains("shopee") || lower.contains("siêu thị") || lower.contains("mua") -> state.categories.firstOrNull { it.name.contains("Mua sắm", true) }
                lower.contains("lương") -> state.categories.firstOrNull { it.name.contains("Lương", true) }
                lower.contains("điện") || lower.contains("nước") -> state.categories.firstOrNull { it.name.contains("Hóa đơn", true) }
                else -> null
            }

            val suggestion = if (matchedCategory != null) {
                "Gợi ý danh mục: ${matchedCategory.name} - Dùng"
            } else {
                null
            }

            state.copy(
                description = description,
                aiSuggestion = suggestion,
                suggestedCategoryId = matchedCategory?.id
            )
        }
    }

    fun updateType(type: String) {
        _uiState.update { state ->
            val defaultCat = state.categories.firstOrNull { it.type == type }?.id ?: state.categoryId
            state.copy(type = type, categoryId = defaultCat)
        }
    }

    fun updateCategory(categoryId: Long) {
        _uiState.update { it.copy(categoryId = categoryId, aiSuggestion = null) }
    }

    fun applyAiSuggestion() {
        val suggestedId = _uiState.value.suggestedCategoryId
        if (suggestedId != null) {
            _uiState.update { it.copy(categoryId = suggestedId, aiSuggestion = null) }
        }
    }

    fun updateDate(date: String) {
        _uiState.update { it.copy(date = date) }
    }

    fun updateAccount(account: String) {
        _uiState.update { it.copy(account = account) }
    }

    fun saveTransaction() {
        viewModelScope.launch {
            val state = _uiState.value
            val amountValue = state.amount.toDoubleOrNull() ?: 0.0
            if (amountValue <= 0.0 || state.description.isBlank()) return@launch

            _uiState.update { it.copy(isLoading = true) }

            val transaction = TransactionEntity(
                id = transactionId ?: 0L,
                title = state.description,
                amount = amountValue,
                type = state.type,
                categoryId = state.categoryId,
                date = state.date,
                note = "Nguồn: ${state.account}",
                isAiDraft = false
            )

            if (transactionId != null && transactionId > 0) {
                repository.updateTransaction(transaction)
            } else {
                repository.insertTransaction(transaction)
            }

            _uiState.update { it.copy(isLoading = false, isSaved = true) }
        }
    }
}
