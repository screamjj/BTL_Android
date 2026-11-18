package com.example.smartspend.data.local.dao

import com.example.smartspend.data.local.entity.*
import kotlinx.coroutines.flow.Flow

interface UserDao {
    suspend fun getUserByEmail(email: String): UserEntity?
    fun getUserById(id: Long): Flow<UserEntity?>
    suspend fun insertUser(user: UserEntity): Long
}

interface CategoryDao {
    fun getAllCategories(): Flow<List<CategoryEntity>>
    fun getCategoriesByType(type: String): Flow<List<CategoryEntity>>
    suspend fun getCategoryById(id: Long): CategoryEntity?
    suspend fun insertCategory(category: CategoryEntity): Long
    suspend fun insertCategories(categories: List<CategoryEntity>)
}

interface TransactionDao {
    fun getAllTransactions(): Flow<List<TransactionEntity>>
    fun getTransactionsByMonth(yearMonth: String): Flow<List<TransactionEntity>>
    fun getAiDraftTransactions(): Flow<List<TransactionEntity>>
    suspend fun insertTransaction(transaction: TransactionEntity): Long
    suspend fun insertTransactions(transactions: List<TransactionEntity>)
    suspend fun updateTransaction(transaction: TransactionEntity)
    suspend fun deleteTransaction(transaction: TransactionEntity)
    suspend fun deleteTransactionById(id: Long)
}

interface BudgetDao {
    fun getBudgetsByMonth(month: String): Flow<List<BudgetEntity>>
    suspend fun insertBudget(budget: BudgetEntity): Long
    suspend fun insertBudgets(budgets: List<BudgetEntity>)
    suspend fun updateBudget(budget: BudgetEntity)
}

interface SavingsGoalDao {
    fun getAllSavingsGoals(): Flow<List<SavingsGoalEntity>>
    suspend fun insertSavingsGoal(goal: SavingsGoalEntity): Long
    suspend fun insertSavingsGoals(goals: List<SavingsGoalEntity>)
    suspend fun updateSavingsGoal(goal: SavingsGoalEntity)
    suspend fun deleteSavingsGoal(goal: SavingsGoalEntity)
}

interface AiRuleDao {
    fun getAllAiRules(): Flow<List<AiRuleEntity>>
    suspend fun insertAiRule(rule: AiRuleEntity): Long
    suspend fun insertAiRules(rules: List<AiRuleEntity>)
    suspend fun deleteAiRule(rule: AiRuleEntity)
    suspend fun deleteAiRuleById(ruleId: Long)
}
