package com.example.smartspend.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartspend.data.local.entity.AiRuleEntity
import com.example.smartspend.data.local.entity.CategoryEntity
import com.example.smartspend.data.local.entity.TransactionEntity
import com.example.smartspend.data.repository.SmartSpendRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AiAutoCategorizationState(
    val pendingDrafts: List<TransactionEntity> = emptyList(),
    val savedRules: List<AiRuleEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val isLoading: Boolean = false
)

class AiAutoCategorizationViewModel(
    private val repository: SmartSpendRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiAutoCategorizationState())
    val uiState: StateFlow<AiAutoCategorizationState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                repository.aiDraftTransactions,
                repository.allAiRules,
                repository.allCategories
            ) { drafts, rules, categories ->
                AiAutoCategorizationState(
                    pendingDrafts = drafts,
                    savedRules = rules,
                    categories = categories
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun confirmDraft(transaction: TransactionEntity, saveRule: Boolean) {
        viewModelScope.launch {
            val updated = transaction.copy(isAiDraft = false)
            repository.updateTransaction(updated)

            if (saveRule) {
                val keyword = extractKeyword(transaction.title)
                if (keyword.isNotBlank()) {
                    val existingRules = _uiState.value.savedRules
                    if (existingRules.none { it.keyword.equals(keyword, true) }) {
                        repository.insertAiRule(
                            AiRuleEntity(keyword = keyword, categoryId = transaction.categoryId)
                        )
                    }
                }
            }
        }
    }

    fun deleteDraft(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    fun deleteRule(rule: AiRuleEntity) {
        viewModelScope.launch {
            repository.deleteAiRule(rule)
        }
    }

    fun addCustomRule(keyword: String, categoryId: Long) {
        if (keyword.isBlank()) return
        viewModelScope.launch {
            repository.insertAiRule(AiRuleEntity(keyword = keyword.trim(), categoryId = categoryId))
        }
    }

    private fun extractKeyword(title: String): String {
        val lower = title.lowercase()
        return when {
            lower.contains("grab") -> "Grab"
            lower.contains("shopee") -> "Shopee"
            lower.contains("cà phê") -> "Cà phê"
            lower.contains("co.opmart") -> "Co.opmart"
            lower.contains("internet") || lower.contains("fpt") -> "Internet"
            lower.contains("điện") -> "Điện"
            else -> title.split(" ").firstOrNull() ?: title
        }
    }
}
