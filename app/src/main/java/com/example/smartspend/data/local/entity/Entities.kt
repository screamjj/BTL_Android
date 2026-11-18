package com.example.smartspend.data.local.entity

// Entity thuần (chưa gắn Room). Khi có database thật, thêm @Entity/@PrimaryKey vào đây.

data class UserEntity(
    val id: Long = 0,
    val email: String,
    val password: String,
    val name: String
)

data class CategoryEntity(
    val id: Long = 0,
    val name: String,
    val icon: String,
    val type: String, // "EXPENSE" | "INCOME"
    val color: String
)

data class TransactionEntity(
    val id: Long = 0,
    val title: String,
    val amount: Double,
    val type: String, // "EXPENSE" | "INCOME"
    val categoryId: Long,
    val date: String, // yyyy-MM-dd
    val note: String = "",
    val isAiDraft: Boolean = false
)

data class BudgetEntity(
    val id: Long = 0,
    val categoryId: Long,
    val monthlyLimit: Double,
    val month: String // yyyy-MM
)

data class SavingsGoalEntity(
    val id: Long = 0,
    val title: String,
    val targetAmount: Double,
    val currentAmount: Double = 0.0,
    val targetDate: String,
    val icon: String = "savings"
)

data class AiRuleEntity(
    val id: Long = 0,
    val keyword: String,
    val categoryId: Long
)
