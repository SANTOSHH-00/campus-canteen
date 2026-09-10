package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.firebase.ItemDocument
import com.example.data.firebase.OrderDocument
import com.example.data.firebase.OwnerDocument
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DeliveryOrange
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream
import com.example.ui.viewmodel.DashboardUiState
import com.example.ui.viewmodel.OwnerDashboardViewModel
import com.example.ui.state.CanteenAppState
import java.util.Calendar

// ── PRIVATE THEME TOKENS ──────────────────────────────────────────────────────
private val OrangeAccent = Color(0xFFFF5200)
private val AmberBrand = Color(0xFFEBA53D)
private val CardBackground = Color(0xFFFFFFFF)
private val PageBackground = WarmCream
private val SelectedNavBg = Color(0xFFFFF0E8)

// ── NAV ITEM MODEL ────────────────────────────────────────────────────────────
private data class NavItem(
    val label: String,
    val icon: ImageVector,
    val badge: Int = 0,
    val tag: String = "",
)

// ─────────────────────────────────────────────────────────────────────────────
//  MAIN SCREEN
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun OwnerDashboardScreen(
    owner: OwnerDocument?,
    onLogout: () -> Unit,
    onNavigateToManageItems: () -> Unit = {},
    onNavigateToInventory: () -> Unit = {},
    onNavigateToOrders: () -> Unit = {},
    dashboardViewModel: OwnerDashboardViewModel = viewModel(),
    appState: CanteenAppState? = null,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(owner) {
        if (owner != null) dashboardViewModel.setOwner(owner)
    }

    val uiState by dashboardViewModel.uiState.collectAsState()
    val ownerOrders by dashboardViewModel.orders.collectAsState()

    var drawerOpen by remember { mutableStateOf(false) }
    var selectedNavItem by remember { mutableStateOf("Dashboard") }
    var showCloseReasonDialog by remember { mutableStateOf(false) }
    var selectedReason by remember { mutableStateOf("Break") }
    var customReason by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PageBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("owner_dashboard_screen"),
    ) {
        when (val state = uiState) {

            // ── LOADING ──────────────────────────────────────────────────────
            is DashboardUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = OrangeAccent, strokeWidth = 3.dp)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "Loading Your Canteen Dashboard...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextMuted,
                        )
                    }
                }
            }

            // ── ERROR ─────────────────────────────────────────────────────────
            is DashboardUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = state.message,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDark,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(18.dp))
                        TextButton(onClick = { dashboardViewModel.refresh() }) {
                            Text("Retry Loading", color = OrangeAccent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── READY ─────────────────────────────────────────────────────────
            is DashboardUiState.Ready -> {
                val navItems = listOf(
                    NavItem("Dashboard", Icons.Default.Home, tag = "nav_dashboard"),
                    NavItem("Menu Management", Icons.Default.RestaurantMenu, tag = "nav_menu_mgmt"),
                    NavItem("Customers", Icons.Default.Group, tag = "nav_customers"),
                    NavItem("Discounts & Offers", Icons.Default.LocalOffer, tag = "nav_discounts"),
                    NavItem("Settings", Icons.Default.Settings, tag = "nav_settings"),
                    NavItem("Staff Management", Icons.Default.ManageAccounts, tag = "nav_staff"),
                )

                Column(Modifier.fillMaxSize().background(PageBackground)) {

                    // ── TOP BAR (Shown on Dashboard or when drawer is open; hidden when screen has a back arrow)
                    val showConstantTopBar = drawerOpen || (selectedNavItem != "Profile" && selectedNavItem != "Customers")
                    if (showConstantTopBar) {
                        OwnerTopBar(
                            drawerOpen = drawerOpen,
                            onHamburgerClick = { drawerOpen = !drawerOpen },
                            onProfileClick = {
                                selectedNavItem = "Profile"
                                drawerOpen = false
                            },
                        )
                    }

                    // ── ANIMATED CONTENT AREA ────────────────────────────────
                    AnimatedContent(
                        targetState = drawerOpen,
                        transitionSpec = {
                            if (targetState) {
                                (fadeIn(tween(280)) + slideInHorizontally(tween(280)) { -it / 5 }) togetherWith
                                    (fadeOut(tween(220)) + slideOutHorizontally(tween(220)) { it / 5 })
                            } else {
                                (fadeIn(tween(280)) + slideInHorizontally(tween(280)) { it / 5 }) togetherWith
                                    (fadeOut(tween(220)) + slideOutHorizontally(tween(220)) { -it / 5 })
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        label = "drawer_toggle_anim",
                    ) { isDrawerOpen ->
                        if (isDrawerOpen) {
                            OwnerNavDrawer(
                                owner = state.owner,
                                canteenName = state.canteen.name,
                                isCanteenOpen = state.canteen.isOpen,
                                closeReason = state.canteen.closeReason,
                                selectedItem = selectedNavItem,
                                navItems = navItems,
                                onItemClick = { item ->
                                    selectedNavItem = item
                                    drawerOpen = false
                                    when (item) {
                                        "Menu Management" -> onNavigateToManageItems()
                                        else -> {}
                                    }
                                },
                                onToggleCanteen = {
                                    if (state.canteen.isOpen) showCloseReasonDialog = true
                                    else dashboardViewModel.setCanteenOpenStatus(true)
                                },
                                onLogout = onLogout,
                            )
                        } else {
                            when (selectedNavItem) {
                                "Profile" -> {
                                    OwnerProfileScreen(
                                        owner = state.owner,
                                        onBack = { selectedNavItem = "Dashboard" },
                                        onLogout = onLogout,
                                        onNavigateToManageItems = onNavigateToManageItems,
                                        onNavigateToOrders = onNavigateToOrders,
                                        appState = appState,
                                        dashboardViewModel = dashboardViewModel,
                                    )
                                }
                                "Customers" -> {
                                    OwnerCustomersScreen(
                                        orders = ownerOrders.ifEmpty { state.recentOrders },
                                        onBack = { selectedNavItem = "Dashboard" },
                                    )
                                }
                                else -> {
                                    OwnerDashboardBody(
                                        state = state,
                                        onNavigateToManageItems = onNavigateToManageItems,
                                        onNavigateToOrders = onNavigateToOrders,
                                        onToggleCanteen = {
                                            if (state.canteen.isOpen) showCloseReasonDialog = true
                                            else dashboardViewModel.setCanteenOpenStatus(true)
                                        },
                                        onAdvanceOrderStatus = { orderId, nextStatus ->
                                            dashboardViewModel.updateOrderStatus(orderId, nextStatus)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── CLOSE CANTEEN REASON DIALOG ──────────────────────────────────────────
    if (showCloseReasonDialog) {
        val reasons = listOf("Break", "Stock unavailable", "Kitchen maintenance", "End of day", "Other")
        AlertDialog(
            onDismissRequest = { showCloseReasonDialog = false },
            title = { Text("Close Canteen?", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column {
                    Text(
                        text = "Select or enter a reason for closing. This will be shown to campus students.",
                        fontSize = 13.sp,
                        color = Color(0xFF4B5563),
                    )
                    Spacer(Modifier.height(14.dp))
                    reasons.forEach { r ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedReason = r }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedReason == r,
                                onClick = { selectedReason = r },
                                colors = RadioButtonDefaults.colors(selectedColor = OrangeAccent),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = r, fontSize = 14.sp, color = TextDark)
                        }
                    }
                    if (selectedReason == "Other") {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customReason,
                            onValueChange = { customReason = it },
                            placeholder = { Text("Enter reason", fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val finalReason =
                            if (selectedReason == "Other") customReason.ifBlank { "Closed" } else selectedReason
                        dashboardViewModel.setCanteenOpenStatus(false, finalReason)
                        showCloseReasonDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626)),
                ) {
                    Text("Confirm & Close", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseReasonDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = PureWhite,
            shape = RoundedCornerShape(20.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  TOP BAR (Constant Upper Navigation Bar: 3 lines, QuickBite, notifications, profile)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun OwnerTopBar(
    drawerOpen: Boolean = false,
    onHamburgerClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(WarmCream)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Constant 3 lines hamburger menu icon
            IconButton(
                onClick = onHamburgerClick,
                modifier = Modifier.testTag("owner_hamburger_button"),
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = BlackPrimary,
                    modifier = Modifier.size(26.dp),
                )
            }

            Spacer(Modifier.width(2.dp))

            // QuickBite logo & app name
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(BlackPrimary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Fastfood,
                        contentDescription = null,
                        tint = PureWhite,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "QuickBite",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = BlackPrimary,
                )
            }

            Spacer(Modifier.weight(1f))

            // Notification bell with alert dot
            IconButton(onClick = {}) {
                Box {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = BlackPrimary,
                        modifier = Modifier.size(26.dp),
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(OrangeAccent)
                            .align(Alignment.TopEnd),
                    )
                }
            }

            // Owner Profile button
            IconButton(
                onClick = onProfileClick,
                modifier = Modifier.testTag("owner_profile_top_button"),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(OrangeAccent.copy(alpha = 0.12f))
                        .border(1.5.dp, OrangeAccent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        tint = OrangeAccent,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        HorizontalDivider(thickness = 0.8.dp, color = BorderGray.copy(alpha = 0.45f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  NAVIGATION DRAWER  (matches Image 1 reference)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OwnerNavDrawer(
    owner: OwnerDocument,
    canteenName: String,
    isCanteenOpen: Boolean,
    closeReason: String,
    selectedItem: String,
    navItems: List<NavItem>,
    onItemClick: (String) -> Unit,
    onToggleCanteen: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureWhite),
    ) {
        // ── Owner profile section ────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onItemClick("Profile") }
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(OrangeAccent.copy(alpha = 0.12f))
                    .border(2.dp, OrangeAccent.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = owner.name.take(1).uppercase().ifBlank { "C" },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = OrangeAccent,
                )
            }

            Spacer(Modifier.width(14.dp))

            val blockInfo = if (owner.block.isNotBlank()) "Block ${owner.block}" else ""
            Column {
                Text(
                    text = owner.name.ifBlank { "Canteen Owner" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                // Canteen name and Block shown clearly inside the drawer
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (blockInfo.isNotBlank()) "${canteenName.ifBlank { "Main Canteen" }} • $blockInfo" else canteenName.ifBlank { "Main Canteen" },
                        fontSize = 13.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        HorizontalDivider(thickness = 0.8.dp, color = Color(0xFFEEEEEE))
        Spacer(Modifier.height(6.dp))

        var showLogoutConfirmation by remember { mutableStateOf(false) }

        // ── Scrollable nav items ─────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            navItems.forEach { item ->
                DrawerNavItem(
                    item = item,
                    isSelected = item.label == selectedItem,
                    onClick = { onItemClick(item.label) },
                )
            }

            Spacer(Modifier.height(6.dp))
            HorizontalDivider(
                thickness = 0.8.dp,
                color = Color(0xFFEEEEEE),
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(6.dp))

            // Logout with confirmation
            DrawerNavItem(
                item = NavItem("Logout", Icons.AutoMirrored.Filled.Logout, tag = "owner_logout_button"),
                isSelected = false,
                tintOverride = Color(0xFFDC2626),
                onClick = { showLogoutConfirmation = true },
            )
            Spacer(Modifier.height(16.dp))
        }

        if (showLogoutConfirmation) {
            AlertDialog(
                onDismissRequest = { showLogoutConfirmation = false },
                title = {
                    Text(
                        text = "Confirm Logout",
                        fontWeight = FontWeight.Bold,
                        color = TextDark,
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to log out of the Owner Dashboard?",
                        color = TextMuted,
                        fontSize = 14.sp,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showLogoutConfirmation = false
                            onLogout()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Log Out", color = PureWhite, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutConfirmation = false }) {
                        Text("Cancel", color = TextDark)
                    }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = PureWhite,
            )
        }
    }
}

@Composable
private fun DrawerNavItem(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    tintOverride: Color? = null,
) {
    val iconTint = tintOverride ?: if (isSelected) OrangeAccent else Color(0xFF6B7280)
    val textColor = tintOverride ?: if (isSelected) OrangeAccent else TextDark
    val tagMod = if (item.tag.isNotBlank()) Modifier.testTag(item.tag) else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) SelectedNavBg else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .then(tagMod)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = item.label,
            fontSize = 14.5.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
            modifier = Modifier.weight(1f),
        )
        if (item.badge > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(OrangeAccent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${item.badge}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  MAIN DASHBOARD BODY  (matches Image 2 reference)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OwnerDashboardBody(
    state: DashboardUiState.Ready,
    onNavigateToManageItems: () -> Unit,
    onNavigateToOrders: () -> Unit,
    onToggleCanteen: () -> Unit,
    onAdvanceOrderStatus: (String, String) -> Unit,
) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when {
        hour < 12 -> "Good Morning"
        hour < 17 -> "Good Afternoon"
        else -> "Good Evening"
    }
    val ownerFirstName = state.owner.name.split(" ").firstOrNull().orEmpty().ifBlank { "Owner" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 36.dp),
    ) {
        Spacer(Modifier.height(18.dp))

        // ── Greeting — NO canteen name displayed here ────────────────────────
        Text(
            text = "$greeting, $ownerFirstName 👋",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(14.dp))

        Spacer(Modifier.height(18.dp))

        // ── Today's Overview (orange hero card) ──────────────────────────────
        TodayOverviewCard(
            revenueText = "₹ ${state.metrics.todayRevenue}",
            totalOrders = state.metrics.todayOrdersCount,
            activeOrders = state.metrics.newOrdersCount + state.metrics.preparingOrdersCount,
            readyOrders = state.metrics.readyOrdersCount,
        )

        Spacer(Modifier.height(24.dp))

        // ── Quick Actions ────────────────────────────────────────────────────
        Text(
            text = "Quick Actions",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuickDashboardAction(
                label = "Add Item",
                icon = Icons.Default.Add,
                backgroundColor = Color(0xFFFFF7ED),
                iconColor = Color(0xFFEA580C),
                borderColor = Color(0xFFFFEDD5),
                modifier = Modifier.weight(1f),
                testTag = "quick_action_manage_items",
                onClick = onNavigateToManageItems,
            )
            QuickDashboardAction(
                label = "Orders",
                icon = Icons.Default.ShoppingBag,
                backgroundColor = Color(0xFFEFF6FF),
                iconColor = Color(0xFF2563EB),
                borderColor = Color(0xFFDBEAFE),
                modifier = Modifier.weight(1f),
                testTag = "quick_action_order_queue",
                onClick = onNavigateToOrders,
            )
            QuickDashboardAction(
                label = "Reports",
                icon = Icons.Default.Analytics,
                backgroundColor = Color(0xFFFAF5FF),
                iconColor = Color(0xFF7C3AED),
                borderColor = Color(0xFFF3E8FF),
                modifier = Modifier.weight(1f),
                onClick = {},
            )
            QuickDashboardAction(
                label = "Canteen",
                icon = Icons.Default.Storefront,
                backgroundColor = Color(0xFFFEFCE8),
                iconColor = Color(0xFFD97706),
                borderColor = Color(0xFFFEF08A),
                modifier = Modifier.weight(1f),
                onClick = onToggleCanteen,
            )
        }

        Spacer(Modifier.height(26.dp))

        // ── Recent Orders (Delivered & Completed Orders Only) ───────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Recent Orders",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                )
                Text(
                    text = "Delivered & completed orders",
                    fontSize = 12.sp,
                    color = TextMuted,
                )
            }
            Text(
                text = "View All",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = OrangeAccent,
                modifier = Modifier.clickable { onNavigateToOrders() },
            )
        }

        Spacer(Modifier.height(10.dp))

        if (state.recentOrders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBackground)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.ShoppingBag,
                        contentDescription = null,
                        tint = Color(0xFFD1D5DB),
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("No delivered orders yet today.", fontSize = 13.5.sp, color = TextMuted)
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.recentOrders.take(5).forEach { order ->
                    DashboardOrderRow(
                        order = order,
                        onAdvanceStatus = { onAdvanceOrderStatus(order.orderId, it) },
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  TODAY'S OVERVIEW CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TodayOverviewCard(
    revenueText: String,
    totalOrders: Int,
    activeOrders: Int,
    readyOrders: Int,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(OrangeAccent)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Today's Overview",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PureWhite.copy(alpha = 0.88f),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(PureWhite.copy(alpha = 0.22f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(PureWhite),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(text = "Live", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(text = revenueText, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = PureWhite)
            Text(text = "Total Sales", fontSize = 12.sp, color = PureWhite.copy(alpha = 0.75f))

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OverviewStat(label = "Orders", value = "$totalOrders")
                OverviewStat(label = "Active Orders", value = "$activeOrders")
                OverviewStat(label = "Ready to Pickup", value = "$readyOrders")
            }
        }
    }
}

@Composable
private fun OverviewStat(label: String, value: String) {
    Column {
        Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = PureWhite)
        Text(text = label, fontSize = 11.sp, color = PureWhite.copy(alpha = 0.75f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  QUICK ACTION ICON BUTTON
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QuickDashboardAction(
    label: String,
    icon: ImageVector,
    backgroundColor: Color,
    iconColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    testTag: String = "",
    onClick: () -> Unit,
) {
    val tagMod = if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .then(tagMod)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(backgroundColor)
                .border(1.2.dp, borderColor, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextDark,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  DASHBOARD ORDER ROW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DashboardOrderRow(
    order: OrderDocument,
    onAdvanceStatus: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFFF0E6)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Fastfood,
                contentDescription = null,
                tint = OrangeAccent,
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = order.studentName.ifBlank { "Campus Student" },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = "₹${order.totalAmount}", fontSize = 12.sp, color = TextMuted)
        }

        Spacer(Modifier.width(8.dp))

        when (order.status.uppercase()) {
            "NEW" -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(DeliveryOrange)
                        .clickable { onAdvanceStatus("PREPARING") }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Start Prep", color = PureWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            "PREPARING" -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(AmberBrand.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Preparing", color = AmberBrand, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            "READY" -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(AccentGreen.copy(alpha = 0.15f))
                        .clickable { onAdvanceStatus("COMPLETED") }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Ready", color = AccentGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFE5E7EB))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(order.status, color = Color(0xFF4B5563), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Keep data model reference to suppress unused-import warnings
@Suppress("unused")
private fun unusedRef(i: ItemDocument) = Unit
