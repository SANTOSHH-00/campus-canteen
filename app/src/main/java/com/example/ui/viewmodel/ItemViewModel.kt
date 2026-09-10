package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.ItemDocument
import com.example.data.firebase.ItemRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed interface ItemActionState {
  object Idle : ItemActionState
  object Loading : ItemActionState
  data class Success(val message: String) : ItemActionState
  data class Error(val error: String) : ItemActionState
}

class ItemViewModel(
  private val repository: ItemRepository = ItemRepository(),
  private val canteenId: String = "",
) : ViewModel() {

  private val _allItems = MutableStateFlow<List<ItemDocument>>(emptyList())
  val allItems: StateFlow<List<ItemDocument>> = _allItems.asStateFlow()
  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _selectedCategory = MutableStateFlow("ALL")
  val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

  private val _filteredItems = MutableStateFlow<List<ItemDocument>>(emptyList())
  val filteredItems: StateFlow<List<ItemDocument>> = _filteredItems.asStateFlow()

  private val _actionState = MutableStateFlow<ItemActionState>(ItemActionState.Idle)
  val actionState: StateFlow<ItemActionState> = _actionState.asStateFlow()

  private var activeCanteenId: String = canteenId

  init {
    if (activeCanteenId.isNotBlank()) {
      startObserving(activeCanteenId)
    }

    viewModelScope.launch {
      combine(_allItems, _searchQuery, _selectedCategory) { items, query, category ->
        items.filter { item ->
          val matchesQuery = query.isBlank() ||
            item.name.contains(query, ignoreCase = true) ||
            item.description.contains(query, ignoreCase = true)
          val matchesCategory = category == "ALL" || item.category.equals(category, ignoreCase = true)
          matchesQuery && matchesCategory
        }
      }.collect {
        _filteredItems.value = it
      }
    }
  }

  fun setCanteenId(newCanteenId: String) {
    if (activeCanteenId != newCanteenId) {
      activeCanteenId = newCanteenId
      startObserving(newCanteenId)
    }
  }

  fun startObserving(canteenId: String) {
    activeCanteenId = canteenId
    viewModelScope.launch {
      _actionState.value = ItemActionState.Loading
      repository.observeCanteenItems(canteenId)
        .catch { e ->
          _actionState.value = ItemActionState.Error(e.message ?: "Failed to load menu items")
        }
        .collect { list ->
          _allItems.value = list
          _actionState.value = ItemActionState.Idle
        }
    }
  }

  fun onSearchQueryChanged(query: String) {
    _searchQuery.value = query
  }

  fun onCategorySelected(category: String) {
    _selectedCategory.value = category
  }

  fun saveItem(
    itemId: String = "",
    name: String,
    description: String,
    price: Int,
    stock: Int,
    prepMinutes: Int,
    category: String,
    imageUrl: String = "",
    imageUri: Uri? = null,
    ingredients: List<String> = emptyList(),
    customizationTitle: String = "",
    customizationOptions: List<String> = emptyList(),
    addons: List<com.example.data.firebase.AddonDocument> = emptyList(),
    onSuccess: () -> Unit = {},
  ) {
    if (name.trim().isBlank()) {
      _actionState.value = ItemActionState.Error("Item name cannot be empty.")
      return
    }
    if (price <= 0) {
      _actionState.value = ItemActionState.Error("Price must be greater than ₹0.")
      return
    }
    if (stock < 0) {
      _actionState.value = ItemActionState.Error("Stock cannot be negative.")
      return
    }
    if (prepMinutes <= 0) {
      _actionState.value = ItemActionState.Error("Preparation time must be at least 1 minute.")
      return
    }

    viewModelScope.launch {
      _actionState.value = ItemActionState.Loading
      try {
        var finalImageUrl = imageUrl
        var finalCloudinaryPublicId: String? = null
        var isCustom = false
        val generatedId = itemId.ifBlank { "item_${System.currentTimeMillis()}" }

        if (imageUri != null) {
          val uploadResult = repository.uploadImage(activeCanteenId, generatedId, imageUri)
          if (uploadResult.isSuccess) {
            val uploadDto = uploadResult.getOrThrow()
            finalImageUrl = uploadDto.imageUrl
            finalCloudinaryPublicId = uploadDto.cloudinaryPublicId
            isCustom = true
          }
        }

        val itemDoc = ItemDocument(
          id = generatedId,
          canteenId = activeCanteenId,
          name = name.trim(),
          description = description.trim(),
          price = price,
          stock = stock,
          available = stock > 0,
          preparationTime = "$prepMinutes min",
          prepMinutes = prepMinutes,
          category = category,
          imageUrl = finalImageUrl,
          cloudinaryPublicId = finalCloudinaryPublicId,
          isCustom = isCustom,
          ingredients = ingredients,
          customizationTitle = customizationTitle,
          customizationOptions = customizationOptions,
          addons = addons,
        )

        val result = if (itemId.isBlank()) {
          repository.addItem(activeCanteenId, itemDoc)
        } else {
          repository.updateItem(activeCanteenId, itemDoc).map { generatedId }
        }

        if (result.isSuccess) {
          _actionState.value = ItemActionState.Success(if (itemId.isBlank()) "Item added successfully" else "Item updated")
          onSuccess()
        } else {
          val err = result.exceptionOrNull()?.message ?: "Failed to save item"
          _actionState.value = ItemActionState.Error(err)
        }
      } catch (e: Exception) {
        _actionState.value = ItemActionState.Error(e.message ?: "Failed to save item")
      }
    }
  }

  fun deleteItem(item: ItemDocument, onSuccess: () -> Unit = {}) {
    viewModelScope.launch {
      _actionState.value = ItemActionState.Loading
      val result = repository.deleteItem(activeCanteenId, item)
      if (result.isSuccess) {
        _actionState.value = ItemActionState.Success("Item deleted")
        onSuccess()
      } else {
        _actionState.value = ItemActionState.Error(result.exceptionOrNull()?.message ?: "Failed to delete item")
      }
    }
  }

  fun toggleAvailability(item: ItemDocument, available: Boolean) {
    if (item.stock == 0 && available) {
      _actionState.value = ItemActionState.Error("Cannot enable an item with 0 stock. Please update stock first.")
      return
    }
    viewModelScope.launch {
      repository.toggleAvailability(activeCanteenId, item, available)
    }
  }

  fun updateStock(item: ItemDocument, newStock: Int) {
    viewModelScope.launch {
      repository.updateStock(activeCanteenId, item, newStock)
    }
  }

  fun resetActionState() {
    _actionState.value = ItemActionState.Idle
  }
}
