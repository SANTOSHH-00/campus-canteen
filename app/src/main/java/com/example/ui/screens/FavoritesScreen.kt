package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BottomNavTab
import com.example.data.CampusBlockCanteen
import com.example.data.FoodItem
import com.example.ui.components.FoodImagePlaceholder
import com.example.ui.state.CanteenAppState
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream

/**
 * Full-screen dedicated Favorites page showing all food items the user has added to favorites.
 *
 * Adheres strictly to Quick Bite's clean, modern, and monochrome visual language:
 * - Back button & monochrome header with favorite count.
 * - Responsive 1-2 column grid on mobile (up to 4 on tablets).
 * - Large food images with shimmer loading.
 * - Monochrome canteen vector icon beside canteen name (strictly no food thumbnails).
 * - Instant reactive heart toggle that updates local session and syncs to backend.
 * - Quick "Add to Cart" action with instant feedback.
 * - Monochrome empty state with "Explore Menu" button.
 */
@Composable
fun FavoritesScreen(
  appState: CanteenAppState,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  // Resolve all favorited items across all campus canteens and caches
  val favoriteItemsWithCanteen = remember(
    appState.favoriteItemIds.toList(),
    appState.allCanteens,
    appState.selectedCanteen,
    appState.allKnownFoodItems.toMap(),
  ) {
    val result = mutableListOf<Pair<FoodItem, CampusBlockCanteen>>()
    val candidateCanteens = (listOf(appState.selectedCanteen) + appState.allCanteens + com.example.data.SampleFoodData.campusCanteens).distinctBy { it.id }
    for (itemId in appState.favoriteItemIds) {
      val cached = appState.allKnownFoodItems[itemId]
      if (cached != null) {
        result.add(cached)
        continue
      }
      var foundPair: Pair<FoodItem, CampusBlockCanteen>? = null
      for (canteen in candidateCanteens) {
        val found = canteen.allItems.find { it.id == itemId }
        if (found != null) {
          foundPair = found to canteen
          break
        }
      }
      if (foundPair != null) {
        result.add(foundPair)
      }
    }
    result
  }

  val totalCount = favoriteItemsWithCanteen.size

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(WarmCream)
      .statusBarsPadding()
  ) {
    Column(
      modifier = Modifier.fillMaxSize()
    ) {
      // ── 1. Top Header Bar ──────────────────────────────────────────────────
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Back Button
        Box(
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(PureWhite)
            .border(1.dp, BorderGray.copy(alpha = 0.8f), CircleShape)
            .clickable(onClick = onBack),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = BlackPrimary,
            modifier = Modifier.size(20.dp),
          )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Title: ♡ Favorites + Count Badge
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = BlackPrimary,
            modifier = Modifier.size(22.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Favorites",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = BlackPrimary,
            letterSpacing = (-0.3).sp,
          )

          if (totalCount > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFEFEFEF))
                .padding(horizontal = 8.dp, vertical = 2.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = "$totalCount",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = BlackPrimary,
              )
            }
          }
        }
      }

      // ── 2. Content Area: Grid or Empty State ──────────────────────────────
      if (favoriteItemsWithCanteen.isEmpty()) {
        // Empty State matching requirement 8
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            // Monochrome Heart Icon Container
            Box(
              modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(PureWhite)
                .border(1.5.dp, BorderGray.copy(alpha = 0.9f), CircleShape),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.FavoriteBorder,
                contentDescription = "No Favorites",
                tint = BlackPrimary,
                modifier = Modifier.size(38.dp),
              )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
              text = "No Favorites Yet",
              fontSize = 20.sp,
              fontWeight = FontWeight.Black,
              color = BlackPrimary,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = "Save your favorite food items here\nand find them quickly next time.",
              fontSize = 13.5.sp,
              fontWeight = FontWeight.Medium,
              color = TextMuted,
              textAlign = TextAlign.Center,
              lineHeight = 20.sp,
            )

            Spacer(modifier = Modifier.height(26.dp))

            Button(
              onClick = {
                onBack()
                appState.currentTab = BottomNavTab.MENU
              },
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = BlackPrimary,
                contentColor = PureWhite,
              ),
              contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
              elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
            ) {
              Text(
                text = "Explore Menu",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = PureWhite,
              )
            }
          }
        }
      } else {
        // Responsive Food Cards Grid (1-2 columns on mobile, 3-4 on wide screens)
        LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = 164.dp),
          contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          items(
            items = favoriteItemsWithCanteen,
            key = { (item, _) -> item.id }
          ) { (item, canteen) ->
            FavoriteFoodCard(
              foodItem = item,
              canteen = canteen,
              onCardClick = {
                appState.openFoodDetail(item)
              },
              onToggleFavorite = {
                appState.toggleFavorite(item.id)
              },
              onAddToCart = {
                appState.addToCart(item)
              },
            )
          }
        }
      }
    }
  }
}

/**
 * Large, modern food card for the Favorites screen.
 * Displays prominent food image, item name, monochrome canteen icon + name,
 * price, short description, immediate heart toggle, and Add to Cart action.
 */
@Composable
private fun FavoriteFoodCard(
  foodItem: FoodItem,
  canteen: CampusBlockCanteen,
  onCardClick: () -> Unit,
  onToggleFavorite: () -> Unit,
  onAddToCart: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
      .clickable(onClick = onCardClick),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = PureWhite),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray.copy(alpha = 0.8f)),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      // ── Large Prominent Food Image with Floating Heart Button ─────────────
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(1.25f)
          .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
      ) {
        FoodImagePlaceholder(
          itemName = foodItem.name,
          imageUrl = foodItem.imageUrl,
          rating = foodItem.rating,
          modifier = Modifier.fillMaxSize(),
        )

        // Top-right Floating Favorite Heart Button
        Box(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(8.dp)
            .size(34.dp)
            .clip(CircleShape)
            .background(PureWhite.copy(alpha = 0.92f))
            .border(1.dp, BorderGray.copy(alpha = 0.6f), CircleShape)
            .clickable(onClick = onToggleFavorite),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = "Remove Favorite",
            tint = BlackPrimary,
            modifier = Modifier.size(18.dp),
          )
        }

        // Closed / Unavailable overlay if canteen is closed
        if (!canteen.isOpen) {
          Box(
            modifier = Modifier
              .align(Alignment.BottomStart)
              .padding(8.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(Color.Black.copy(alpha = 0.72f))
              .padding(horizontal = 7.dp, vertical = 3.dp),
          ) {
            Text(
              text = "Canteen Closed",
              fontSize = 10.5.sp,
              fontWeight = FontWeight.Bold,
              color = PureWhite,
            )
          }
        }
      }

      // ── Food Item Details ──────────────────────────────────────────────────
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 10.dp)
      ) {
        // Food Name
        Text(
          text = foodItem.name,
          fontSize = 14.5.sp,
          fontWeight = FontWeight.ExtraBold,
          color = BlackPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(3.dp))

        // Canteen Name with monochrome vector icon (strictly no food thumbnails)
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(
            imageVector = Icons.Default.Storefront,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(13.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = canteen.name,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        // Optional short description if available
        if (foodItem.description.isNotBlank()) {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = foodItem.description,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            color = Color(0xFF6B7280),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Price + [ Add to Cart ] Action Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "₹${foodItem.price}",
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            color = BlackPrimary,
          )

          // Add to Cart Button
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .background(BlackPrimary)
              .clickable(enabled = canteen.isOpen, onClick = onAddToCart)
              .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add",
                tint = PureWhite,
                modifier = Modifier.size(14.dp),
              )
              Spacer(modifier = Modifier.width(2.dp))
              Text(
                text = "Add",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = PureWhite,
              )
            }
          }
        }
      }
    }
  }
}
