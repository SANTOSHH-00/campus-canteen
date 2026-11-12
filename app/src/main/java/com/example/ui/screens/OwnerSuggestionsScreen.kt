package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.ProductSuggestionDto
import com.example.data.api.ProductSuggestionRepository
import com.example.data.firebase.OwnerDocument
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerSuggestionsScreen(
  owner: OwnerDocument?,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val repository = remember { ProductSuggestionRepository() }

  var suggestions by remember { mutableStateOf<List<ProductSuggestionDto>>(emptyList()) }
  var isLoading by remember { mutableStateOf(true) }
  var selectedFilter by remember { mutableStateOf("All") }

  fun loadSuggestions() {
    isLoading = true
    scope.launch {
      val result = repository.getOwnerSuggestions()
      isLoading = false
      result.onSuccess { list ->
        suggestions = list
      }.onFailure { ex ->
        Toast.makeText(context, ex.message ?: "Failed to load suggestions", Toast.LENGTH_SHORT).show()
      }
    }
  }

  LaunchedEffect(Unit) {
    loadSuggestions()
  }

  val filteredSuggestions = remember(suggestions, selectedFilter) {
    if (selectedFilter == "All") suggestions
    else suggestions.filter { it.status.equals(selectedFilter, ignoreCase = true) }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(WarmCream)
  ) {
    // Top Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(PureWhite)
        .padding(horizontal = 8.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "Back",
          tint = BlackPrimary,
        )
      }

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Product Suggestions",
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = BlackPrimary,
        )
        Text(
          text = "${owner?.canteenId ?: "Canteen"} · Student Requests",
          fontSize = 12.sp,
          color = TextMuted,
        )
      }

      IconButton(onClick = { loadSuggestions() }) {
        Icon(
          imageVector = Icons.Default.Refresh,
          contentDescription = "Refresh",
          tint = DrawerAmber,
        )
      }
    }

    // Filter Chips Row
    LazyRow(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 10.dp, horizontal = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      val filterOptions = listOf("All", "New", "Under Review", "Accepted", "Rejected", "Added to Menu")
      items(filterOptions) { filter ->
        val isSelected = (selectedFilter == filter)
        val count = if (filter == "All") suggestions.size else suggestions.count { it.status.equals(filter, ignoreCase = true) }

        Surface(
          shape = RoundedCornerShape(16.dp),
          color = if (isSelected) DrawerAmber else PureWhite,
          border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) DrawerAmber else BorderGray),
          modifier = Modifier.clickable { selectedFilter = filter },
        ) {
          Text(
            text = "$filter ($count)",
            fontSize = 12.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) PureWhite else TextDark,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          )
        }
      }
    }

    // Content Body
    if (isLoading) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = DrawerAmber)
      }
    } else if (filteredSuggestions.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(24.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Fastfood,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(48.dp),
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = if (selectedFilter == "All") "No product suggestions yet" else "No suggestions with status \"$selectedFilter\"",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = BlackPrimary,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Suggestions submitted by students for your canteen will appear here.",
            fontSize = 13.sp,
            color = TextMuted,
            textAlign = TextAlign.Center,
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        items(filteredSuggestions, key = { it.suggestionId }) { suggestion ->
          SuggestionOwnerCard(
            suggestion = suggestion,
            onUpdateStatus = { newStatus ->
              scope.launch {
                val res = repository.updateSuggestionStatus(suggestion.suggestionId, newStatus)
                res.onSuccess {
                  Toast.makeText(context, "Status updated to $newStatus", Toast.LENGTH_SHORT).show()
                  loadSuggestions()
                }.onFailure { ex ->
                  Toast.makeText(context, ex.message ?: "Failed to update", Toast.LENGTH_SHORT).show()
                }
              }
            },
          )
        }
      }
    }
  }
}

@Composable
private fun SuggestionOwnerCard(
  suggestion: ProductSuggestionDto,
  onUpdateStatus: (String) -> Unit,
) {
  var menuExpanded by remember { mutableStateOf(false) }

  val dateFormatted = remember(suggestion.createdAt) {
    try {
      if (suggestion.createdAt.isNotBlank()) {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = parser.parse(suggestion.createdAt) ?: Date()
        SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(date)
      } else "Recently"
    } catch (_: Exception) {
      "Recently"
    }
  }

  val statusColor = when (suggestion.status) {
    "New" -> Color(0xFFF59E0B)
    "Under Review" -> Color(0xFF3B82F6)
    "Accepted" -> Color(0xFF10B981)
    "Added to Menu" -> Color(0xFF059669)
    "Rejected" -> Color(0xFFEF4444)
    else -> DrawerAmber
  }

  Surface(
    shape = RoundedCornerShape(18.dp),
    color = PureWhite,
    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray.copy(alpha = 0.7f)),
    shadowElevation = 1.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      // Header: Student Name + Date + Status Pill
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = suggestion.studentName.ifBlank { "Student" },
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = BlackPrimary,
          )
          val regOrEmail = suggestion.studentRegistration.ifBlank { suggestion.studentEmail }
          if (regOrEmail.isNotBlank()) {
            Text(
              text = regOrEmail,
              fontSize = 11.5.sp,
              color = TextMuted,
            )
          }
        }

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(statusColor.copy(alpha = 0.12f))
            .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
          Text(
            text = suggestion.status,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = statusColor,
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Suggestion Text
      Text(
        text = suggestion.suggestionText,
        fontSize = 14.sp,
        color = TextDark,
        lineHeight = 20.sp,
      )

      Spacer(modifier = Modifier.height(14.dp))

      // Footer: Date + Status Change Dropdown
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = dateFormatted,
          fontSize = 11.5.sp,
          color = TextMuted,
        )

        Box {
          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(10.dp))
              .background(WarmCream)
              .border(1.dp, BorderGray, RoundedCornerShape(10.dp))
              .clickable { menuExpanded = true }
              .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Update Status",
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              color = BlackPrimary,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
              imageVector = Icons.Default.ArrowDropDown,
              contentDescription = null,
              tint = BlackPrimary,
              modifier = Modifier.size(16.dp),
            )
          }

          DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.background(PureWhite),
          ) {
            val statusChoices = listOf("New", "Under Review", "Accepted", "Rejected", "Added to Menu")
            statusChoices.forEach { statusOption ->
              DropdownMenuItem(
                text = {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    if (suggestion.status == statusOption) {
                      Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = DrawerAmber,
                        modifier = Modifier.size(16.dp),
                      )
                      Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                      text = statusOption,
                      fontWeight = if (suggestion.status == statusOption) FontWeight.Bold else FontWeight.Normal,
                    )
                  }
                },
                onClick = {
                  menuExpanded = false
                  if (suggestion.status != statusOption) {
                    onUpdateStatus(statusOption)
                  }
                },
              )
            }
          }
        }
      }
    }
  }
}
