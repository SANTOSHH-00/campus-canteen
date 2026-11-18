// OwnerDashboardScreen: Jetpack Compose screen for OwnerDashboard user interface
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Settings
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
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
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
import com.example.ui.components.FoodImagePlaceholder
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DeliveryOrange
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.DrawerAmberLight
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream
import com.example.ui.viewmodel.DashboardUiState
import com.example.ui.viewmodel.OwnerDashboardViewModel
import com.example.ui.state.CanteenAppState
import java.util.Calendar
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

private val OrangeAccent = DrawerAmber
private val AmberBrand = DrawerAmber
private val CardBackground = PureWhite
private val PageBackground = WarmCream
private val SelectedNavBg = DrawerAmberLight

private data class NavItem(
    val label: String,
    val icon: ImageVector,
    val badge: Int = 0,
    val tag: String = "",
)

private data class NavSection(
    val title: String,
    val items: List<NavItem>,
)

//  MAIN SCREEN

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

    val context = LocalContext.current
    val isOnline by com.example.util.NetworkMonitor.isOnline.collectAsState()
    val uiState by dashboardViewModel.uiState.collectAsState()
    val ownerOrders by dashboardViewModel.orders.collectAsState()

    if (!isOnline) {
        NoInternetScreen(
            onRetrySuccess = {
                com.example.util.NetworkMonitor.refreshConnectivity()
                dashboardViewModel.refresh()
            },
            modifier = modifier,
        )
        return
    }

    var drawerOpen by remember { mutableStateOf(false) }
    var selectedNavItem by remember { mutableStateOf("Dashboard") }
    var showCloseReasonDialog by remember { mutableStateOf(false) }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var selectedReason by remember { mutableStateOf("Break") }
    var customReason by remember { mutableStateOf("") }

    // Intercept back gesture to close drawer, close dialog, or minimize app instead of logging out
    BackHandler(enabled = true) {
        when {
            drawerOpen -> drawerOpen = false
            showCloseReasonDialog -> showCloseReasonDialog = false
            selectedNavItem != "Dashboard" -> selectedNavItem = "Dashboard"
            else -> (context as? android.app.Activity)?.moveTaskToBack(true)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PageBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("owner_dashboard_screen"),
    ) {
        when (val state = uiState) {

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

            is DashboardUiState.Ready -> {
                // Reorganized sidebar sections: duplicate items (Scan & Verify, Menu Mgmt, Customers)
                // removed from sidebar; accessible prominently from Owner Home & Bottom Bar
                val navSections = listOf(
                    NavSection(
                        title = "MAIN",
                        items = listOf(
                            NavItem("Dashboard", Icons.Outlined.Dashboard, tag = "nav_dashboard"),
                            NavItem("Orders", Icons.AutoMirrored.Outlined.ReceiptLong, badge = state.metrics.newOrdersCount + state.metrics.preparingOrdersCount, tag = "nav_orders"),
                            NavItem("Order History", Icons.Outlined.History, tag = "nav_order_history"),
                        ),
                    ),
                    NavSection(
                        title = "MANAGEMENT",
                        items = listOf(
                            NavItem("Product Suggestions", Icons.Outlined.Lightbulb, tag = "nav_suggestions"),
                            NavItem("Reviews & Feedback", Icons.Outlined.RateReview, tag = "nav_reviews"),
                            NavItem("Notifications", Icons.Outlined.Notifications, tag = "nav_notifications"),
                        ),
                    ),
                    NavSection(
                        title = "ACCOUNT",
                        items = listOf(
                            NavItem("Profile", Icons.Outlined.Person, tag = "nav_profile"),
                            NavItem("Settings", Icons.Outlined.Settings, tag = "nav_settings"),
                            NavItem("Help & Support", Icons.Outlined.HelpOutline, tag = "nav_help"),
                        ),
                    ),
                )

                Column(Modifier.fillMaxSize().background(PageBackground)) {

                    val isSubScreenActive = selectedNavItem in listOf(
                        "Profile", "Customers", "Product Suggestions", "Orders",
                        "Order History", "Reviews & Feedback", "Notifications", "Settings", "Help & Support"
                    )
                    val showConstantTopBar = !drawerOpen && !isSubScreenActive
                    if (showConstantTopBar) {
                        OwnerTopBar(
                            drawerOpen = drawerOpen,
                            onHamburgerClick = { drawerOpen = !drawerOpen },
                            onNotificationsClick = {
                                selectedNavItem = "Notifications"
                                drawerOpen = false
                            },
                            onHomeClick = {
                                selectedNavItem = "Dashboard"
                                drawerOpen = false
                            },
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
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
                                    navSections = navSections,
                                    onCloseDrawer = { drawerOpen = false },
                                    onItemClick = { item ->
                                        selectedNavItem = item
                                        drawerOpen = false
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
                                    "Orders" -> {
                                        OwnerOrdersScreen(
                                            canteenId = state.canteen.id,
                                            onBack = { selectedNavItem = "Dashboard" },
                                            onNavigateToOrderDetails = { onNavigateToOrders() },
                                            dashboardViewModel = dashboardViewModel,
                                            initialTab = "Preparing",
                                        )
                                    }
                                    "Order History" -> {
                                        OwnerOrdersScreen(
                                            canteenId = state.canteen.id,
                                            onBack = { selectedNavItem = "Dashboard" },
                                            onNavigateToOrderDetails = { onNavigateToOrders() },
                                            dashboardViewModel = dashboardViewModel,
                                            initialTab = "Picked Up",
                                        )
                                    }
                                    "Customers" -> {
                                        OwnerCustomersScreen(
                                            orders = ownerOrders.ifEmpty { state.recentOrders },
                                            onBack = { selectedNavItem = "Dashboard" },
                                        )
                                    }
                                    "Product Suggestions" -> {
                                        OwnerSuggestionsScreen(
                                            owner = state.owner,
                                            onBack = { selectedNavItem = "Dashboard" },
                                        )
                                    }
                                    "Reviews & Feedback" -> {
                                        OwnerReviewsScreen(
                                            canteenName = state.canteen.name,
                                            onBack = { selectedNavItem = "Dashboard" },
                                        )
                                    }
                                    "Notifications" -> {
                                        OwnerNotificationsScreen(
                                            canteenName = state.canteen.name,
                                            onBack = { selectedNavItem = "Dashboard" },
                                        )
                                    }
                                    "Settings" -> {
                                        OwnerSettingsScreen(
                                            canteenName = state.canteen.name,
                                            isCanteenOpen = state.canteen.isOpen,
                                            onToggleCanteenStatus = { isOpen ->
                                                if (isOpen) {
                                                    dashboardViewModel.setCanteenOpenStatus(true)
                                                } else {
                                                    showCloseReasonDialog = true
                                                }
                                            },
                                            onBack = { selectedNavItem = "Dashboard" },
                                        )
                                    }
                                    "Help & Support" -> {
                                        OwnerHelpSupportScreen(
                                            canteenName = state.canteen.name,
                                            onBack = { selectedNavItem = "Dashboard" },
                                        )
                                    }
                                    else -> {
                                        OwnerDashboardBody(
                                            state = state,
                                            allOrders = ownerOrders.ifEmpty { state.recentOrders },
                                            onNavigateToManageItems = onNavigateToManageItems,
                                            onNavigateToOrders = onNavigateToOrders,
                                            onAdvanceOrderStatus = { orderId, nextStatus ->
                                                dashboardViewModel.updateOrderStatus(orderId, nextStatus)
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        val showBottomBar = !drawerOpen && (selectedNavItem == "Dashboard" || selectedNavItem == "Customers")
                        if (showBottomBar) {
                            OwnerBottomBar(
                                currentSection = selectedNavItem,
                                onSectionSelected = { section ->
                                    selectedNavItem = section
                                },
                                onAddItemClick = onNavigateToManageItems,
                                onScanQrClick = { showBarcodeScanner = true },
                                onOrdersClick = onNavigateToOrders,
                                activeOrdersCount = state.metrics.newOrdersCount + state.metrics.preparingOrdersCount,
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                    }
                }
            }
        }
    }

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

    if (showBarcodeScanner) {
        OwnerBarcodeScannerModal(
            onDismiss = { showBarcodeScanner = false },
            onOrderVerified = {
                dashboardViewModel.refresh()
            }
        )
    }
}

//  TOP BAR (Constant Upper Navigation Bar: 3 lines, QuickBite, notifications, profile)

@Composable
fun OwnerTopBar(
    drawerOpen: Boolean = false,
    onHamburgerClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
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

            // QuickBite logo & app name (Tappable to return to Dashboard)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onHomeClick() }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
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

            // Notification bell with alert dot (navigates to Notifications screen)
            IconButton(
                onClick = onNotificationsClick,
                modifier = Modifier.testTag("owner_notifications_button"),
            ) {
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
        }
        HorizontalDivider(thickness = 0.8.dp, color = BorderGray.copy(alpha = 0.45f))
    }
}

//  NAVIGATION DRAWER  (matches Image 1 reference)

@Composable
private fun OwnerNavDrawer(
    owner: OwnerDocument,
    canteenName: String,
    isCanteenOpen: Boolean,
    closeReason: String,
    selectedItem: String,
    navSections: List<NavSection>,
    onCloseDrawer: () -> Unit = {},
    onItemClick: (String) -> Unit,
    onToggleCanteen: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DrawerAmber)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(PureWhite.copy(alpha = 0.25f))
                    .border(1.5.dp, PureWhite, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = owner.name.take(1).uppercase().ifBlank { "C" },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = BlackPrimary,
                )
            }

            Spacer(Modifier.width(12.dp))

            val blockInfo = if (owner.block.isNotBlank()) "Block ${owner.block}" else ""
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onItemClick("Profile") },
            ) {
                Text(
                    text = owner.name.ifBlank { "Canteen Owner" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = BlackPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isCanteenOpen) BlackPrimary else Color(0xFFDC2626)),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (blockInfo.isNotBlank()) "${canteenName.ifBlank { "Main Canteen" }} • $blockInfo" else canteenName.ifBlank { "Main Canteen" },
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = BlackPrimary.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Close Drawer Icon Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(PureWhite.copy(alpha = 0.25f))
                    .clickable(onClick = onCloseDrawer)
                    .testTag("owner_drawer_close_button"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Menu",
                    tint = BlackPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Canteen Status Toggle Card - App Theme Matched (No creepy neon green)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PureWhite.copy(alpha = if (isCanteenOpen) 0.26f else 0.16f))
                .border(1.dp, PureWhite.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .clickable { onToggleCanteen() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isCanteenOpen) BlackPrimary else Color(0xFFDC2626)),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (isCanteenOpen) "Canteen is Open" else "Canteen is Closed",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = BlackPrimary,
                    )
                    Text(
                        text = if (isCanteenOpen) "Accepting student orders" else closeReason.ifBlank { "Tap to open canteen" },
                        fontSize = 11.5.sp,
                        color = BlackPrimary.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Switch(
                checked = isCanteenOpen,
                onCheckedChange = { onToggleCanteen() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PureWhite,
                    checkedTrackColor = BlackPrimary,
                    uncheckedThumbColor = PureWhite,
                    uncheckedTrackColor = PureWhite.copy(alpha = 0.4f),
                ),
                modifier = Modifier.testTag("canteen_status_drawer_switch"),
            )
        }

        Spacer(Modifier.height(8.dp))

        var showLogoutConfirmation by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            navSections.forEachIndexed { index, section ->
                if (index > 0) {
                    Spacer(Modifier.height(8.dp))
                }

                // Section heading matching student drawer style
                Text(
                    text = section.title.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PureWhite.copy(alpha = 0.9f),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )

                section.items.forEach { item ->
                    DrawerNavItem(
                        item = item,
                        isSelected = item.label == selectedItem,
                        onClick = { onItemClick(item.label) },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            DrawerNavItem(
                item = NavItem("Logout", Icons.AutoMirrored.Outlined.Logout, tag = "owner_logout_button"),
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
    val iconTint = tintOverride ?: if (isSelected) DrawerAmber else BlackPrimary
    val textColor = tintOverride ?: if (isSelected) DrawerAmber else BlackPrimary
    val tagMod = if (item.tag.isNotBlank()) Modifier.testTag(item.tag) else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) PureWhite else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .then(tagMod)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (isSelected) DrawerAmberLight else PureWhite.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Text(
            text = item.label,
            fontSize = 14.5.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
            color = textColor,
            modifier = Modifier.weight(1f),
        )
        if (item.badge > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) DrawerAmber else PureWhite)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${item.badge}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) PureWhite else DrawerAmber,
                )
            }
        } else {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isSelected) DrawerAmber.copy(alpha = 0.7f) else BlackPrimary.copy(alpha = 0.4f),
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

//  MAIN DASHBOARD BODY  (matches Image 2 reference)

@Composable
private fun OwnerDashboardBody(
    state: DashboardUiState.Ready,
    allOrders: List<OrderDocument> = emptyList(),
    onNavigateToManageItems: () -> Unit,
    onNavigateToOrders: () -> Unit,
    onAdvanceOrderStatus: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when {
        hour < 12 -> "Good Morning"
        hour < 17 -> "Good Afternoon"
        else -> "Good Evening"
    }
    val ownerFirstName = state.owner.name.split(" ").firstOrNull().orEmpty().ifBlank { "Owner" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PageBackground)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 96.dp),
    ) {
        Spacer(Modifier.height(18.dp))

        Text(
            text = "$greeting, $ownerFirstName 👋",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(14.dp))

        if (state.metrics.pendingOrdersCount > 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clickable { onNavigateToOrders() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF59E0B)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = PureWhite,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "${state.metrics.pendingOrdersCount} Incoming Order${if (state.metrics.pendingOrdersCount > 1) "s" else ""}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = Color(0xFF92400E),
                            )
                            Text(
                                text = "Action required: Confirm or Reject",
                                fontSize = 12.sp,
                                color = Color(0xFFB45309),
                            )
                        }
                    }
                    Button(
                        onClick = onNavigateToOrders,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(34.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    ) {
                        Text("Review", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PureWhite)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        TodayOverviewCard(
            defaultRevenue = state.metrics.todayRevenue,
            defaultOrdersCount = state.metrics.todayOrdersCount,
            activeOrders = state.metrics.newOrdersCount + state.metrics.preparingOrdersCount,
            readyOrders = state.metrics.readyOrdersCount,
            allOrders = allOrders.ifEmpty { state.recentOrders },
        )

        Spacer(Modifier.height(26.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Menu Items",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(OrangeAccent.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "${state.allItems.size}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = OrangeAccent,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onNavigateToManageItems() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "Manage All",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OrangeAccent,
                )
                Spacer(Modifier.width(3.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = OrangeAccent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        // Low stock warning alert banner if any items are low stock
        if (state.lowStockItems.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFEF3C7))
                    .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(12.dp))
                    .clickable { onNavigateToManageItems() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${state.lowStockItems.size} item(s) are low in stock or unavailable. Tap to update.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF92400E),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (state.allItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBackground)
                    .border(1.dp, Color(0xFFF3F4F6), RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.RestaurantMenu,
                        contentDescription = null,
                        tint = Color(0xFFD1D5DB),
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "No menu items listed yet.",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextDark,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Add delicious food items for campus students to order.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onNavigateToManageItems,
                        colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add First Item", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Show first 6 items
                state.allItems.take(6).forEach { item ->
                    DashboardMenuItemRow(
                        item = item,
                        onClick = onNavigateToManageItems,
                    )
                }

                if (state.allItems.size > 6) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DrawerAmberLight)
                            .border(1.dp, BorderGray, RoundedCornerShape(14.dp))
                            .clickable { onNavigateToManageItems() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "View All ${state.allItems.size} Menu Items →",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = OrangeAccent,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(26.dp))

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

//  TODAY'S OVERVIEW CARD WITH FILTER FOR PREVIOUS DAYS

private enum class DashboardPeriodFilter(val displayName: String, val shortName: String) {
    TODAY("Today's Overview", "Today"),
    YESTERDAY("Yesterday's Earnings", "Yesterday"),
    LAST_7_DAYS("Last 7 Days", "7 Days"),
    LAST_30_DAYS("Last 30 Days", "30 Days"),
    ALL_TIME("All-Time Earnings", "All Time"),
}

private data class FilteredPeriodStats(
    val revenue: Int,
    val totalOrders: Int,
    val completedOrders: Int,
    val label: String,
)

@Composable
private fun TodayOverviewCard(
    defaultRevenue: Int,
    defaultOrdersCount: Int,
    activeOrders: Int,
    readyOrders: Int,
    allOrders: List<OrderDocument> = emptyList(),
) {
    var selectedPeriod by remember { mutableStateOf(DashboardPeriodFilter.TODAY) }

    val stats = remember(selectedPeriod, allOrders, defaultRevenue, defaultOrdersCount) {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfToday = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, -1)
        val startOfYesterday = cal.timeInMillis
        val endOfYesterday = startOfToday - 1

        val matching = when (selectedPeriod) {
            DashboardPeriodFilter.TODAY -> allOrders.filter { it.createdAt >= startOfToday }
            DashboardPeriodFilter.YESTERDAY -> allOrders.filter { it.createdAt in startOfYesterday..endOfYesterday }
            DashboardPeriodFilter.LAST_7_DAYS -> allOrders.filter { it.createdAt >= now - (7L * 24 * 60 * 60 * 1000) }
            DashboardPeriodFilter.LAST_30_DAYS -> allOrders.filter { it.createdAt >= now - (30L * 24 * 60 * 60 * 1000) }
            DashboardPeriodFilter.ALL_TIME -> allOrders
        }

        val rev = if (selectedPeriod == DashboardPeriodFilter.TODAY && matching.isEmpty()) {
            defaultRevenue
        } else {
            matching.sumOf { it.totalAmount }
        }
        val count = if (selectedPeriod == DashboardPeriodFilter.TODAY && matching.isEmpty()) {
            defaultOrdersCount
        } else {
            matching.size
        }
        val completed = matching.count {
            it.status.equals("COMPLETED", ignoreCase = true) ||
            it.status.equals("PICKED_UP", ignoreCase = true) ||
            it.status.equals("DELIVERED", ignoreCase = true)
        }
        val label = selectedPeriod.displayName
        FilteredPeriodStats(rev, count, completed, label)
    }

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
                    text = stats.label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PureWhite.copy(alpha = 0.88f),
                )
                if (selectedPeriod == DashboardPeriodFilter.TODAY) {
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
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(PureWhite.copy(alpha = 0.22f))
                            .clickable { selectedPeriod = DashboardPeriodFilter.TODAY }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(text = "Back to Live ●", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Filter chips selector directly on the Live Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DashboardPeriodFilter.values().forEach { filter ->
                    val isSelected = selectedPeriod == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) PureWhite else PureWhite.copy(alpha = 0.18f))
                            .clickable { selectedPeriod = filter }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = filter.shortName,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isSelected) OrangeAccent else PureWhite,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(text = "₹ ${stats.revenue}", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = PureWhite)
            Text(
                text = if (selectedPeriod == DashboardPeriodFilter.TODAY) "Total Sales (Today)" else "${selectedPeriod.shortName} Total Sales",
                fontSize = 12.sp,
                color = PureWhite.copy(alpha = 0.75f)
            )

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OverviewStat(label = "Orders", value = "${stats.totalOrders}")
                if (selectedPeriod == DashboardPeriodFilter.TODAY) {
                    OverviewStat(label = "Active Orders", value = "$activeOrders")
                    OverviewStat(label = "Ready to Pickup", value = "$readyOrders")
                } else {
                    OverviewStat(label = "Delivered", value = "${stats.completedOrders}")
                    val avg = if (stats.totalOrders > 0) stats.revenue / stats.totalOrders else 0
                    OverviewStat(label = "Avg / Order", value = "₹ $avg")
                }
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

//  DASHBOARD ORDER ROW

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
                .background(DrawerAmberLight),
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
            val fulfillmentLabel = if (order.fulfillmentType.equals("DINE_IN", ignoreCase = true)) "Dine-in" else "Takeaway"
            Text(text = "₹${order.totalAmount} • $fulfillmentLabel", fontSize = 12.sp, color = TextMuted)
        }

        Spacer(Modifier.width(8.dp))

        when (order.status.uppercase().trim()) {
            "PENDING", "NEW", "WAITING" -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFFEF3C7))
                        .clickable { onAdvanceStatus("ACCEPTED") }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Accept Order", color = Color(0xFFD97706), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            "ACCEPTED" -> {
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
                        .clickable { onAdvanceStatus("READY") }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Mark Ready", color = AmberBrand, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                    Text("Picked Up", color = AccentGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

//  DASHBOARD MENU ITEM ROW

@Composable
private fun DashboardMenuItemRow(
    item: ItemDocument,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .border(1.dp, Color(0xFFF3F4F6), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FoodImagePlaceholder(
            itemName = item.name,
            imageUrl = item.imageUrl,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp)),
            compact = true,
        )

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Category badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF3F4F6))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = item.category.replace("_", " "),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF4B5563),
                    )
                }

                Text(
                    text = "⏱ ${item.prepMinutes}m",
                    fontSize = 11.sp,
                    color = TextMuted,
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "₹${item.price}",
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextDark,
            )
            Spacer(Modifier.height(4.dp))
            // Stock badge
            val (badgeText, badgeBg, badgeTextColor) = when {
                !item.available || item.stock == 0 -> Triple("Out of Stock", Color(0xFFFEE2E2), Color(0xFFDC2626))
                item.stock <= 5 -> Triple("Low (${item.stock})", Color(0xFFFEF3C7), Color(0xFFD97706))
                else -> Triple("In Stock (${item.stock})", Color(0xFFDCFCE7), Color(0xFF16A34A))
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(badgeBg)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = badgeText,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeTextColor,
                )
            }
        }
    }
}

//  OWNER BOTTOM NAVIGATION BAR

@Composable
fun OwnerBottomBar(
    currentSection: String,
    onSectionSelected: (String) -> Unit,
    onAddItemClick: () -> Unit,
    onScanQrClick: () -> Unit,
    onOrdersClick: () -> Unit,
    activeOrdersCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(28.dp),
                clip = false,
            ),
        shape = RoundedCornerShape(28.dp),
        color = PureWhite,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OwnerBottomNavCell(
                label = "Add Item",
                icon = Icons.Default.Add,
                isSelected = false,
                onClick = onAddItemClick,
                modifier = Modifier.weight(1f),
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(46.dp)
                    .shadow(4.dp, CircleShape, spotColor = DrawerAmber.copy(alpha = 0.45f))
                    .clip(CircleShape)
                    .background(PureWhite)
                    .border(
                        width = 2.dp,
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                DrawerAmber,
                                Color(0xFFEA580C),
                                Color(0xFFE11D48),
                                Color(0xFF8B5CF6),
                                DrawerAmber,
                            )
                        ),
                        shape = CircleShape,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onScanQrClick,
                    )
                    .semantics { contentDescription = "Scan Customer QR" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = BlackPrimary,
                    modifier = Modifier.size(23.dp),
                )
            }

            OwnerBottomNavCell(
                label = "Orders",
                icon = Icons.Default.ShoppingBag,
                isSelected = false,
                badgeCount = activeOrdersCount,
                onClick = onOrdersClick,
                modifier = Modifier.weight(1f),
            )

            OwnerBottomNavCell(
                label = "Customers",
                icon = Icons.Default.Group,
                isSelected = currentSection == "Customers",
                onClick = { onSectionSelected("Customers") },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun OwnerBottomNavCell(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    badgeCount: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "ownerTabScale_$label",
    )

    val pillAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "ownerTabPillAlpha_$label",
    )

    val iconTint by animateColorAsState(
        targetValue = if (isSelected) DrawerAmber else Color(0xFF8E8E93),
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "ownerTabIconTint_$label",
    )

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        // App theme subtle selected pill indicator
        Box(
            modifier = Modifier
                .size(width = 46.dp, height = 34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(DrawerAmberLight.copy(alpha = pillAlpha)),
        )

        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier
                .size(24.dp)
                .scale(iconScale),
        )

        // Live badge for Orders
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = 12.dp, y = (-10).dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(DrawerAmber),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                    color = PureWhite,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
