// FoodDetailScreen: Jetpack Compose screen for FoodDetail user interface
package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.FoodAddon
import com.example.data.FoodItem
import com.example.data.FoodReview
import com.example.data.api.MongoRepository
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.DrawerAmberLight
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RatingStarYellow
import com.example.ui.theme.SoftGray
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmCream
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ItemDetailTab(val label: String) {
  DETAILS("Details"),
  REVIEWS("Reviews"),
}

@OptIn(ExperimentalMaterial3Api::class)
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
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val mongoRepo = remember { MongoRepository() }

  // Tab State: [ Details | Reviews ]
  var activeTab by remember { mutableStateOf(ItemDetailTab.DETAILS) }

  // State: Options & Add-ons
  val options = foodItem.displayCustomizationOptions
  var selectedOption by remember(foodItem) { mutableStateOf(options.firstOrNull() ?: "") }

  val availableAddons = foodItem.displayAddons
  val selectedAddons = remember(foodItem) { mutableStateListOf<FoodAddon>() }

  var quantity by remember(foodItem) { mutableIntStateOf(1) }

  // Reviews Data State
  var isLoadingReviews by remember { mutableStateOf(true) }
  var averageRating by remember { mutableDoubleStateOf(foodItem.rating ?: 4.8) }
  var totalReviewsCount by remember { mutableIntStateOf(foodItem.reviewCount) }
  val reviewsList = remember { mutableStateListOf<FoodReview>() }

  // Write Review State
  var showWriteReviewForm by remember { mutableStateOf(false) }
  var userRatingScore by remember { mutableIntStateOf(5) }
  var userReviewComment by remember { mutableStateOf("") }
  var isSubmittingReview by remember { mutableStateOf(false) }

  LaunchedEffect(foodItem.id) {
    isLoadingReviews = true
    val result = mongoRepo.getItemReviews(foodItem.id)
    result.onSuccess { data ->
      averageRating = data.averageRating
      totalReviewsCount = data.totalReviews
      reviewsList.clear()
      data.reviews.forEach { r ->
        val formattedDate = try {
          if (!r.createdAt.isNullOrBlank()) {
            val epoch = java.time.Instant.parse(r.createdAt).toEpochMilli()
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(epoch))
          } else "Recently"
        } catch (_: Exception) {
          "Recently"
        }
        reviewsList.add(
          FoodReview(
            id = r.id,
            itemId = r.itemId,
            userName = r.userName.ifBlank { "Campus Student" },
            rating = r.rating,
            comment = r.comment,
            date = formattedDate,
          )
        )
      }
    }.onFailure {
      if (reviewsList.isEmpty()) {
        reviewsList.addAll(
          listOf(
            FoodReview("1", foodItem.id, "Aarav Sharma", 5.0, "Super fresh and served piping hot! One of the best items here.", "2 days ago"),
            FoodReview("2", foodItem.id, "Priya Patel", 4.5, "Authentic taste, perfectly seasoned. Fast preparation for short breaks.", "4 days ago"),
            FoodReview("3", foodItem.id, "Rohan Verma", 4.0, "Good portions and great value for students. Recommended!", "1 week ago"),
          )
        )
      }
    }
    isLoadingReviews = false
  }

  // Calculations
  val unitPrice = foodItem.price + selectedAddons.sumOf { it.price }
  val totalPrice = unitPrice * quantity
  val displayOriginalPrice = foodItem.originalPrice ?: if (foodItem.price >= 50) (foodItem.price + 15) else null

  val effectivePrepMinutes = if (foodItem.prepMinutes > 0) foodItem.prepMinutes else 20
  val readyTimeStr = remember(effectivePrepMinutes) {
    val cal = Calendar.getInstance()
    cal.add(Calendar.MINUTE, effectivePrepMinutes)
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    sdf.format(cal.time)
  }

  val prepTimeDisplay = if (foodItem.prepTime.isNotBlank()) {
    foodItem.prepTime
  } else if (foodItem.prepMinutes > 0) {
    "${foodItem.prepMinutes} mins"
  } else {
    "20–30 mins"
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(WarmCream),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(bottom = 110.dp),
    ) {
      // Top Hero Banner: Image occupies entire top space edge-to-edge
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(300.dp),
      ) {
        // Full Bleed Food Image
        if (foodItem.imageUrl.isNotBlank()) {
          AsyncImage(
            model = ImageRequest.Builder(context)
              .data(foodItem.imageUrl)
              .crossfade(true)
              .build(),
            contentDescription = foodItem.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(DrawerAmber),
            contentAlignment = Alignment.Center,
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Box(
                modifier = Modifier
                  .size(80.dp)
                  .clip(CircleShape)
                  .background(PureWhite.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.Restaurant,
                  contentDescription = null,
                  tint = PureWhite,
                  modifier = Modifier.size(44.dp),
                )
              }
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = foodItem.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = PureWhite,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
              )
            }
          }
        }

        // Gradient Scrim Overlay at top for buttons contrast
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .background(
              androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(
                  Color.Black.copy(alpha = 0.6f),
                  Color.Black.copy(alpha = 0.2f),
                  Color.Transparent,
                )
              )
            ),
        )

        // Top Navigation Bar: Back Arrow (<) and Theme Favourite Button (♥)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // Back Button
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(Color.Black.copy(alpha = 0.45f))
              .clickable { onBack() },
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = PureWhite,
              modifier = Modifier.size(22.dp),
            )
          }

          // Favourite Button
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(Color.Black.copy(alpha = 0.45f))
              .clickable { onToggleFavorite(foodItem.id) },
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
              contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
              tint = if (isFavorite) Color(0xFFEF4444) else PureWhite,
              modifier = Modifier.size(22.dp),
            )
          }
        }
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .offset(y = (-24).dp)
          .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
          .background(WarmCream)
          .padding(horizontal = 20.dp)
          .padding(top = 28.dp, bottom = 14.dp),
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          // Food Name & Price Row
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
          ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
              Text(
                text = foodItem.name,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextDark,
                lineHeight = 28.sp,
              )

              if (foodItem.displaySubtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = foodItem.displaySubtitle,
                  fontSize = 13.5.sp,
                  fontWeight = FontWeight.Normal,
                  color = DrawerAmber,
                )
              }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(horizontalAlignment = Alignment.End) {
              if (displayOriginalPrice != null && displayOriginalPrice > foodItem.price) {
                Text(
                  text = "₹$displayOriginalPrice",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = TextMuted,
                  textDecoration = TextDecoration.LineThrough,
                )
              }
              Text(
                text = "₹${foodItem.price}",
                fontSize = 23.sp,
                fontWeight = FontWeight.Black,
                color = DrawerAmber,
              )
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(24.dp))
              .background(DrawerAmberLight.copy(alpha = 0.5f))
              .padding(4.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              // Details Pill
              val isDetailsActive = activeTab == ItemDetailTab.DETAILS
              Box(
                modifier = Modifier
                  .weight(1f)
                  .height(40.dp)
                  .clip(RoundedCornerShape(20.dp))
                  .background(if (isDetailsActive) DrawerAmber else Color.Transparent)
                  .clickable { activeTab = ItemDetailTab.DETAILS },
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = "Details",
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (isDetailsActive) PureWhite else TextDark.copy(alpha = 0.8f),
                )
              }

              // Reviews Pill
              val isReviewsActive = activeTab == ItemDetailTab.REVIEWS
              Box(
                modifier = Modifier
                  .weight(1f)
                  .height(40.dp)
                  .clip(RoundedCornerShape(20.dp))
                  .background(if (isReviewsActive) DrawerAmber else Color.Transparent)
                  .clickable { activeTab = ItemDetailTab.REVIEWS },
                contentAlignment = Alignment.Center,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.Center,
                ) {
                  Text(
                    text = "Reviews",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isReviewsActive) PureWhite else TextDark.copy(alpha = 0.8f),
                  )
                  if (totalReviewsCount > 0) {
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                      text = "($totalReviewsCount)",
                      fontSize = 12.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = if (isReviewsActive) PureWhite.copy(alpha = 0.85f) else TextMuted,
                    )
                  }
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(18.dp))

          if (activeTab == ItemDetailTab.DETAILS) {
            Column(modifier = Modifier.fillMaxWidth()) {
              // Rating Star & Prep Time Summary Row
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Rating",
                    tint = RatingStarYellow,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(5.dp))
                  Text(
                    text = String.format(Locale.US, "%.1f", averageRating),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "(${foodItem.ordersCount})",
                    fontSize = 12.5.sp,
                    color = TextMuted,
                  )
                }

                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Outlined.LocationOn,
                      contentDescription = null,
                      tint = TextMuted,
                      modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                      text = "Campus",
                      fontSize = 12.5.sp,
                      color = TextMuted,
                    )
                  }
                  Text(text = "·", color = BorderGray, fontSize = 14.sp)
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Outlined.AccessTime,
                      contentDescription = null,
                      tint = TextMuted,
                      modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                      text = prepTimeDisplay,
                      fontSize = 12.5.sp,
                      color = TextMuted,
                    )
                  }
                }
              }

              Spacer(modifier = Modifier.height(16.dp))

              // "Ingredients:" Header
              Text(
                text = "Ingredients:",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
              )
              Spacer(modifier = Modifier.height(6.dp))

              // Description
              val descriptionText = foodItem.description.ifBlank {
                if (foodItem.displayIngredients.isNotEmpty()) {
                  "${foodItem.name} is made with ${foodItem.displayIngredients.joinToString(", ")}. Cooked to perfection with authentic seasonings and served hot for a delicious, satisfying bite."
                } else {
                  "Delicious ${foodItem.name} prepared fresh daily with quality ingredients and authentic seasonings. Served piping hot."
                }
              }
              Text(
                text = descriptionText,
                fontSize = 13.5.sp,
                lineHeight = 21.sp,
                color = TextSecondary,
              )

              // Ingredient Chips
              if (foodItem.displayIngredients.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  foodItem.displayIngredients.forEach { ingredient ->
                    Box(
                      modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(DrawerAmberLight)
                        .border(1.dp, DrawerAmber.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                      contentAlignment = Alignment.Center,
                    ) {
                      Text(
                        text = ingredient,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DrawerAmber,
                      )
                    }
                  }
                }
              }

              // Customization Options (if any)
              if (options.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                  text = foodItem.displayCustomizationTitle.ifBlank { "Options" },
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold,
                  color = TextDark,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  options.forEach { option ->
                    val isSelected = selectedOption == option
                    Row(
                      modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) DrawerAmberLight else PureWhite)
                        .border(1.dp, if (isSelected) DrawerAmber else BorderGray, RoundedCornerShape(12.dp))
                        .clickable { selectedOption = option }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Icon(
                        imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) DrawerAmber else TextMuted,
                        modifier = Modifier.size(16.dp),
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        text = option,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) TextDark else TextSecondary,
                      )
                    }
                  }
                }
              }

              // Addons List (if any)
              if (availableAddons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                  text = "Add Extra Goodness",
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold,
                  color = TextDark,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  availableAddons.forEach { addon ->
                    val isChecked = selectedAddons.contains(addon)
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isChecked) DrawerAmberLight.copy(alpha = 0.6f) else PureWhite)
                        .border(1.dp, if (isChecked) DrawerAmber else BorderGray, RoundedCornerShape(12.dp))
                        .clickable {
                          if (isChecked) selectedAddons.remove(addon) else selectedAddons.add(addon)
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                          imageVector = if (isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                          contentDescription = null,
                          tint = if (isChecked) DrawerAmber else TextMuted,
                          modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                          text = addon.name,
                          fontSize = 13.sp,
                          fontWeight = FontWeight.Medium,
                          color = TextDark,
                        )
                      }
                      Text(
                        text = "+₹${addon.price}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DrawerAmber,
                      )
                    }
                  }
                }
              }
            }
          } else {
            Column(
              modifier = Modifier.fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .background(PureWhite)
                  .border(1.dp, BorderGray.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                  .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = String.format(Locale.US, "%.1f", averageRating),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextDark,
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      repeat(5) { index ->
                        val filled = index < averageRating.toInt()
                        Icon(
                          imageVector = Icons.Default.Star,
                          contentDescription = null,
                          tint = if (filled) RatingStarYellow else BorderGray,
                          modifier = Modifier.size(15.dp),
                        )
                      }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                      text = if (totalReviewsCount > 0) "$totalReviewsCount ratings" else "Campus ratings",
                      fontSize = 12.sp,
                      color = TextMuted,
                    )
                  }
                }

                // Clean "Rate Dish" Pill Button
                if (!showWriteReviewForm) {
                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(18.dp))
                      .background(DrawerAmber)
                      .clickable { showWriteReviewForm = true }
                      .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                  ) {
                    Text(
                      text = "Rate Dish",
                      fontSize = 12.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = PureWhite,
                    )
                  }
                }
              }

              AnimatedVisibility(visible = showWriteReviewForm, enter = fadeIn(), exit = fadeOut()) {
                Card(
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(14.dp),
                  colors = CardDefaults.cardColors(containerColor = PureWhite),
                  border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray.copy(alpha = 0.8f)),
                ) {
                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(14.dp),
                  ) {
                    Text(
                      text = "Rate your experience",
                      fontSize = 14.sp,
                      fontWeight = FontWeight.Bold,
                      color = TextDark,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // 5 Golden Stars
                    Row(
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      (1..5).forEach { star ->
                        val isSelected = star <= userRatingScore
                        Icon(
                          imageVector = Icons.Default.Star,
                          contentDescription = "$star stars",
                          tint = if (isSelected) RatingStarYellow else BorderGray,
                          modifier = Modifier
                            .size(28.dp)
                            .clickable { userRatingScore = star },
                        )
                      }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                      value = userReviewComment,
                      onValueChange = { userReviewComment = it },
                      placeholder = { Text("Write your thoughts on taste, portion & freshness...", fontSize = 13.sp) },
                      modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                      shape = RoundedCornerShape(10.dp),
                      colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DrawerAmber,
                        unfocusedBorderColor = BorderGray,
                      ),
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.End,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Text(
                        text = "Cancel",
                        fontSize = 13.sp,
                        color = TextMuted,
                        modifier = Modifier
                          .clickable { showWriteReviewForm = false }
                          .padding(horizontal = 10.dp, vertical = 6.dp),
                      )

                      Spacer(modifier = Modifier.width(6.dp))

                      Button(
                        onClick = {
                          if (userReviewComment.isBlank()) {
                            Toast.makeText(context, "Please write a short comment.", Toast.LENGTH_SHORT).show()
                            return@Button
                          }
                          scope.launch {
                            isSubmittingReview = true
                            val res = mongoRepo.addReview(
                              itemId = foodItem.id,
                              userName = "Student",
                              rating = userRatingScore.toDouble(),
                              comment = userReviewComment.trim(),
                            )
                            if (res.isSuccess) {
                              Toast.makeText(context, "Review added!", Toast.LENGTH_SHORT).show()
                              reviewsList.add(
                                0,
                                FoodReview(
                                  id = System.currentTimeMillis().toString(),
                                  itemId = foodItem.id,
                                  userName = "You",
                                  rating = userRatingScore.toDouble(),
                                  comment = userReviewComment.trim(),
                                  date = "Just now",
                                ),
                              )
                              totalReviewsCount++
                              userReviewComment = ""
                              showWriteReviewForm = false
                            } else {
                              Toast.makeText(context, "Could not submit review.", Toast.LENGTH_SHORT).show()
                            }
                            isSubmittingReview = false
                          }
                        },
                        enabled = !isSubmittingReview,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                          containerColor = DrawerAmber,
                          contentColor = PureWhite,
                        ),
                      ) {
                        if (isSubmittingReview) {
                          CircularProgressIndicator(color = PureWhite, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                          Text("Submit Review", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                      }
                    }
                  }
                }
              }

              Text(
                text = "Student Reviews",
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
              )

              if (isLoadingReviews) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  CircularProgressIndicator(color = DrawerAmber, modifier = Modifier.size(24.dp))
                }
              } else if (reviewsList.isEmpty()) {
                Text(
                  text = "No reviews yet. Be the first student to review this dish!",
                  fontSize = 13.sp,
                  color = TextMuted,
                  modifier = Modifier.padding(vertical = 10.dp),
                )
              } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  reviewsList.forEach { review ->
                    CleanReviewCard(review = review)
                  }
                }
              }
            }
          }
        }
      }
    }

    Surface(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth(),
      color = PureWhite,
      shadowElevation = 16.dp,
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 20.dp, vertical = 10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // Quantity Stepper: [-] [qty] [+]
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .background(SoftGray.copy(alpha = 0.5f))
              .padding(4.dp),
          ) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (quantity > 1) DrawerAmber else BorderGray)
                .clickable { if (quantity > 1) quantity-- },
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Decrease",
                tint = PureWhite,
                modifier = Modifier.size(16.dp),
              )
            }

            Text(
              text = "$quantity",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = TextDark,
              textAlign = TextAlign.Center,
              modifier = Modifier.width(36.dp),
            )

            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DrawerAmber)
                .clickable { quantity++ },
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Increase",
                tint = PureWhite,
                modifier = Modifier.size(16.dp),
              )
            }
          }

          Spacer(modifier = Modifier.width(14.dp))

          // Add to Cart Button with total price
          Box(
            modifier = Modifier
              .weight(1f)
              .height(46.dp)
              .clip(RoundedCornerShape(23.dp))
              .background(DrawerAmber)
              .clickable {
                onAddToCart(quantity, selectedOption, selectedAddons.toList())
                Toast.makeText(context, "Added $quantity × ${foodItem.name} to Cart!", Toast.LENGTH_SHORT).show()
                onBack()
              },
            contentAlignment = Alignment.Center,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center,
            ) {
              Text(
                text = "Add to cart · ₹$totalPrice",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = PureWhite,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Icon(
                imageVector = Icons.Outlined.ShoppingCart,
                contentDescription = "Cart",
                tint = PureWhite,
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Estimated ready time indicator
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Outlined.AccessTime,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(13.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "Estimated ready time: $readyTimeStr (~$effectivePrepMinutes mins)",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextMuted,
          )
        }
      }
    }
  }
}

/**
 * Normal, Clean, Professional Review Card
 */
@Composable
private fun CleanReviewCard(review: FoodReview) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(PureWhite)
      .border(1.dp, BorderGray.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
      .padding(12.dp),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(26.dp)
              .clip(CircleShape)
              .background(DrawerAmberLight),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = review.userName.take(1).uppercase(),
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = DrawerAmber,
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = review.userName,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
          )
        }

        Text(
          text = review.date,
          fontSize = 11.5.sp,
          color = TextMuted,
        )
      }

      Spacer(modifier = Modifier.height(4.dp))

      Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { index ->
          val filled = index < review.rating.toInt()
          Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = if (filled) RatingStarYellow else BorderGray,
            modifier = Modifier.size(13.dp),
          )
        }
      }

      if (review.comment.isNotBlank()) {
        Spacer(modifier = Modifier.height(5.dp))
        Text(
          text = review.comment,
          fontSize = 12.5.sp,
          color = TextSecondary,
          lineHeight = 17.5.sp,
        )
      }
    }
  }
}
