package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FoodAddon
import com.example.data.FoodItem
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DeliveryOrange
import com.example.ui.theme.DeliveryOrangeLight
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RatingStarYellow
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Exact Match Food Product Detail Screen:
 * - Top rounded image card with back button & favorite heart button
 * - Food Title & Price in bold dark text
 * - Rating & Prep Time row with vertical divider
 * - Ingredients list with pill capsules
 * - Customize radio selection (e.g. White vs Wheat)
 * - Add-ons selection card (+₹10 Cheese, +₹5 Extra Sauce)
 * - Quantity pill & dynamic "Add to Cart • ₹XX" action button
 * - Estimated ready time calculated from prepMinutes
 */
@Composable
fun FoodDetailScreen(
  foodItem: FoodItem,
  isFavorite: Boolean = false,
  onBack: () -> Unit,
  onToggleFavorite: (String) -> Unit = {},
  onAddToCart: (quantity: Int, selectedOption: String?, selectedAddons: List<FoodAddon>) -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onBack)

  // State: Options & Add-ons
  val options = foodItem.displayCustomizationOptions
  var selectedOption by remember(foodItem) { mutableStateOf(options.firstOrNull() ?: "") }

  val availableAddons = foodItem.displayAddons
  val selectedAddons = remember(foodItem) { mutableStateListOf<FoodAddon>() }

  var quantity by remember(foodItem) { mutableIntStateOf(1) }
  var localFavorite by remember(foodItem, isFavorite) { mutableStateOf(isFavorite) }

  // Calculation: (Base Price + Addons) * Quantity
  val unitPrice = foodItem.price + selectedAddons.sumOf { it.price }
  val totalPrice = unitPrice * quantity

  // Estimated ready time calculation
  val readyTimeStr = remember(foodItem.prepMinutes) {
    val cal = Calendar.getInstance()
    cal.add(Calendar.MINUTE, foodItem.prepMinutes)
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    sdf.format(cal.time)
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(WarmCream)
      .statusBarsPadding()
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .padding(bottom = 120.dp), // Extra space for sticky bottom bar
    ) {
      Spacer(modifier = Modifier.height(12.dp))

      // ── 1. Top Food Image Card with Floating Back & Heart Buttons ─────────
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(240.dp)
          .shadow(8.dp, RoundedCornerShape(24.dp), clip = false)
          .clip(RoundedCornerShape(24.dp))
          .background(Color(0xFFEFE8DD)),
      ) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val decorativeFallback: @Composable () -> Unit = {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(
                Brush.verticalGradient(
                  colors = listOf(
                    Color(0xFFF3E7D7),
                    Color(0xFFE2D0B8),
                  )
                )
              ),
            contentAlignment = Alignment.Center,
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Box(
                modifier = Modifier
                  .size(72.dp)
                  .clip(CircleShape)
                  .background(DeliveryOrangeLight),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.Restaurant,
                  contentDescription = null,
                  tint = DeliveryOrange,
                  modifier = Modifier.size(36.dp),
                )
              }
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = foodItem.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextDark,
              )
            }
          }
        }

        if (foodItem.imageUrl.isNotBlank()) {
          val imageReq = remember(foodItem.imageUrl) {
            coil.request.ImageRequest.Builder(context)
              .data(foodItem.imageUrl)
              .crossfade(200)
              .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
              .diskCachePolicy(coil.request.CachePolicy.ENABLED)
              .build()
          }
          coil.compose.SubcomposeAsyncImage(
            model = imageReq,
            contentDescription = foodItem.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = {
              com.example.ui.components.ShimmerPlaceholder(modifier = Modifier.fillMaxSize())
            },
            error = {
              decorativeFallback()
            }
          )
        } else {
          decorativeFallback()
        }

        // Floating Back Button (Top-Left)
        Box(
          modifier = Modifier
            .padding(14.dp)
            .align(Alignment.TopStart)
            .size(42.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(PureWhite)
            .clickable { onBack() },
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = TextDark,
            modifier = Modifier.size(20.dp),
          )
        }

        // Floating Favorite Button (Top-Right)
        Box(
          modifier = Modifier
            .padding(14.dp)
            .align(Alignment.TopEnd)
            .size(42.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(PureWhite)
            .clickable {
              localFavorite = !localFavorite
              onToggleFavorite(foodItem.id)
            },
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = if (localFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = "Favorite",
            tint = if (localFavorite) Color(0xFFEF4444) else TextDark,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // ── 2. Title & Price Header ──────────────────────────────────────────
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = foodItem.name,
          fontSize = 24.sp,
          fontWeight = FontWeight.ExtraBold,
          color = TextDark,
          modifier = Modifier.weight(1f, fill = false),
        )
        Text(
          text = "₹${foodItem.price}",
          fontSize = 24.sp,
          fontWeight = FontWeight.ExtraBold,
          color = TextDark,
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      // ── 3. Rating & Prep Time Row ─────────────────────────────────────────
      Row(
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.Default.Star,
          contentDescription = "Rating",
          tint = RatingStarYellow,
          modifier = Modifier.size(17.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = "${foodItem.rating ?: 4.7}",
          fontSize = 13.5.sp,
          fontWeight = FontWeight.ExtraBold,
          color = TextDark,
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
          text = "(${foodItem.ordersCount})",
          fontSize = 13.sp,
          fontWeight = FontWeight.Medium,
          color = TextMuted,
        )

        Text(
          text = "   |   ",
          fontSize = 14.sp,
          fontWeight = FontWeight.Light,
          color = BorderGray,
        )

        Icon(
          imageVector = Icons.Default.AccessTime,
          contentDescription = "Prep time",
          tint = TextMuted,
          modifier = Modifier.size(15.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = foodItem.prepTime,
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
          color = TextMuted,
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      // ── 4. Description ────────────────────────────────────────────────────
      Text(
        text = foodItem.description.ifEmpty {
          "Fresh vegetables, cheese and special house sauce in crispy bread."
        },
        fontSize = 13.5.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        color = TextMuted,
      )

      Spacer(modifier = Modifier.height(20.dp))

      // ── 5. Ingredients Section ───────────────────────────────────────────
      if (foodItem.displayIngredients.isNotEmpty()) {
        Text(
          text = "Ingredients",
          fontSize = 17.sp,
          fontWeight = FontWeight.ExtraBold,
          color = TextDark,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          foodItem.displayIngredients.forEach { ingredient ->
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFEFECE6))
                .border(1.dp, BorderGray.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 7.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = ingredient,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(22.dp))
      }

      // ── 6. Customize Section ─────────────────────────────────────────────
      if (options.isNotEmpty()) {
        Text(
          text = "Customize",
          fontSize = 17.5.sp,
          fontWeight = FontWeight.ExtraBold,
          color = TextDark,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Bread Type / Primary Option Subheading
        Text(
          text = foodItem.displayCustomizationTitle.ifBlank { "Options" },
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold,
          color = TextMuted,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Radio Button Choices
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(20.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          options.forEach { option ->
            val isSelected = selectedOption == option
            Row(
              modifier = Modifier
                .clickable { selectedOption = option }
                .padding(vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                contentDescription = option,
                tint = if (isSelected) BlackPrimary else BorderGray,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = option,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = TextDark,
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))
      }

      // ── 7. Add-ons Section ───────────────────────────────────────────────
      if (availableAddons.isNotEmpty()) {
        Text(
          text = "Add-ons",
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold,
          color = TextMuted,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = PureWhite),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            availableAddons.forEach { addon ->
              val isChecked = selectedAddons.any { it.name == addon.name }

              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable {
                    if (isChecked) {
                      selectedAddons.removeAll { it.name == addon.name }
                    } else {
                      selectedAddons.add(addon)
                    }
                  }
                  .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = if (isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = addon.name,
                    tint = if (isChecked) BlackPrimary else BorderGray,
                    modifier = Modifier.size(20.dp),
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Text(
                    text = addon.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark,
                  )
                }

                Text(
                  text = "+₹${addon.price}",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  color = TextMuted,
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))
      }
    }

    // ── 7. Pinned Bottom Action Bar & Estimated Ready Time ──────────────────
    Surface(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth(),
      color = WarmCream,
      shadowElevation = 12.dp,
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // Quantity Selector Pill: [ - ]  1  [ + ]
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(16.dp))
              .background(PureWhite)
              .border(1.5.dp, BorderGray.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
              .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Box(
                modifier = Modifier
                  .size(28.dp)
                  .clickable {
                    if (quantity > 1) quantity--
                  },
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.Remove,
                  contentDescription = "Decrease",
                  tint = TextDark,
                  modifier = Modifier.size(16.dp),
                )
              }

              Text(
                text = "$quantity",
                fontSize = 15.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextDark,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(20.dp),
              )

              Box(
                modifier = Modifier
                  .size(28.dp)
                  .clickable { quantity++ },
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.Add,
                  contentDescription = "Increase",
                  tint = TextDark,
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          }

          Spacer(modifier = Modifier.width(14.dp))

          // Black Add to Cart Button: "Add to Cart • ₹XX"
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(16.dp))
              .background(BlackPrimary)
              .clickable {
                onAddToCart(quantity, selectedOption, selectedAddons.toList())
                onBack()
              }
              .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "Add to Cart • ₹$totalPrice",
              fontSize = 15.sp,
              fontWeight = FontWeight.ExtraBold,
              color = PureWhite,
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Estimated Ready Time Footer: ⏱ Estimated ready time: 12:42 PM
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Default.AccessTime,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(15.dp),
          )
          Spacer(modifier = Modifier.width(5.dp))
          Text(
            text = "Estimated ready time: $readyTimeStr",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted,
          )
        }
      }
    }
  }
}
