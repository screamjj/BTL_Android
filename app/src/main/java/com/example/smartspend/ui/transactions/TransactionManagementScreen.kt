package com.example.smartspend.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartspend.data.local.entity.TransactionEntity
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionManagementScreen(
    viewModel: TransactionManagementViewModel,
    onAddTransactionClick: () -> Unit,
    onEditTransactionClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val currencyFormat = remember { NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Giao dịch",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Toàn bộ lịch sử thu chi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onAddTransactionClick) {
                        Icon(
                            imageVector = Icons.Rounded.AddCircle,
                            contentDescription = "Thêm giao dịch",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTransactionClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = "Thêm giao dịch")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Color Legend Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0F8278))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Xanh: Thu (Income)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFC62828))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Đỏ: Chi (Expense)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                placeholder = { Text("Tìm theo nội dung hoặc số tiền...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(imageVector = Icons.Rounded.Clear, contentDescription = "Xóa tìm kiếm")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Type Filters (Tất cả, Thu, Chi)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.selectedType == null,
                    onClick = { viewModel.updateSelectedType(null) },
                    label = { Text("Tất cả") }
                )
                FilterChip(
                    selected = uiState.selectedType == "INCOME",
                    onClick = { viewModel.updateSelectedType("INCOME") },
                    label = { Text("Thu") }
                )
                FilterChip(
                    selected = uiState.selectedType == "EXPENSE",
                    onClick = { viewModel.updateSelectedType("EXPENSE") },
                    label = { Text("Chi") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Category Filters Horizontal Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = uiState.selectedCategoryId == null,
                        onClick = { viewModel.updateSelectedCategory(null) },
                        label = { Text("Mọi danh mục") }
                    )
                }
                items(uiState.categories) { cat ->
                    FilterChip(
                        selected = uiState.selectedCategoryId == cat.id,
                        onClick = { viewModel.updateSelectedCategory(cat.id) },
                        label = { Text(cat.name) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Grouped transaction list by date
            if (uiState.filteredTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Không tìm thấy giao dịch nào.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val grouped = uiState.filteredTransactions.groupBy { it.date }
                    grouped.forEach { (date, transactions) ->
                        item {
                            Text(
                                text = date,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        items(transactions) { tx ->
                            val category = uiState.categories.firstOrNull { it.id == tx.categoryId }
                            TransactionManageCard(
                                transaction = tx,
                                categoryName = category?.name ?: "Chưa phân loại",
                                currencyFormat = currencyFormat,
                                onItemClick = { onEditTransactionClick(tx.id) },
                                onDeleteClick = { viewModel.deleteTransaction(tx) }
                            )
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun getCategoryTagStyle(categoryName: String): Pair<Color, Color> {
    return when (categoryName.lowercase().trim()) {
        "chưa phân loại" -> Pair(Color(0xFFFFF9C4), Color(0xFFF57F17)) // Amber / Yellow
        "ăn uống" -> Pair(Color(0xFFC8E6C9), Color(0xFF0F8278)) // Green / Teal
        "di chuyển", "mua sắm" -> Pair(Color(0xFFB2DFDB), Color(0xFF00796B)) // Teal
        "hóa đơn & tiện ích", "hóa đơn" -> Pair(Color(0xFFFFCCBC), Color(0xFFD84315)) // Orange
        "giải trí", "sức khỏe" -> Pair(Color(0xFFD1C4E9), Color(0xFF512DA8)) // Purple
        else -> Pair(Color(0xFFE0E0E0), Color(0xFF424242)) // Default
    }
}

@Composable
fun TransactionManageCard(
    transaction: TransactionEntity,
    categoryName: String,
    currencyFormat: NumberFormat,
    onItemClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isIncome = transaction.type == "INCOME"
    val amountColor = if (isIncome) Color(0xFF0F8278) else Color(0xFFC62828)
    val prefix = if (isIncome) "+" else "-"
    val (tagBg, tagText) = getCategoryTagStyle(categoryName)

    Card(
        onClick = onItemClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = if (isIncome) Color(0xFF0F8278).copy(alpha = 0.15f) else Color(0xFFC62828).copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isIncome) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                        contentDescription = null,
                        tint = amountColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Category pill with specific color styling matching requirement
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = tagBg
                    ) {
                        Text(
                            text = categoryName,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = tagText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Source account tag / note
                    if (!transaction.note.isNullOrBlank()) {
                        Text(
                            text = transaction.note!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$prefix${currencyFormat.format(transaction.amount)} đ",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = amountColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Xóa",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
