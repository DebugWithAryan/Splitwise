package com.aryan.expensesplitwise

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.aryan.expensesplitwise.domain.model.Expense
import com.aryan.expensesplitwise.domain.model.Friend
import com.aryan.expensesplitwise.domain.model.Message
import com.aryan.expensesplitwise.domain.model.Settlement
import com.aryan.expensesplitwise.domain.model.TransactionType
import com.aryan.expensesplitwise.presentation.ExpenseViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestSmsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(
                this@MainActivity,
                "SMS permission granted. App will now detect payment messages.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(
                this@MainActivity,
                "SMS permission denied. You'll need to add expenses manually.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkSmsPermission()
        setContent {
            ExpenseSplitterApp()
        }
    }

    private fun checkSmsPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED -> {}

            shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS) -> {
                Toast.makeText(
                    this,
                    "SMS permission needed to automatically detect payment expenses",
                    Toast.LENGTH_LONG
                ).show()
                requestSmsPermission.launch(Manifest.permission.READ_SMS)
            }

            else -> {
                requestSmsPermission.launch(Manifest.permission.READ_SMS)
            }
        }
    }

    // Dark Theme Colors
    private val DarkBackground = Color(0xFF0A0E27)
    private val DarkSurface = Color(0xFF151B3D)
    private val AccentPurple = Color(0xFF7C4DFF)
    private val AccentCyan = Color(0xFF00E5FF)
    private val AccentGreen = Color(0xFF00E676)
    private val AccentPink = Color(0xFFFF4081)
    private val TextPrimary = Color(0xFFE8EAED)
    private val TextSecondary = Color(0xFFB0B3B8)



    @OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
    @Composable
    fun ExpenseSplitterApp(viewModel: ExpenseViewModel = hiltViewModel()) {
        var selectedTab by remember { mutableStateOf(0) }
        val tabs = listOf("Expenses", "Messages", "Balances", "Friends")

        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = AccentPurple,
                secondary = AccentCyan,
                tertiary = AccentGreen,
                background = DarkBackground,
                surface = DarkSurface,
                onPrimary = Color.White,
                onSecondary = Color.White,
                onBackground = TextPrimary,
                onSurface = TextPrimary
            )
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                "Smart Expense Splitter",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = DarkSurface,
                            titleContentColor = AccentPurple
                        )
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = DarkSurface,
                        contentColor = AccentPurple
                    ) {
                        tabs.forEachIndexed { index, title ->
                            NavigationBarItem(
                                icon = {
                                    AnimatedIcon(
                                        isSelected = selectedTab == index,
                                        icon = when (index) {
                                            0 -> Icons.Default.Receipt
                                            1 -> Icons.Default.Message
                                            2 -> Icons.Default.AccountBalance
                                            else -> Icons.Default.People
                                        }
                                    )
                                },
                                label = { Text(title) },
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AccentPurple,
                                    selectedTextColor = AccentPurple,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                    indicatorColor = AccentPurple.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                },
                containerColor = DarkBackground
            ) { padding ->
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                ) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            slideInHorizontally { it } + fadeIn() with
                                    slideOutHorizontally { -it } + fadeOut()
                        },
                        label = "tab_animation"
                    ) { tab ->
                        when (tab) {
                            0 -> ExpensesScreen(viewModel)
                            1 -> MessagesScreen(viewModel)
                            2 -> BalancesScreen(viewModel)
                            3 -> FriendsScreen(viewModel)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun AnimatedIcon(isSelected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector) {
        val scale by animateFloatAsState(
            targetValue = if (isSelected) 1.2f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "icon_scale"
        )

        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.scale(scale)
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ExpensesScreen(viewModel: ExpenseViewModel) {
        val expenses by viewModel.expenses.collectAsState()
        val friends by viewModel.friends.collectAsState()
        var showAddDialog by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxSize()) {
            if (expenses.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Receipt,
                    title = "No expenses yet",
                    subtitle = "Add an expense or send a message"
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(expenses, key = { it.id }) { expense ->
                        AnimatedExpenseCard(
                            expense = expense,
                            friends = friends.map { it.name },
                            onDelete = { viewModel.deleteExpense(expense) },
                            onUpdateSplit = { newSplit ->
                                viewModel.updateExpenseSplit(expense, newSplit)
                            }
                        )
                    }
                }
            }

            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = AccentPurple,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Expense")
            }

            if (showAddDialog) {
                AddExpenseDialog(
                    friends = friends.map { it.name },
                    onDismiss = { showAddDialog = false },
                    onAdd = { desc, amount, paidBy, splitBetween ->
                        viewModel.addExpense(desc, amount, paidBy, splitBetween)
                        showAddDialog = false
                    }
                )
            }
        }
    }

    @Composable
    fun AnimatedExpenseCard(
        expense: Expense,
        friends: List<String>,
        onDelete: () -> Unit,
        onUpdateSplit: (List<String>) -> Unit
    ) {
        var isExpanded by remember { mutableStateOf(false) }
        var showEditDialog by remember { mutableStateOf(false) }
        var showDeleteDialog by remember { mutableStateOf(false) }
        val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

        // Determine colors based on transaction type
        val accentColor = if (expense.transactionType == TransactionType.INCOMING) {
            AccentGreen
        } else {
            AccentCyan
        }

        val transactionIcon = if (expense.transactionType == TransactionType.INCOMING) {
            Icons.Default.CallReceived
        } else {
            Icons.Default.CallMade
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            colors = CardDefaults.cardColors(
                containerColor = DarkSurface
            ),
            shape = RoundedCornerShape(16.dp),
            onClick = { isExpanded = !isExpanded }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                transactionIcon,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                expense.description,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            if (expense.detectedFromMessage) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(AccentCyan.copy(alpha = 0.2f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "AUTO",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AccentCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Text(
                            dateFormat.format(Date(expense.timestamp)),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Text(
                            if (expense.transactionType == TransactionType.INCOMING) "Incoming" else "Outgoing",
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        "₹${String.format("%.2f", expense.amount)}",
                        style = MaterialTheme.typography.titleLarge,
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Divider(color = TextSecondary.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    if (expense.transactionType == TransactionType.INCOMING) "Received from" else "Paid by",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Text(
                                    expense.paidBy,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "Per person",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Text(
                                    "₹${String.format("%.2f", expense.amount / expense.splitBetween.size)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AccentCyan,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            if (expense.transactionType == TransactionType.INCOMING) "Beneficiaries" else "Split between",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            expense.splitBetween.forEach { person ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(accentColor.copy(alpha = 0.3f), AccentCyan.copy(alpha = 0.3f))
                                            )
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        person,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showEditDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = AccentCyan
                                )
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit Split")
                            }

                            OutlinedButton(
                                onClick = { showDeleteDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = AccentPink
                                )
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }

        if (showEditDialog) {
            EditSplitDialog(
                expense = expense,
                friends = friends,
                onDismiss = { showEditDialog = false },
                onConfirm = { newSplit ->
                    onUpdateSplit(newSplit)
                    showEditDialog = false
                }
            )
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Expense?", color = TextPrimary) },
                text = { Text("Are you sure you want to delete this expense?", color = TextSecondary) },
                confirmButton = {
                    TextButton(onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }) {
                        Text("Delete", color = AccentPink)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = DarkSurface
            )
        }
    }

    @Composable
    fun EditSplitDialog(
        expense: Expense,
        friends: List<String>,
        onDismiss: () -> Unit,
        onConfirm: (List<String>) -> Unit
    ) {
        var selectedFriends by remember { mutableStateOf(expense.splitBetween.toSet()) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Edit Split", color = TextPrimary) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Select who to split with:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    friends.forEach { friend ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedFriends.contains(friend),
                                onCheckedChange = { checked ->
                                    selectedFriends = if (checked) {
                                        selectedFriends + friend
                                    } else {
                                        selectedFriends - friend
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = AccentPurple,
                                    uncheckedColor = TextSecondary
                                )
                            )
                            Text(friend, color = TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onConfirm(selectedFriends.toList()) },
                    enabled = selectedFriends.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentPurple
                    )
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    @Composable
    fun EmptyState(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        title: String,
        subtitle: String
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "icon_pulse"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .scale(scale),
                tint = TextSecondary.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = TextSecondary,
                fontWeight = FontWeight.Bold
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary.copy(alpha = 0.7f)
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AddExpenseDialog(
        friends: List<String>,
        onDismiss: () -> Unit,
        onAdd: (String, Double, String, List<String>) -> Unit
    ) {
        var description by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var paidBy by remember { mutableStateOf(friends.firstOrNull() ?: "") }
        var selectedFriends by remember { mutableStateOf(setOf<String>()) }
        var expanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Add Expense", color = TextPrimary) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentPurple,
                            focusedLabelColor = AccentPurple,
                            cursorColor = AccentPurple,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount") },
                        modifier = Modifier.fillMaxWidth(),
                        prefix = { Text("₹", color = AccentGreen) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentPurple,
                            focusedLabelColor = AccentPurple,
                            cursorColor = AccentPurple,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = paidBy,
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("Paid by") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentPurple,
                                focusedLabelColor = AccentPurple,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(DarkSurface)
                        ) {
                            friends.forEach { friend ->
                                DropdownMenuItem(
                                    text = { Text(friend, color = TextPrimary) },
                                    onClick = {
                                        paidBy = friend
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        "Split between:",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    friends.forEach { friend ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedFriends.contains(friend),
                                onCheckedChange = { checked ->
                                    selectedFriends = if (checked) {
                                        selectedFriends + friend
                                    } else {
                                        selectedFriends - friend
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = AccentPurple,
                                    uncheckedColor = TextSecondary
                                )
                            )
                            Text(friend, color = TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amountValue = amount.toDoubleOrNull()
                        if (description.isNotBlank() && amountValue != null &&
                            amountValue > 0 && selectedFriends.isNotEmpty()
                        ) {
                            onAdd(description, amountValue, paidBy, selectedFriends.toList())
                        }
                    },
                    enabled = description.isNotBlank() &&
                            amount.toDoubleOrNull() != null &&
                            selectedFriends.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentPurple
                    )
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    @Composable
    fun MessagesScreen(viewModel: ExpenseViewModel) {
        val messages by viewModel.messages.collectAsState()
        val isScanning by viewModel.isScanningSms.collectAsState()
        var messageText by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Message Parser",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                FilledIconButton(
                    onClick = { viewModel.scanHistoricalSms(30) },
                    enabled = !isScanning,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = AccentPurple
                    )
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White
                        )
                    } else {
                        Icon(Icons.Default.Search, contentDescription = "Scan SMS")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = DarkSurface
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Try these examples:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    listOf(
                        "I paid ₹50 for pizza for everyone",
                        "Spent 120 on dinner with John and Sarah",
                        "₹45 for movie tickets"
                    ).forEach { example ->
                        Text(
                            "• $example",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tap scan button to find payment SMS from last 30 days",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Type a message...", color = TextSecondary) },
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentPurple,
                        cursorColor = AccentPurple,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                FilledIconButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            viewModel.addMessage(messageText)
                            messageText = ""
                        }
                    },
                    enabled = messageText.isNotBlank(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = AccentPurple
                    )
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (messages.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Message,
                    title = "No messages yet",
                    subtitle = "Send a message to test the parser"
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageCard(message)
                    }
                }
            }
        }
    }

    @Composable
    fun MessageCard(message: Message) {
        val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (message.processed)
                    AccentGreen.copy(alpha = 0.2f)
                else
                    DarkSurface
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Text(
                        dateFormat.format(Date(message.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                if (message.processed) {
                    Column(horizontalAlignment = Alignment.End) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Processed",
                            tint = AccentGreen
                        )
                        Text(
                            "Processed",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentGreen
                        )
                    }
                } else {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = "Pending",
                        tint = TextSecondary
                    )
                }
            }
        }
    }

    @Composable
    fun BalancesScreen(viewModel: ExpenseViewModel) {
        val balances by viewModel.balances.collectAsState()
        val settlements by viewModel.settlements.collectAsState()
        val expenses by viewModel.expenses.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                "Balances",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (expenses.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.AccountBalance,
                    title = "No balances to show",
                    subtitle = "Add expenses to see balances"
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = DarkSurface
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Current Balances",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentPurple,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        balances.forEach { balance ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(AccentPurple, AccentCyan)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            balance.person.first().toString(),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        balance.person,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = TextPrimary
                                    )
                                }

                                Text(
                                    if (balance.amount >= 0)
                                        "+₹${String.format("%.2f", balance.amount)}"
                                    else
                                        "-₹${String.format("%.2f", -balance.amount)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (balance.amount >= 0) AccentGreen else AccentPink,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (balance != balances.last()) {
                                Divider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = TextSecondary.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    "Suggested Settlements",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (settlements.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = AccentGreen.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AccentGreen,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                "All settled up!",
                                style = MaterialTheme.typography.titleMedium,
                                color = AccentGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(settlements) { settlement ->
                            SettlementCard(settlement)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun SettlementCard(settlement: Settlement) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = DarkSurface
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        settlement.from,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = "pays",
                        tint = AccentPurple
                    )
                    Text(
                        settlement.to,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                }
                Text(
                    "₹${String.format("%.2f", settlement.amount)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = AccentGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    @Composable
    fun FriendsScreen(viewModel: ExpenseViewModel) {
        val friends by viewModel.friends.collectAsState()
        var showAddDialog by remember { mutableStateOf(false) }
        var friendName by remember { mutableStateOf("") }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    "Friends",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (friends.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.People,
                        title = "No friends added yet",
                        subtitle = "Add friends to split expenses"
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(friends, key = { it.id }) { friend ->
                            FriendCard(
                                friend = friend,
                                onDelete = { viewModel.deleteFriend(friend) }
                            )
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = AccentPurple,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add Friend")
            }

            if (showAddDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showAddDialog = false
                        friendName = ""
                    },
                    title = { Text("Add Friend", color = TextPrimary) },
                    text = {
                        OutlinedTextField(
                            value = friendName,
                            onValueChange = { friendName = it },
                            label = { Text("Name") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentPurple,
                                focusedLabelColor = AccentPurple,
                                cursorColor = AccentPurple,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (friendName.isNotBlank()) {
                                    viewModel.addFriend(friendName)
                                    showAddDialog = false
                                    friendName = ""
                                }
                            },
                            enabled = friendName.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentPurple
                            )
                        ) {
                            Text("Add")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showAddDialog = false
                            friendName = ""
                        }) {
                            Text("Cancel", color = TextSecondary)
                        }
                    },
                    containerColor = DarkSurface
                )
            }
        }
    }

    @Composable
    fun FriendCard(friend: Friend, onDelete: () -> Unit) {
        var showDeleteDialog by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = DarkSurface
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(AccentPurple, AccentCyan)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            friend.name.first().toString().uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        friend.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = AccentPink
                    )
                }
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Remove Friend?", color = TextPrimary) },
                text = { Text("Are you sure you want to remove ${friend.name}?", color = TextSecondary) },
                confirmButton = {
                    TextButton(onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }) {
                        Text("Remove", color = AccentPink)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = DarkSurface
            )
        }
    }
}