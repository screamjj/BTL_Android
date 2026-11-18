package com.example.smartspend.ui.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartspend.data.local.entity.SavingsGoalEntity
import com.example.smartspend.data.repository.SmartSpendRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SavingsGoalsUiState(
    val goals: List<SavingsGoalEntity> = emptyList()
)

class SavingsGoalsViewModel(
    private val repository: SmartSpendRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavingsGoalsUiState())
    val uiState: StateFlow<SavingsGoalsUiState> = _uiState.asStateFlow()

    init {
        loadGoals()
    }

    private fun loadGoals() {
        viewModelScope.launch {
            repository.allSavingsGoals.collect { goals ->
                _uiState.value = _uiState.value.copy(goals = goals)
            }
        }
    }

    fun addDeposit(goal: SavingsGoalEntity, amount: Double) {
        viewModelScope.launch {
            val updated = goal.copy(currentAmount = (goal.currentAmount + amount).coerceAtMost(goal.targetAmount))
            repository.updateSavingsGoal(updated)
        }
    }

    fun addSavingsGoal(title: String, targetAmount: Double, targetDate: String) {
        viewModelScope.launch {
            repository.insertSavingsGoal(
                SavingsGoalEntity(
                    title = title,
                    targetAmount = targetAmount,
                    currentAmount = 0.0,
                    targetDate = targetDate,
                    icon = "laptop"
                )
            )
        }
    }

    fun deleteGoal(goal: SavingsGoalEntity) {
        viewModelScope.launch {
            repository.deleteSavingsGoal(goal)
        }
    }
}
