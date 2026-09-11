package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.firebase.ItemDocument
import com.example.ui.components.FoodImagePlaceholder
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.viewmodel.ItemActionState
import com.example.ui.viewmodel.ItemViewModel

private val OrangeAccent = Color(0xFFF97316)
private val VegGreen = Color(0xFF16A34A)
private val NonVegRed = Color(0xFFB91C1C)
private val CardBorderColor = Color(0xFFF3F4F6)
private val InactiveFilterBg = Color(0xFFF8F9FA)
private val InactiveFilterBorder = Color(0xFFE5E7EB)

// Categories matching Image 1 exactly: All, Meals, Snacks, Beverages, Combos
private val MenuCategories = listOf("All", "Meals", "Snacks", "Beverages", "Combos")

@Composable
fun ManageItemsScreen(
  canteenId: String,
  onBack: () -> Unit,
  viewModel: ItemViewModel = viewModel(),
  modifier: Modifier = Modifier,
) {
  LaunchedEffect(canteenId) {
    if (canteenId.isNotBlank()) {
      viewModel.setCanteenId(canteenId)
    }
  }

  val allItems by viewModel.allItems.collectAsState()
  val actionState by viewModel.actionState.collectAsState()

  var activeCategory by remember { mutableStateOf("All") }
  var showAddEditDialog by remember { mutableStateOf(false) }
  var itemToEdit by remember { mutableStateOf<ItemDocument?>(null) }
  var itemToDelete by remember { mutableStateOf<ItemDocument?>(null) }
  var itemForQuickPrep by remember { mutableStateOf<ItemDocument?>(null) }
  var itemForQuickStock by remember { mutableStateOf<ItemDocument?>(null) }

  val displayItems = remember(allItems, activeCategory) {
    val sourceList = allItems

    when (activeCategory) {
      "All" -> sourceList
      "Meals" -> sourceList.filter { it.category.equals("MEALS", ignoreCase = true) || it.name.contains("Dosa", true) || it.name.contains("Roll", true) || it.name.contains("Thali", true) || it.name.contains("Rice", true) }
      "Snacks" -> sourceList.filter { it.category.equals("SNACKS", ignoreCase = true) || it.category.equals("QUICK_ORDER", ignoreCase = true) || it.name.contains("Sandwich", true) || it.name.contains("Samosa", true) || it.name.contains("Puff", true) }
      "Beverages" -> sourceList.filter { it.category.equals("BEVERAGES", ignoreCase = true) || it.name.contains("Coffee", true) || it.name.contains("Tea", true) || it.name.contains("Juice", true) || it.name.contains("Shake", true) }
      "Combos" -> sourceList.filter { it.category.equals("COMBOS", ignoreCase = true) || it.category.equals("POPULAR", ignoreCase = true) || it.name.contains("Combo", true) || it.name.contains("Special", true) }
      else -> sourceList
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(PureWhite)
      .statusBarsPadding()
      .navigationBarsPadding()
      .testTag("manage_items_screen"),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // ── Top Header matching Image 1 ───────────────────────────────────────
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("manage_items_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = BlackPrimary,
              modifier = Modifier.size(24.dp),
            )
          }

          Spacer(modifier = Modifier.width(8.dp))

          Text(
            text = "Menu Management",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
          )
        }

        // "+ Add Item" Button (Orange pill button matching Image 1)
        Button(
          onClick = {
            itemToEdit = null
            showAddEditDialog = true
          },
          shape = RoundedCornerShape(20.dp),
          colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
          modifier = Modifier.testTag("top_add_item_button"),
        ) {
          Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = PureWhite,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "Add Item",
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = PureWhite,
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      // ── Category Filter Chips matching Image 1 (All, Meals, Snacks, Beverages, Combos) ─
      LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
      ) {
        items(MenuCategories) { category ->
          val isSelected = activeCategory.equals(category, ignoreCase = true)
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(18.dp))
              .background(if (isSelected) OrangeAccent else InactiveFilterBg)
              .border(1.dp, if (isSelected) OrangeAccent else InactiveFilterBorder, RoundedCornerShape(18.dp))
              .clickable { activeCategory = category }
              .padding(horizontal = 18.dp, vertical = 8.dp)
              .testTag("category_tab_$category"),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = category,
              fontSize = 13.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isSelected) PureWhite else TextDark,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // ── Action Error Feedback ─────────────────────────────────────────────
      if (actionState is ItemActionState.Error) {
        val err = (actionState as ItemActionState.Error).error
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFFF7ED))
            .border(1.dp, Color(0xFFFED7AA), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = Color(0xFFC2410C),
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = err,
            color = Color(0xFF9A3412),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
          )
        }
      }

      // ── Item Cards List matching Image 1 ──────────────────────────────────
      if (displayItems.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(32.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              imageVector = Icons.Default.Fastfood,
              contentDescription = null,
              tint = Color(0xFFD1D5DB),
              modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = "No items in $activeCategory",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = TextDark,
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, bottom = 24.dp
          ),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(displayItems, key = { it.id.ifBlank { it.name } }) { item ->
            MenuItemCard(
              item = item,
              onToggleAvailable = { isAvailable ->
                viewModel.toggleAvailability(item, isAvailable)
              },
              onEdit = {
                itemToEdit = item
                showAddEditDialog = true
              },
              onDuplicate = {
                viewModel.saveItem(
                  itemId = "",
                  name = "${item.name} (Copy)",
                  description = item.description,
                  price = item.price,
                  stock = item.stock,
                  prepMinutes = item.prepMinutes,
                  category = item.category,
                  imageUrl = item.imageUrl,
                  imageUri = null,
                  ingredients = item.ingredients,
                  customizationTitle = item.customizationTitle,
                  customizationOptions = item.customizationOptions,
                  addons = item.addons,
                  onSuccess = {},
                )
              },
              onSetPrepTime = { itemForQuickPrep = item },
              onUpdateStock = { itemForQuickStock = item },
              onDelete = { itemToDelete = item },
            )
          }
        }
      }
    }
  }

  // ── QUICK PREP TIME DIALOG ───────────────────────────────────────────────
  if (itemForQuickPrep != null) {
    val target = itemForQuickPrep!!
    val prepOptions = listOf(5, 7, 10, 12, 15, 20)
    AlertDialog(
      onDismissRequest = { itemForQuickPrep = null },
      title = { Text("Set Prep Time for ${target.name}", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
      text = {
        Column {
          Text("Select estimated cooking time:", fontSize = 13.sp, color = TextMuted)
          Spacer(modifier = Modifier.height(12.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            prepOptions.forEach { mins ->
              val isCurrent = target.prepMinutes == mins
              Box(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(8.dp))
                  .background(if (isCurrent) OrangeAccent else Color(0xFFF3F4F6))
                  .clickable {
                    viewModel.saveItem(
                      itemId = target.id,
                      name = target.name,
                      description = target.description,
                      price = target.price,
                      stock = target.stock,
                      prepMinutes = mins,
                      category = target.category,
                      imageUrl = target.imageUrl,
                      imageUri = null,
                      ingredients = target.ingredients,
                      customizationTitle = target.customizationTitle,
                      customizationOptions = target.customizationOptions,
                      addons = target.addons,
                      onSuccess = { itemForQuickPrep = null },
                    )
                  }
                  .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = "${mins}m",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (isCurrent) PureWhite else TextDark,
                )
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { itemForQuickPrep = null }) {
          Text("Done", color = OrangeAccent, fontWeight = FontWeight.Bold)
        }
      },
      containerColor = PureWhite,
      shape = RoundedCornerShape(20.dp),
    )
  }

  // ── QUICK STOCK DIALOG ───────────────────────────────────────────────────
  if (itemForQuickStock != null) {
    val target = itemForQuickStock!!
    var stockCount by remember { mutableStateOf(target.stock.toString()) }
    AlertDialog(
      onDismissRequest = { itemForQuickStock = null },
      title = { Text("Update Stock for ${target.name}", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
      text = {
        Column {
          Text("Enter available portions count:", fontSize = 13.sp, color = TextMuted)
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = stockCount,
            onValueChange = { stockCount = it },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = OrangeAccent,
              focusedLabelColor = OrangeAccent,
            ),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val count = stockCount.toIntOrNull() ?: target.stock
            viewModel.updateStock(target, count)
            itemForQuickStock = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
        ) {
          Text("Update", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { itemForQuickStock = null }) {
          Text("Cancel", color = TextMuted)
        }
      },
      containerColor = PureWhite,
      shape = RoundedCornerShape(20.dp),
    )
  }

  // ── ADD / EDIT ITEM DIALOG ──────────────────────────────────────────────
  if (showAddEditDialog) {
    AddEditItemDialog(
      item = itemToEdit,
      onDismiss = { showAddEditDialog = false },
      onSave = { name, desc, price, stock, prep, cat, uri, ingredients, customTitle, customOptions, addons ->
        viewModel.saveItem(
          itemId = itemToEdit?.id ?: "",
          name = name,
          description = desc,
          price = price,
          stock = stock,
          prepMinutes = prep,
          category = cat,
          imageUrl = itemToEdit?.imageUrl ?: "",
          imageUri = uri,
          ingredients = ingredients,
          customizationTitle = customTitle,
          customizationOptions = customOptions,
          addons = addons,
          onSuccess = { showAddEditDialog = false },
        )
      },
    )
  }

  // ── DELETE ITEM CONFIRMATION DIALOG ──────────────────────────────────────
  if (itemToDelete != null) {
    val target = itemToDelete!!
    AlertDialog(
      onDismissRequest = { itemToDelete = null },
      title = { Text("Delete Item?", fontWeight = FontWeight.Bold) },
      text = { Text("Are you sure you want to delete '${target.name}' from your menu?") },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.deleteItem(target) { itemToDelete = null }
          },
          colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626)),
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

// ── MENU ITEM CARD (MATCHES IMAGE 1 PRECISELY) ──────────────────────────────

@Composable
private fun MenuItemCard(
  item: ItemDocument,
  onToggleAvailable: (Boolean) -> Unit,
  onEdit: () -> Unit,
  onDuplicate: () -> Unit,
  onSetPrepTime: () -> Unit,
  onUpdateStock: () -> Unit,
  onDelete: () -> Unit,
) {
  var menuExpanded by remember { mutableStateOf(false) }

  val isVeg = !item.name.contains("Chicken", ignoreCase = true) &&
      !item.name.contains("Egg", ignoreCase = true) &&
      !item.name.contains("Fish", ignoreCase = true) &&
      !item.name.contains("Meat", ignoreCase = true)

  val isBeverage = item.category.equals("BEVERAGES", ignoreCase = true) ||
      item.name.contains("Coffee", ignoreCase = true) ||
      item.name.contains("Tea", ignoreCase = true)

  val prepTimeDisplay = if (item.preparationTime.isNotBlank()) {
    item.preparationTime
  } else {
    "${item.prepMinutes - 2}-${item.prepMinutes} min".replace("0-", "3-").replace("-1-", "3-")
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("item_card_${item.id}"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = PureWhite),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    border = androidx.compose.foundation.BorderStroke(0.8.dp, CardBorderColor),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // 1. Food Thumbnail Image matching user-side FoodCards
      FoodImagePlaceholder(
        itemName = item.name,
        imageUrl = item.imageUrl,
        compact = true,
        modifier = Modifier.size(76.dp),
      )

      Spacer(modifier = Modifier.width(14.dp))

      // 2. Center Content: Name, Price, Veg Badge + Prep Time
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = item.name,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          color = TextDark,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = "₹${item.price}",
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          color = TextDark,
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Badge Row: Veg / Non-Veg + Clock Prep Time
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (isBeverage) {
            Text(
              text = "Beverage",
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Medium,
              color = Color(0xFF4B5563),
            )
          } else {
            // Veg / Non-Veg badge symbol
            Box(
              modifier = Modifier
                .size(13.dp)
                .border(1.2.dp, if (isVeg) VegGreen else NonVegRed, RoundedCornerShape(2.dp)),
              contentAlignment = Alignment.Center,
            ) {
              Box(
                modifier = Modifier
                  .size(6.dp)
                  .clip(CircleShape)
                  .background(if (isVeg) VegGreen else NonVegRed),
              )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = if (isVeg) "Veg" else "Non-Veg",
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Medium,
              color = Color(0xFF4B5563),
            )
          }

          Spacer(modifier = Modifier.width(12.dp))

          // Clock icon + prep time (matches Image 1: 🕒 5-7 min)
          Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = null,
            tint = Color(0xFF6B7280),
            modifier = Modifier.size(13.dp),
          )
          Spacer(modifier = Modifier.width(3.dp))
          Text(
            text = prepTimeDisplay,
            fontSize = 11.5.sp,
            color = Color(0xFF6B7280),
          )
        }
      }

      Spacer(modifier = Modifier.width(8.dp))

      // 3. Right: Orange Switch + 3-dots Menu Button
      Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(
          checked = item.available,
          onCheckedChange = onToggleAvailable,
          colors = SwitchDefaults.colors(
            checkedThumbColor = PureWhite,
            checkedTrackColor = OrangeAccent,
            uncheckedThumbColor = PureWhite,
            uncheckedTrackColor = Color(0xFFD1D5DB),
          ),
          modifier = Modifier.testTag("toggle_available_${item.id}"),
        )

        Box {
          IconButton(
            onClick = { menuExpanded = true },
            modifier = Modifier.testTag("more_options_${item.id}"),
          ) {
            Icon(
              imageVector = Icons.Default.MoreVert,
              contentDescription = "More features",
              tint = Color(0xFF6B7280),
              modifier = Modifier.size(22.dp),
            )
          }

          // ── 3-DOTS FEATURES DROPDOWN ─────────────────────────────────────
          DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.background(PureWhite),
          ) {
            DropdownMenuItem(
              text = { Text("Edit Item", fontSize = 13.5.sp) },
              leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = TextDark, modifier = Modifier.size(18.dp)) },
              onClick = {
                menuExpanded = false
                onEdit()
              },
            )
            DropdownMenuItem(
              text = { Text("Duplicate Item", fontSize = 13.5.sp) },
              leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextDark, modifier = Modifier.size(18.dp)) },
              onClick = {
                menuExpanded = false
                onDuplicate()
              },
            )
            DropdownMenuItem(
              text = { Text("Set Prep Time", fontSize = 13.5.sp) },
              leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, tint = OrangeAccent, modifier = Modifier.size(18.dp)) },
              onClick = {
                menuExpanded = false
                onSetPrepTime()
              },
            )
            DropdownMenuItem(
              text = { Text("Update Stock Count", fontSize = 13.5.sp) },
              leadingIcon = { Icon(Icons.Default.Inventory, contentDescription = null, tint = OrangeAccent, modifier = Modifier.size(18.dp)) },
              onClick = {
                menuExpanded = false
                onUpdateStock()
              },
            )
            HorizontalDivider(thickness = 0.5.dp, color = CardBorderColor)
            DropdownMenuItem(
              text = { Text("Delete Item", fontSize = 13.5.sp, color = Color(0xFFDC2626)) },
              leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp)) },
              onClick = {
                menuExpanded = false
                onDelete()
              },
            )
          }
        }
      }
    }
  }
}

// ── ADD / EDIT ITEM DIALOG ──────────────────────────────────────────────────

private data class EditableAddon(
  val id: Long = System.currentTimeMillis() + (0..100000).random(),
  val name: String = "",
  val priceStr: String = "",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditItemDialog(
  item: ItemDocument?,
  onDismiss: () -> Unit,
  onSave: (
    name: String,
    description: String,
    price: Int,
    stock: Int,
    prepMinutes: Int,
    category: String,
    imageUri: Uri?,
    ingredients: List<String>,
    customizationTitle: String,
    customizationOptions: List<String>,
    addons: List<com.example.data.firebase.AddonDocument>,
  ) -> Unit,
) {
  var name by remember { mutableStateOf(item?.name ?: "") }
  var description by remember { mutableStateOf(item?.description ?: "") }
  var priceStr by remember { mutableStateOf(item?.price?.toString() ?: "") }
  var stockStr by remember { mutableStateOf(item?.stock?.toString() ?: "50") }
  var prepStr by remember { mutableStateOf(item?.prepMinutes?.toString() ?: "7") }
  var category by remember { mutableStateOf(item?.category ?: "MEALS") }
  var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var categoryExpanded by remember { mutableStateOf(false) }

  // Ingredients, Customization & Add-ons state
  var ingredientsStr by remember { mutableStateOf(item?.ingredients?.joinToString(", ") ?: "") }
  var customizationTitle by remember { mutableStateOf(item?.customizationTitle ?: "") }
  var customizationOptionsStr by remember { mutableStateOf(item?.customizationOptions?.joinToString(", ") ?: "") }

  // Dynamic Add-ons list
  val addonsList = remember {
    mutableStateListOf<EditableAddon>().apply {
      if (item != null && item.addons.isNotEmpty()) {
        item.addons.forEach { add(EditableAddon(name = it.name, priceStr = it.price.toString())) }
      }
    }
  }

  val imagePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri ->
    selectedImageUri = uri
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth(0.94f)
        .imePadding()
        .clip(RoundedCornerShape(24.dp)),
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = PureWhite),
    ) {
      Column(
        modifier = Modifier
          .padding(22.dp)
          .verticalScroll(rememberScrollState()),
      ) {
        Text(
          text = if (item == null) "Add Menu Item" else "Edit Item",
          fontSize = 19.sp,
          fontWeight = FontWeight.Bold,
          color = TextDark,
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Image Preview / Upload
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier = Modifier
              .size(68.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(Color(0xFFF3ECE4))
              .border(1.dp, BorderGray.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
              .clickable { imagePickerLauncher.launch("image/*") },
            contentAlignment = Alignment.Center,
          ) {
            val previewSource = selectedImageUri ?: item?.imageUrl?.ifBlank { null }
            if (previewSource != null) {
              coil.compose.SubcomposeAsyncImage(
                model = previewSource,
                contentDescription = "Selected Item Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                loading = {
                  com.example.ui.components.ShimmerPlaceholder(modifier = Modifier.fillMaxSize())
                },
              )
            } else {
              Icon(
                imageVector = Icons.Default.AddPhotoAlternate,
                contentDescription = "Upload Image",
                tint = OrangeAccent,
                modifier = Modifier.size(28.dp),
              )
            }
          }

          Spacer(modifier = Modifier.width(14.dp))

          Column {
            Text(
              text = if (selectedImageUri != null) "Photo Selected" else "No Photo Selected",
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold,
              color = TextDark,
            )
            TextButton(onClick = { imagePickerLauncher.launch("image/*") }) {
              Text("Choose Photo", fontSize = 12.5.sp, color = OrangeAccent, fontWeight = FontWeight.Bold)
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Name
        OutlinedTextField(
          value = name,
          onValueChange = { name = it; errorMessage = null },
          label = { Text("Item Name *") },
          placeholder = { Text("e.g. Veg Sandwich") },
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = OrangeAccent, focusedLabelColor = OrangeAccent),
          modifier = Modifier.fillMaxWidth().testTag("item_name_input"),
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Description
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("Description") },
          placeholder = { Text("Fresh vegetables with mint chutney") },
          maxLines = 2,
          colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = OrangeAccent, focusedLabelColor = OrangeAccent),
          modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Price & Stock
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedTextField(
            value = priceStr,
            onValueChange = { priceStr = it; errorMessage = null },
            label = { Text("Price (₹) *") },
            placeholder = { Text("60") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = OrangeAccent, focusedLabelColor = OrangeAccent),
            modifier = Modifier.weight(1f).testTag("item_price_input"),
          )

          OutlinedTextField(
            value = stockStr,
            onValueChange = { stockStr = it; errorMessage = null },
            label = { Text("Stock Qty *") },
            placeholder = { Text("50") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = OrangeAccent, focusedLabelColor = OrangeAccent),
            modifier = Modifier.weight(1f).testTag("item_stock_input"),
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Prep Time & Category
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedTextField(
            value = prepStr,
            onValueChange = { prepStr = it; errorMessage = null },
            label = { Text("Prep Time (min) *") },
            placeholder = { Text("7") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = OrangeAccent, focusedLabelColor = OrangeAccent),
            modifier = Modifier.weight(1f).testTag("item_prep_input"),
          )

          ExposedDropdownMenuBox(
            expanded = categoryExpanded,
            onExpandedChange = { categoryExpanded = !categoryExpanded },
            modifier = Modifier.weight(1.3f),
          ) {
            OutlinedTextField(
              value = category,
              onValueChange = {},
              readOnly = true,
              label = { Text("Category") },
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
              colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = OrangeAccent, focusedLabelColor = OrangeAccent),
              modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
              expanded = categoryExpanded,
              onDismissRequest = { categoryExpanded = false },
            ) {
              listOf("MEALS", "SNACKS", "BEVERAGES", "COMBOS", "BREAKFAST", "HEALTHY BREAKFAST", "LUNCH", "QUICK_ORDER", "POPULAR").forEach { cat ->
                DropdownMenuItem(
                  text = { Text(cat) },
                  onClick = {
                    category = cat
                    categoryExpanded = false
                  }
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(thickness = 0.5.dp, color = CardBorderColor)
        Spacer(modifier = Modifier.height(12.dp))

        // ── 7. INGREDIENTS SECTION ──────────────────────────────────────────
        Text(
          text = "Ingredients",
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          color = TextDark,
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
          value = ingredientsStr,
          onValueChange = { ingredientsStr = it },
          label = { Text("Ingredients (comma separated)") },
          placeholder = { Text("e.g. Potato, Onion, Coriander, Spices") },
          maxLines = 3,
          colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = OrangeAccent, focusedLabelColor = OrangeAccent),
          modifier = Modifier.fillMaxWidth().testTag("item_ingredients_input"),
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(thickness = 0.5.dp, color = CardBorderColor)
        Spacer(modifier = Modifier.height(12.dp))

        // ── 8. CUSTOMIZATION SECTION ────────────────────────────────────────
        Text(
          text = "Customization Options",
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          color = TextDark,
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
          value = customizationTitle,
          onValueChange = { customizationTitle = it },
          label = { Text("Customization Title") },
          placeholder = { Text("e.g. Bread Type, Spice Level, Size") },
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = OrangeAccent, focusedLabelColor = OrangeAccent),
          modifier = Modifier.fillMaxWidth().testTag("item_custom_title_input"),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
          value = customizationOptionsStr,
          onValueChange = { customizationOptionsStr = it },
          label = { Text("Options (comma separated)") },
          placeholder = { Text("e.g. White Bread, Brown Bread, Multigrain") },
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = OrangeAccent, focusedLabelColor = OrangeAccent),
          modifier = Modifier.fillMaxWidth().testTag("item_custom_options_input"),
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(thickness = 0.5.dp, color = CardBorderColor)
        Spacer(modifier = Modifier.height(12.dp))

        // ── 9. ADD-ONS SECTION ──────────────────────────────────────────────
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Add-ons",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
          )
          TextButton(
            onClick = { addonsList.add(EditableAddon()) },
            modifier = Modifier.testTag("add_addon_button"),
          ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = OrangeAccent, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("+ Add Add-on", color = OrangeAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
          }
        }

        if (addonsList.isEmpty()) {
          Text(
            text = "No add-ons created. Tap '+ Add Add-on' to add one.",
            fontSize = 12.sp,
            color = TextMuted,
            modifier = Modifier.padding(vertical = 4.dp),
          )
        } else {
          for (index in addonsList.indices) {
            val addon = addonsList[index]
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              OutlinedTextField(
                value = addon.name,
                onValueChange = { newName ->
                  addonsList[index] = addon.copy(name = newName)
                },
                label = { Text("Add-on Name") },
                placeholder = { Text("e.g. Extra Cheese") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = OrangeAccent, focusedLabelColor = OrangeAccent),
                modifier = Modifier.weight(1.5f),
              )

              OutlinedTextField(
                value = addon.priceStr,
                onValueChange = { newPrice ->
                  addonsList[index] = addon.copy(priceStr = newPrice)
                },
                label = { Text("Price (₹)") },
                placeholder = { Text("15") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = OrangeAccent, focusedLabelColor = OrangeAccent),
                modifier = Modifier.weight(1f),
              )

              IconButton(
                onClick = { addonsList.removeAt(index) },
                modifier = Modifier.size(36.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Delete,
                  contentDescription = "Remove Addon",
                  tint = Color(0xFFDC2626),
                  modifier = Modifier.size(20.dp),
                )
              }
            }
          }
        }

        if (errorMessage != null) {
          Spacer(modifier = Modifier.height(12.dp))
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(Color(0xFFFFF7ED))
              .border(1.dp, Color(0xFFFED7AA), RoundedCornerShape(12.dp))
              .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.Default.ErrorOutline,
              contentDescription = null,
              tint = Color(0xFFC2410C),
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = errorMessage ?: "",
              color = Color(0xFF9A3412),
              fontSize = 12.5.sp,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.weight(1f),
            )
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action Buttons
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(onClick = onDismiss) {
            Text("Cancel", color = TextMuted)
          }
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = {
              val p = priceStr.toIntOrNull() ?: 0
              val s = stockStr.toIntOrNull() ?: 0
              val prep = prepStr.toIntOrNull() ?: 0

              if (name.trim().isBlank()) {
                errorMessage = "Please enter an item name."
                return@Button
              }
              if (p <= 0) {
                errorMessage = "Price must be greater than ₹0."
                return@Button
              }
              if (s < 0) {
                errorMessage = "Stock cannot be negative."
                return@Button
              }
              if (prep <= 0) {
                errorMessage = "Preparation time must be at least 1 minute."
                return@Button
              }

              val parsedIngredients = ingredientsStr
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }

              val parsedOptions = customizationOptionsStr
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }

              val parsedAddons = addonsList.mapNotNull { addon ->
                val trimmedName = addon.name.trim()
                if (trimmedName.isNotBlank()) {
                  val priceVal = addon.priceStr.toIntOrNull() ?: 0
                  com.example.data.firebase.AddonDocument(name = trimmedName, price = priceVal)
                } else {
                  null
                }
              }

              onSave(
                name.trim(),
                description.trim(),
                p,
                s,
                prep,
                category,
                selectedImageUri,
                parsedIngredients,
                customizationTitle.trim(),
                parsedOptions,
                parsedAddons,
              )
            },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
            modifier = Modifier.testTag("save_item_button"),
          ) {
            Text("Save Item", color = PureWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
          }
        }
      }
    }
  }
}
