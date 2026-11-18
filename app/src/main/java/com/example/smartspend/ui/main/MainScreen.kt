package com.example.smartspend.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartspend.data.repository.SmartSpendRepository
import com.example.smartspend.ui.ai.*
import com.example.smartspend.ui.budget.BudgetScreen
import com.example.smartspend.ui.budget.BudgetViewModel
import com.example.smartspend.ui.dashboard.DashboardScreen
import com.example.smartspend.ui.dashboard.DashboardViewModel
import com.example.smartspend.ui.reports.ReportsScreen
import com.example.smartspend.ui.reports.ReportsViewModel
import com.example.smartspend.ui.savings.SavingsGoalsScreen
import com.example.smartspend.ui.savings.SavingsGoalsViewModel
import com.example.smartspend.ui.settings.SettingsScreen
import com.example.smartspend.ui.transactions.*

enum class AppTab(val title: String, val icon: ImageVector) {
    DASHBOARD("Tổng quan", Icons.Rounded.Dashboard),
    TRANSACTIONS("Giao dịch", Icons.Rounded.ReceiptLong),
    BUDGET("Ngân sách", Icons.Rounded.AccountBalanceWallet),
    SPENDMATE("SpendMate", Icons.Rounded.AutoAwesome),
    SETTINGS("Cài đặt", Icons.Rounded.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repository: SmartSpendRepository,
    onLogout: () -> Unit
) {
    var currentTab by remember { mutableStateOf(AppTab.DASHBOARD) }
    var showAiAutoScreen by remember { mutableStateOf(false) }
    var showReportsScreen by remember { mutableStateOf(false) }
    var showBudgetSubScreen by remember { mutableStateOf(false) }
    var showSavingsScreen by remember { mutableStateOf(false) }
    var showAddTransactionDialog by remember { mutableStateOf(false) }
    var editingTransactionId by remember { mutableStateOf<Long?>(null) }

    val dashboardViewModel: DashboardViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return DashboardViewModel(repository) as T
            }
        }
    )

    val transactionViewModel: TransactionManagementViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TransactionManagementViewModel(repository) as T
            }
        }
    )

    val spendMateViewModel: SpendMateViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SpendMateViewModel(repository) as T
            }
        }
    )

    val aiAutoViewModel: AiAutoCategorizationViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AiAutoCategorizationViewModel(repository) as T
            }
        }
    )

    val reportsViewModel: ReportsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ReportsViewModel(repository) as T
            }
        }
    )

    val budgetViewModel: BudgetViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return BudgetViewModel(repository) as T
            }
        }
    )

    val savingsGoalsViewModel: SavingsGoalsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SavingsGoalsViewModel(repository) as T
            }
        }
    )

    when {
        showAiAutoScreen -> {
            AiAutoCategorizationScreen(
                viewModel = aiAutoViewModel,
                onBackClick = { showAiAutoScreen = false }
            )
        }
        showReportsScreen -> {
            ReportsScreen(
                viewModel = reportsViewModel,
                onBackClick = { showReportsScreen = false }
            )
        }
        showBudgetSubScreen -> {
            BudgetScreen(
                viewModel = budgetViewModel,
                onBackClick = { showBudgetSubScreen = false }
            )
        }
        showSavingsScreen -> {
            SavingsGoalsScreen(
                viewModel = savingsGoalsViewModel,
                onBackClick = { showSavingsScreen = false }
            )
        }
        else -> {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        AppTab.values().forEach { tab ->
                            NavigationBarItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) },
                                label = { Text(tab.title) }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentTab) {
                        AppTab.DASHBOARD -> {
                            DashboardScreen(
                                viewModel = dashboardViewModel,
                                onAddTransactionClick = {
                                    editingTransactionId = null
                                    showAddTransactionDialog = true
                                },
                                onViewAllTransactions = {
                                    currentTab = AppTab.TRANSACTIONS
                                },
                                onNavigateToSettings = {
                                    currentTab = AppTab.SETTINGS
                                }
                            )
                        }
                        AppTab.TRANSACTIONS -> {
                            TransactionManagementScreen(
                                viewModel = transactionViewModel,
                                onAddTransactionClick = {
                                    editingTransactionId = null
                                    showAddTransactionDialog = true
                                },
                                onEditTransactionClick = { txId ->
                                    editingTransactionId = txId
                                    showAddTransactionDialog = true
                                }
                            )
                        }
                        AppTab.BUDGET -> {
                            BudgetScreen(viewModel = budgetViewModel)
                        }
                        AppTab.SPENDMATE -> {
                            SpendMateScreen(viewModel = spendMateViewModel)
                        }
                        AppTab.SETTINGS -> {
                            SettingsScreen(
                                onLogout = onLogout,
                                onNavigateToAiAuto = { showAiAutoScreen = true },
                                onNavigateToReports = { showReportsScreen = true },
                                onNavigateToBudget = { showBudgetSubScreen = true },
                                onNavigateToSavings = { showSavingsScreen = true }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddTransactionDialog) {
        val addEditViewModel: AddEditTransactionViewModel = viewModel(
            key = editingTransactionId?.toString() ?: "new",
            factory = object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return AddEditTransactionViewModel(repository, editingTransactionId) as T
                }
            }
        )

        LaunchedEffect(editingTransactionId, showAddTransactionDialog) {
            if (showAddTransactionDialog && editingTransactionId == null) {
                addEditViewModel.resetState()
            }
        }

        Dialog(
            onDismissRequest = { showAddTransactionDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            AddEditTransactionScreen(
                viewModel = addEditViewModel,
                onDismiss = { showAddTransactionDialog = false }
            )
        }
    }
}
