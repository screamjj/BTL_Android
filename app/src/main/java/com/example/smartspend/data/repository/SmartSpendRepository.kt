package com.example.smartspend.data.repository

import com.example.smartspend.data.local.dao.*
import com.example.smartspend.data.local.entity.*
import kotlinx.coroutines.flow.Flow

open class SmartSpendRepository(
    private val userDao: UserDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val savingsGoalDao: SavingsGoalDao,
    private val aiRuleDao: AiRuleDao
) {
    // Users
    suspend fun getUserByEmail(email: String): UserEntity? = userDao.getUserByEmail(email)
    suspend fun registerUser(user: UserEntity): Long = userDao.insertUser(user)

    // Categories
    val allCategories: Flow<List<CategoryEntity>> get() = categoryDao.getAllCategories()

    // Transactions
    val allTransactions: Flow<List<TransactionEntity>> get() = transactionDao.getAllTransactions()
    val aiDraftTransactions: Flow<List<TransactionEntity>> get() = transactionDao.getAiDraftTransactions()
    suspend fun insertTransaction(transaction: TransactionEntity): Long = transactionDao.insertTransaction(transaction)
    suspend fun updateTransaction(transaction: TransactionEntity) = transactionDao.updateTransaction(transaction)
    suspend fun deleteTransaction(transaction: TransactionEntity) = transactionDao.deleteTransaction(transaction)

    // Budgets
    fun getBudgetsByMonth(month: String): Flow<List<BudgetEntity>> = budgetDao.getBudgetsByMonth(month)
    suspend fun insertBudget(budget: BudgetEntity): Long = budgetDao.insertBudget(budget)

    // Savings goals
    val allSavingsGoals: Flow<List<SavingsGoalEntity>> get() = savingsGoalDao.getAllSavingsGoals()
    suspend fun insertSavingsGoal(goal: SavingsGoalEntity): Long = savingsGoalDao.insertSavingsGoal(goal)
    suspend fun updateSavingsGoal(goal: SavingsGoalEntity) = savingsGoalDao.updateSavingsGoal(goal)
    suspend fun deleteSavingsGoal(goal: SavingsGoalEntity) = savingsGoalDao.deleteSavingsGoal(goal)

    // AI rules
    val allAiRules: Flow<List<AiRuleEntity>> get() = aiRuleDao.getAllAiRules()
    suspend fun insertAiRule(rule: AiRuleEntity): Long = aiRuleDao.insertAiRule(rule)
    suspend fun deleteAiRule(rule: AiRuleEntity) = aiRuleDao.deleteAiRule(rule)
}
