package com.example.smartspend.ui.auth

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smartspend.data.local.dao.AiRuleDao
import com.example.smartspend.data.local.dao.BudgetDao
import com.example.smartspend.data.local.dao.CategoryDao
import com.example.smartspend.data.local.dao.SavingsGoalDao
import com.example.smartspend.data.local.dao.TransactionDao
import com.example.smartspend.data.local.dao.UserDao
import com.example.smartspend.data.local.entity.AiRuleEntity
import com.example.smartspend.data.local.entity.BudgetEntity
import com.example.smartspend.data.local.entity.CategoryEntity
import com.example.smartspend.data.local.entity.SavingsGoalEntity
import com.example.smartspend.data.local.entity.TransactionEntity
import com.example.smartspend.data.local.entity.UserEntity
import com.example.smartspend.data.repository.SmartSpendRepository
import com.example.smartspend.ui.theme.SmartSpendTheme
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isLoginSuccessful) {
        if (uiState.isLoginSuccessful) {
            onLoginSuccess()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .windowInsetsPadding(WindowInsets.systemBars),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight()
                    .padding(16.dp),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // App Logo / Icon
                    Surface(
                        modifier = Modifier.size(64.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.AccountBalanceWallet,
                                contentDescription = "SmartSpend Logo",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "SmartSpend",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Biết tiền đi đâu, tiêu có kế hoạch.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (uiState.isRegisterMode) {
                        OutlinedTextField(
                            value = uiState.name,
                            onValueChange = viewModel::updateName,
                            label = { Text("Họ tên") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Person, contentDescription = null)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = viewModel::updateEmail,
                        label = { Text("Email") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Email, contentDescription = null)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = viewModel::updatePassword,
                        label = { Text("Mật khẩu") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (passwordVisible) "Ẩn mật khẩu" else "Hiện mật khẩu"
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (uiState.errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (uiState.isRegisterMode) {
                                viewModel.register()
                            } else {
                                viewModel.login()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !uiState.isLoading
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (uiState.isRegisterMode) "Tạo tài khoản" else "Đăng nhập",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Text(
                            text = "  Hoặc  ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = { viewModel.loginWithGoogle() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Public,
                            contentDescription = "Google Icon",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tiếp tục với Google",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = { viewModel.toggleMode(!uiState.isRegisterMode) }
                    ) {
                        Text(
                            text = if (uiState.isRegisterMode) "Đã có tài khoản? Đăng nhập ngay" else "Chưa có tài khoản? Tạo tài khoản mới",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("ViewModelConstructorInComposable")
@Preview(showBackground = true, device = "id:pixel_6")
@Composable
fun LoginScreenPreview() {
    SmartSpendTheme {
        val repository = object : SmartSpendRepository(
            object : UserDao {
                override suspend fun getUserByEmail(email: String) = null
                override fun getUserById(id: Long) = flowOf<UserEntity?>(null)
                override suspend fun insertUser(user: UserEntity) = 1L
            },
            object : CategoryDao {
                override fun getAllCategories() = flowOf<List<CategoryEntity>>(emptyList())
                override fun getCategoriesByType(type: String) = flowOf<List<CategoryEntity>>(emptyList())
                override suspend fun getCategoryById(id: Long) = null
                override suspend fun insertCategory(category: CategoryEntity) = 1L
                override suspend fun insertCategories(categories: List<CategoryEntity>) {}
            },
            object : TransactionDao {
                override fun getAllTransactions() = flowOf<List<TransactionEntity>>(emptyList())
                override fun getTransactionsByMonth(yearMonth: String) = flowOf<List<TransactionEntity>>(emptyList())
                override fun getAiDraftTransactions() = flowOf<List<TransactionEntity>>(emptyList())
                override suspend fun insertTransaction(transaction: TransactionEntity) = 1L
                override suspend fun insertTransactions(transactions: List<TransactionEntity>) {}
                override suspend fun updateTransaction(transaction: TransactionEntity) {}
                override suspend fun deleteTransaction(transaction: TransactionEntity) {}
                override suspend fun deleteTransactionById(id: Long) {}
            },
            object : BudgetDao {
                override fun getBudgetsByMonth(month: String) = flowOf<List<BudgetEntity>>(emptyList())
                override suspend fun insertBudget(budget: BudgetEntity) = 1L
                override suspend fun insertBudgets(budgets: List<BudgetEntity>) {}
                override suspend fun updateBudget(budget: BudgetEntity) {}
            },
            object : SavingsGoalDao {
                override fun getAllSavingsGoals() = flowOf<List<SavingsGoalEntity>>(emptyList())
                override suspend fun insertSavingsGoal(goal: SavingsGoalEntity) = 1L
                override suspend fun insertSavingsGoals(goals: List<SavingsGoalEntity>) {}
                override suspend fun updateSavingsGoal(goal: SavingsGoalEntity) {}
                override suspend fun deleteSavingsGoal(goal: SavingsGoalEntity) {}
            },
            object : AiRuleDao {
                override fun getAllAiRules() = flowOf<List<AiRuleEntity>>(emptyList())
                override suspend fun insertAiRule(rule: AiRuleEntity) = 1L
                override suspend fun insertAiRules(rules: List<AiRuleEntity>) {}
                override suspend fun deleteAiRule(rule: AiRuleEntity) {}
                override suspend fun deleteAiRuleById(ruleId: Long) {}
            }
        ) {}
        val vm = LoginViewModel(repository)
        LoginScreen(viewModel = vm, onLoginSuccess = {})
    }
}
