package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.firebase.InventoryItem
import com.example.data.firebase.InventoryRepository
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private val OrangeAccent = Color(0xFFF97316)
private val StockGreen = Color(0xFF16A34A)
private val LowStockOrange = Color(0xFFF97316)
private val OutOfStockRed = Color(0xFFEF4444)
private val DividerColor = Color(0xFFF3F4F6)

@Composable
fun InventoryScreen(
  canteenId: String,
  onBack: () -> Unit,
  repository: InventoryRepository = remember { InventoryRepository() },
  modifier: Modifier = Modifier,
) {
  val coroutineScope = rememberCoroutineScope()
  val inventoryItemsFlow = remember(canteenId) { repository.observeInventory(canteenId) }
  val items by inventoryItemsFlow.collectAsState(initial = repository.defaultInventory)

  var showAddDialog by remember { mutableStateOf(false) }
  var itemToEdit by remember { mutableStateOf<InventoryItem?>(null) }
  var itemToDelete by remember { mutableStateOf<InventoryItem?>(null) }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(PureWhite)
      .statusBarsPadding()
      .navigationBarsPadding()
      .testTag("inventory_screen"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(bottom = 88.dp), // Space for bottom button
    ) {
      // ── Top Bar (matches Image 2) ─────────────────────────────────────────
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.testTag("inventory_back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = BlackPrimary,
            modifier = Modifier.size(24.dp),
          )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
          text = "Inventory",
          fontSize = 22.sp,
          fontWeight = FontWeight.Bold,
          color = TextDark,
        )
      }

      Spacer(modifier = Modifier.height(6.dp))

      // ── Inventory List ──────────────────────────────────────────────────
      if (items.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📦", fontSize = 40.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = "No inventory items added yet",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = TextDark,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Tap '+ Add Inventory Item' below to record stock.",
              fontSize = 13.sp,
              color = TextMuted,
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
        ) {
          items(items, key = { it.id.ifBlank { it.name } }) { item ->
            InventoryItemRow(
              item = item,
              onClick = { itemToEdit = item },
            )
            HorizontalDivider(
              thickness = 0.8.dp,
              color = DividerColor,
              modifier = Modifier.padding(horizontal = 20.dp),
            )
          }
        }
      }
    }

    // ── Bottom Fixed Button: + Add Inventory Item (matches Image 2) ─────────
    Box(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .background(PureWhite)
        .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
      Button(
        onClick = { showAddDialog = true },
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("add_inventory_item_button"),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
      ) {
        Icon(
          imageVector = Icons.Default.Add,
          contentDescription = null,
          tint = PureWhite,
          modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Add Inventory Item",
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          color = PureWhite,
        )
      }
    }
  }

  // ── ADD / EDIT INVENTORY ITEM DIALOG ──────────────────────────────────────
  if (showAddDialog || itemToEdit != null) {
    val isEdit = itemToEdit != null
    val initial = itemToEdit ?: InventoryItem()

    var name by remember { mutableStateOf(initial.name) }
    var quantityText by remember { mutableStateOf(if (initial.quantity > 0) initial.quantity.toString() else "") }
    var unit by remember { mutableStateOf(initial.unit) }
    var thresholdText by remember { mutableStateOf(initial.lowStockThreshold.toString()) }
    var icon by remember { mutableStateOf(initial.icon) }

    val commonIcons = listOf("🍞", "🧈", "🍅", "🥔", "🧅", "🫘", "🍃", "🥛", "🧀", "🍚", "🥚", "🫒", "📦")
    val units = listOf("pcs", "kg", "g", "l")

    AlertDialog(
      onDismissRequest = {
        showAddDialog = false
        itemToEdit = null
      },
      title = {
        Text(
          text = if (isEdit) "Update Inventory Item" else "Add Inventory Item",
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
        )
      },
      text = {
        Column(modifier = Modifier.fillMaxWidth()) {
          // Icon Picker Row
          Text(text = "Select Icon", fontSize = 12.sp, color = TextMuted)
          Spacer(modifier = Modifier.height(4.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            commonIcons.take(7).forEach { ic ->
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(if (icon == ic) OrangeAccent.copy(alpha = 0.15f) else Color(0xFFF3F4F6))
                  .clickable { icon = ic },
                contentAlignment = Alignment.Center,
              ) {
                Text(text = ic, fontSize = 18.sp)
              }
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          // Item Name
          OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Item Name (e.g. Bread, Paneer)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = OrangeAccent,
              focusedLabelColor = OrangeAccent,
            ),
          )

          Spacer(modifier = Modifier.height(10.dp))

          // Quantity & Unit
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedTextField(
              value = quantityText,
              onValueChange = { quantityText = it },
              label = { Text("Quantity") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              singleLine = true,
              modifier = Modifier.weight(1f),
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = OrangeAccent,
                focusedLabelColor = OrangeAccent,
              ),
            )

            // Unit chips
            Column(modifier = Modifier.width(110.dp)) {
              Text(text = "Unit", fontSize = 11.sp, color = TextMuted)
              Spacer(modifier = Modifier.height(2.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                units.forEach { u ->
                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(6.dp))
                      .background(if (unit == u) OrangeAccent else Color(0xFFF3F4F6))
                      .clickable { unit = u }
                      .padding(horizontal = 6.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                  ) {
                    Text(
                      text = u,
                      fontSize = 11.sp,
                      fontWeight = if (unit == u) FontWeight.Bold else FontWeight.Normal,
                      color = if (unit == u) PureWhite else TextDark,
                    )
                  }
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Low Stock Threshold
          OutlinedTextField(
            value = thresholdText,
            onValueChange = { thresholdText = it },
            label = { Text("Low Stock Alert Threshold") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = OrangeAccent,
              focusedLabelColor = OrangeAccent,
            ),
          )

          if (isEdit) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  itemToDelete = initial
                  showAddDialog = false
                  itemToEdit = null
                },
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = OutOfStockRed,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(text = "Delete this inventory item", color = OutOfStockRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val q = quantityText.toDoubleOrNull() ?: 0.0
            val t = thresholdText.toDoubleOrNull() ?: 5.0
            if (name.isNotBlank()) {
              coroutineScope.launch {
                if (isEdit) {
                  repository.updateInventoryItem(
                    canteenId = canteenId,
                    item = initial.copy(name = name, quantity = q, unit = unit, lowStockThreshold = t, icon = icon),
                  )
                } else {
                  repository.addInventoryItem(
                    canteenId = canteenId,
                    item = InventoryItem(name = name, quantity = q, unit = unit, lowStockThreshold = t, icon = icon),
                  )
                }
              }
              showAddDialog = false
              itemToEdit = null
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
        ) {
          Text("Save", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = {
          showAddDialog = false
          itemToEdit = null
        }) {
          Text("Cancel", color = TextMuted)
        }
      },
      containerColor = PureWhite,
      shape = RoundedCornerShape(20.dp),
    )
  }

  // ── DELETE CONFIRMATION DIALOG ────────────────────────────────────────────
  if (itemToDelete != null) {
    val target = itemToDelete!!
    AlertDialog(
      onDismissRequest = { itemToDelete = null },
      title = { Text("Delete '${target.name}'?", fontWeight = FontWeight.Bold) },
      text = { Text("Are you sure you want to remove this item from your canteen inventory?") },
      confirmButton = {
        TextButton(
          onClick = {
            coroutineScope.launch {
              repository.deleteInventoryItem(canteenId, target.id)
              itemToDelete = null
            }
          },
          colors = ButtonDefaults.textButtonColors(contentColor = OutOfStockRed),
        ) {
          Text("Delete", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { itemToDelete = null }) {
          Text("Cancel", color = TextMuted)
        }
      },
      containerColor = PureWhite,
      shape = RoundedCornerShape(20.dp),
    )
  }
}

// ── ROW ITEM MATCHING IMAGE 2 EXACTLY ────────────────────────────────────────

@Composable
private fun InventoryItemRow(
  item: InventoryItem,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(role = Role.Button, onClick = onClick)
      .padding(horizontal = 20.dp, vertical = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // 3D/realistic ingredient icon badge (matches Image 2)
    Box(
      modifier = Modifier
        .size(44.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(Color(0xFFF9FAFB)),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = item.icon,
        fontSize = 24.sp,
      )
    }

    Spacer(modifier = Modifier.width(16.dp))

    // Ingredient Name
    Text(
      text = item.name,
      fontSize = 15.5.sp,
      fontWeight = FontWeight.SemiBold,
      color = TextDark,
      modifier = Modifier.weight(1f),
    )

    // Quantity (e.g. 120 pcs, 2.5 kg, 800 g)
    Text(
      text = item.formattedQuantity,
      fontSize = 14.5.sp,
      color = Color(0xFF4B5563),
      textAlign = TextAlign.End,
      modifier = Modifier.padding(end = 24.dp),
    )

    // Stock Status (In Stock = Green, Low Stock = Orange, Out of Stock = Red)
    val statusColor = when (item.status) {
      "In Stock" -> StockGreen
      "Low Stock" -> LowStockOrange
      else -> OutOfStockRed
    }

    Text(
      text = item.status,
      fontSize = 14.sp,
      fontWeight = FontWeight.Medium,
      color = statusColor,
    )
  }
}
