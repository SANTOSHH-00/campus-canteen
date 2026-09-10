package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import kotlin.math.absoluteValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FoodItem
import com.example.data.PopularBadge
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.CardSurface
import com.example.ui.theme.DeliveryOrange
import com.example.ui.theme.DeliveryOrangeLight
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RatingStarYellow
import com.example.ui.theme.SoftGray
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted

/**
 * Clean styled image placeholder container that displays the item name clearly
 * or renders the real image asset when available.
 */
@Composable
fun FoodImagePlaceholder(
  itemName: String,
  modifier: Modifier = Modifier,
  imageUrl: String = "",
  rating: Double? = null,
  compact: Boolean = false,
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(14.dp))
      .background(Color(0xFFF3ECE4))
      .border(1.dp, BorderGray.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
    contentAlignment = Alignment.Center,
  ) {
    if (imageUrl.isNotBlank()) {
      val context = LocalContext.current
      val imageRequest = remember(imageUrl) {
        coil.request.ImageRequest.Builder(context)
          .data(imageUrl)
          .crossfade(true)
          .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
          .diskCachePolicy(coil.request.CachePolicy.ENABLED)
          .build()
      }
      coil.compose.AsyncImage(
        model = imageRequest,
        contentDescription = itemName,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    } else {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
      ) {
        Icon(
          imageVector = Icons.Default.Image,
          contentDescription = "Image Placeholder",
          tint = DeliveryOrange,
          modifier = Modifier.size(if (compact) 18.dp else 22.dp),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = itemName,
          fontSize = if (compact) 11.sp else 12.sp,
          fontWeight = FontWeight.ExtraBold,
          color = TextDark,
          textAlign = TextAlign.Center,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        if (!compact) {
          Text(
            text = "[Image Area]",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted,
          )
        }
      }
    }

    // Top Right Rating Badge (for Popular items)
    if (rating != null) {
      Box(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(top = 6.dp, end = 6.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(PureWhite)
          .border(1.dp, BorderGray, RoundedCornerShape(10.dp))
          .padding(horizontal = 6.dp, vertical = 2.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Star,
            contentDescription = "Rating Star",
            tint = RatingStarYellow,
            modifier = Modifier.size(12.dp),
          )
          Spacer(modifier = Modifier.width(3.dp))
          Text(
            text = rating.toString(),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextDark,
          )
        }
      }
    }
  }
}

/**
 * Hero Steak-Style Banner Card matching the uploaded banner:
 * - RoundedCornerShape(22.dp)
 * - Deep dark background (Color(0xFF141312))
 * - Script tag: "Premium" (Italic Serif gold text)
 * - Headline: "GOURMET BITES.\nPERFECTED." (Bold uppercase white text)
 * - Subtitle: "Handcrafted cuts, expertly grilled & prepared to perfection."
 * - Amber pill button: "ORDER SPECIAL ➔"
 * - Pagination dots at bottom center: ● ○ ○ ○
 * - Plated food image preview on right side
 */
data class FeaturedSpecialSlide(
  val scriptTag: String,
  val headline: String,
  val subtitle: String,
  val buttonText: String,
  val iconEmoji: String,
  val isSandwich: Boolean = false,
)

/**
 * Hero Steak-Style Banner Carousel matching the uploaded banner:
 * - RoundedCornerShape(22.dp)
 * - Deep dark background (Color(0xFF141312))
 * - Script tag: "Premium" / "Chef's Special" (Italic Serif gold text)
 * - Headline: "GOURMET BITES.\nPERFECTED." (Bold uppercase white text)
 * - Subtitle: "Handcrafted cuts, expertly grilled & prepared to perfection."
 * - Amber pill button: "ORDER SPECIAL ➔"
 * - Dynamic pagination dots at bottom center: ● ○ ○ ○
 * - Auto-advances every 3.5 seconds and swipeable while scrolling
 */
@Composable
fun FeaturedSpecialBannerCard(
  onOrderClick: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val slides = remember {
    listOf(
      FeaturedSpecialSlide(
        scriptTag = "Premium",
        headline = "GOURMET BITES.\nPERFECTED.",
        subtitle = "Handcrafted cuts, expertly grilled & prepared to perfection.",
        buttonText = "ORDER SPECIAL",
        iconEmoji = "🥩",
        isSandwich = false,
      ),
      FeaturedSpecialSlide(
        scriptTag = "Chef's Special",
        headline = "CAMPUS FEAST.\nCRAFTED FRESH.",
        subtitle = "Authentic spices, hot oven bakes & wholesome chef recipes.",
        buttonText = "EXPLORE MENU",
        iconEmoji = "🍱",
        isSandwich = false,
      ),
      FeaturedSpecialSlide(
        scriptTag = "Top Rated",
        headline = "SIZZLING GRILLS.\nBURSTING FLAVOR.",
        subtitle = "Juicy patties, melted cheddar & seasoned golden crusts.",
        buttonText = "TRY NOW",
        iconEmoji = "🍔",
        isSandwich = false,
      ),
      FeaturedSpecialSlide(
        scriptTag = "Quick Bites",
        headline = "SNACK CRAVINGS.\nREADY IN 5 MIN.",
        subtitle = "Crispy street favorites, masala blends & iced coolers.",
        buttonText = "ORDER SNACKS",
        iconEmoji = "🥟",
        isSandwich = false,
      ),
    )
  }

  val pagerState = rememberPagerState(pageCount = { slides.size })

  // Auto-advance smoothly every 3.8 seconds without interrupting user gestures
  LaunchedEffect(pagerState) {
    while (true) {
      delay(3800)
      if (!pagerState.isScrollInProgress) {
        val next = (pagerState.currentPage + 1) % slides.size
        pagerState.animateScrollToPage(
          page = next,
          animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        )
      }
    }
  }

  HorizontalPager(
    state = pagerState,
    pageSpacing = 12.dp,
    beyondViewportPageCount = 1,
    modifier = modifier.fillMaxWidth(),
  ) { pageIndex ->
    val slide = slides[pageIndex]

    // Elegant depth transition effect: smooth scale & alpha based on page offset
    val pageOffset = ((pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction).absoluteValue

    Card(
      modifier = Modifier
        .fillMaxWidth()
        .graphicsLayer {
          val pageScale = lerp(0.96f, 1.0f, 1f - pageOffset.coerceIn(0f, 1f))
          val pageAlpha = lerp(0.80f, 1.0f, 1f - pageOffset.coerceIn(0f, 1f))
          scaleX = pageScale
          scaleY = pageScale
          alpha = pageAlpha
        }
        .shadow(10.dp, RoundedCornerShape(22.dp)),
      shape = RoundedCornerShape(22.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xFF141312)),
      border = BorderStroke(1.dp, Color(0xFF2A2826)),
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { onOrderClick() }
          .padding(top = 20.dp, start = 20.dp, end = 20.dp, bottom = 16.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // Left Column: Text and CTA
          Column(
            modifier = Modifier
              .weight(1.15f)
              .padding(end = 12.dp),
          ) {
            Text(
              text = slide.scriptTag,
              fontSize = 18.sp,
              fontFamily = FontFamily.Serif,
              fontStyle = FontStyle.Italic,
              fontWeight = FontWeight.Normal,
              color = Color(0xFFDDA647),
              letterSpacing = 0.5.sp,
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
              text = slide.headline,
              fontSize = 21.sp,
              fontWeight = FontWeight.Black,
              color = PureWhite,
              letterSpacing = 0.3.sp,
              lineHeight = 25.sp,
            )

            Spacer(modifier = Modifier.height(7.dp))

            Text(
              text = slide.subtitle,
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium,
              color = PureWhite.copy(alpha = 0.82f),
              lineHeight = 16.sp,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Amber Pill Button
            Row(
              modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFBA7A18))
                .clickable { onOrderClick() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = slide.buttonText,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = PureWhite,
                letterSpacing = 0.4.sp,
              )
              Spacer(modifier = Modifier.width(7.dp))
              Box(
                modifier = Modifier
                  .size(19.dp)
                  .clip(CircleShape)
                  .background(PureWhite),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                  contentDescription = null,
                  tint = Color(0xFFBA7A18),
                  modifier = Modifier.size(12.dp),
                )
              }
            }
          }

          // Right Column: Food Image Preview (Enlarged box)
          Box(
            modifier = Modifier
              .weight(0.88f)
              .height(148.dp)
              .clip(RoundedCornerShape(18.dp))
              .background(Color(0xFF22201E)),
            contentAlignment = Alignment.Center,
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(slide.iconEmoji, fontSize = 48.sp)
              Spacer(modifier = Modifier.height(5.dp))
              Text(
                text = slide.scriptTag,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = PureWhite.copy(alpha = 0.75f),
              )
            }
          }
        }

        // Bottom Center: Dynamic 4 Pagination Dots that sync with pagerState!
        Row(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(top = 14.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          slides.indices.forEach { index ->
            val isCurrent = (pagerState.currentPage == index)
            Box(
              modifier = Modifier
                .size(width = if (isCurrent) 18.dp else 5.dp, height = 5.dp)
                .clip(if (isCurrent) RoundedCornerShape(3.dp) else CircleShape)
                .background(if (isCurrent) Color(0xFFBA7A18) else PureWhite.copy(alpha = 0.45f)),
            )
          }
        }
      }
    }
  }
}

/**
 * Quick Order Big Card (Side-scrolling Carousel Item):
 * - Large image placeholder area at top with prep badge
 * - Dish name, category/description, price
 * - Circular amber '+' button (identical to popular cards)
 */
@Composable
fun QuickOrderCard(
  foodItem: FoodItem,
  onAddToCart: (FoodItem) -> Unit,
  onCardClick: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier
      .width(260.dp)
      .height(270.dp)
      .clickable { onCardClick() },
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = PureWhite),
    border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.65f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
    ) {
      // Top: Big Image Placeholder
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(145.dp),
      ) {
        FoodImagePlaceholder(
          itemName = foodItem.name,
          imageUrl = foodItem.imageUrl,
          modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
        )

        // Top-left Prep Badge
        Box(
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.AccessTime,
              contentDescription = null,
              tint = PureWhite,
              modifier = Modifier.size(12.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = foodItem.prepTime,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = PureWhite,
            )
          }
        }
      }

      // Bottom Content: Name, Description, Price and Circular '+' Add Button
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        Column {
          Text(
            text = foodItem.name,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = BlackPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )

          Spacer(modifier = Modifier.height(2.dp))

          val desc = foodItem.description.ifBlank {
            foodItem.displayIngredients.take(3).joinToString(", ").ifBlank {
              foodItem.category.name.replace('_', ' ')
            }
          }
          Text(
            text = desc,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = TextMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp,
            modifier = Modifier.height(30.dp),
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "₹${foodItem.price}",
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            color = BlackPrimary,
          )

          // Circular Amber '+' Button (identical to popular card)
          Box(
            modifier = Modifier
              .size(36.dp)
              .shadow(2.dp, CircleShape)
              .clip(CircleShape)
              .background(DrawerAmber)
              .clickable { onAddToCart(foodItem) },
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Default.Add,
              contentDescription = "Add to Cart",
              tint = PureWhite,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }
    }
  }
}

/**
 * Popular Right Now Big Card (Full Width with Image Placeholder):
 * - Large image placeholder area at top with Bestseller/Chef's Pick/Popular badge and Star rating
 * - Dish name, description, orders count, price
 * - Circular amber '+' button
 */
@Composable
fun PopularCard(
  foodItem: FoodItem,
  onAddToCart: (FoodItem) -> Unit,
  onCardClick: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val badge = foodItem.popularBadge
  val badgeBgColor = when (badge) {
    PopularBadge.BESTSELLER -> Color(0xFFBA7A18)
    PopularBadge.CHEFS_PICK -> Color(0xFFC07B12)
    PopularBadge.POPULAR -> Color(0xFFA67119)
  }

  Card(
    modifier = modifier
      .fillMaxWidth()
      .clickable { onCardClick() },
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = PureWhite),
    border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.65f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
    ) {
      // Top: Big Image Placeholder with Badges
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(185.dp),
      ) {
        FoodImagePlaceholder(
          itemName = foodItem.name,
          imageUrl = foodItem.imageUrl,
          modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
        )

        // Top-left: Category Badge (BESTSELLER / CHEF'S PICK / POPULAR)
        Box(
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(10.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(badgeBgColor)
            .padding(horizontal = 9.dp, vertical = 4.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = badge.displayName,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Black,
            color = PureWhite,
            letterSpacing = 0.5.sp,
          )
        }

        // Top-right: Star Rating
        Box(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(PureWhite)
            .border(1.dp, BorderGray, RoundedCornerShape(10.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Star,
              contentDescription = "Rating",
              tint = RatingStarYellow,
              modifier = Modifier.size(13.dp),
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = "${foodItem.rating ?: 4.8}",
              fontSize = 12.sp,
              fontWeight = FontWeight.ExtraBold,
              color = BlackPrimary,
            )
          }
        }
      }

      // Bottom Content: Name, Description, Orders Count, Price and Circular '+' Add Button
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 12.dp),
      ) {
        Text(
          text = foodItem.name,
          fontSize = 16.5.sp,
          fontWeight = FontWeight.ExtraBold,
          color = BlackPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(3.dp))

        val desc = foodItem.description.ifBlank {
          foodItem.displayIngredients.take(3).joinToString(", ")
        }
        Text(
          text = desc,
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
          color = TextMuted,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          lineHeight = 16.sp,
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Rating and orders count text
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = RatingStarYellow,
            modifier = Modifier.size(12.dp),
          )
          Spacer(modifier = Modifier.width(3.dp))
          Text(
            text = "${foodItem.rating ?: 4.8}",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = BlackPrimary,
          )
          Text(
            text = " (${foodItem.ordersCount.ifBlank { "1.2K+" }} orders)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = TextMuted,
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "₹${foodItem.price}",
            fontSize = 17.5.sp,
            fontWeight = FontWeight.Black,
            color = BlackPrimary,
          )

          // Circular Amber '+' Button (identical to quick order card)
          Box(
            modifier = Modifier
              .size(36.dp)
              .shadow(2.dp, CircleShape)
              .clip(CircleShape)
              .background(DrawerAmber)
              .clickable { onAddToCart(foodItem) },
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Default.Add,
              contentDescription = "Add to Cart",
              tint = PureWhite,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }
    }
  }
}


/**
 * Ready In < 10 Minutes Card (Image 2 - Row 3):
 * - Horizontal compact card for 2-column or list display
 */
@Composable
fun ReadyIn10Card(
  foodItem: FoodItem,
  onAddToCart: (FoodItem) -> Unit,
  onCardClick: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .height(98.dp)
      .clickable { onCardClick() },
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = CardSurface),
    border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.6f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Enlarged Image Area (Prominent & Easy to Identify)
      FoodImagePlaceholder(
        itemName = foodItem.name,
        imageUrl = foodItem.imageUrl,
        compact = false,
        modifier = Modifier
          .size(78.dp)
          .clip(RoundedCornerShape(12.dp)),
      )

      Spacer(modifier = Modifier.width(12.dp))

      // Details Column
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = foodItem.name,
          fontSize = 14.5.sp,
          fontWeight = FontWeight.ExtraBold,
          color = TextDark,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.AccessTime,
            contentDescription = "Time",
            tint = TextMuted,
            modifier = Modifier.size(12.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = foodItem.prepTime,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted,
          )
        }

        Text(
          text = "₹${foodItem.price}",
          fontSize = 15.sp,
          fontWeight = FontWeight.ExtraBold,
          color = BlackPrimary,
        )
      }

      Spacer(modifier = Modifier.width(8.dp))

      // Amber '+' Button
      Box(
        modifier = Modifier
          .size(34.dp)
          .shadow(1.dp, CircleShape)
          .clip(CircleShape)
          .background(DrawerAmber)
          .clickable { onAddToCart(foodItem) },
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Default.Add,
          contentDescription = "Add to cart",
          tint = PureWhite,
          modifier = Modifier.size(20.dp),
        )
      }
    }
  }
}

/**
 * Modern Redesigned "YOUR USUAL" Card:
 * - Appears only when an item has been ordered >= 3 times
 * - Has a Cross (X) icon to minimize / dismiss
 * - Clean, structured layout with food thumbnail, price, count badge and reorder button
 */
@Composable
fun YourUsualBanner(
  onReorder: () -> Unit,
  onDismiss: () -> Unit,
  usualInfo: com.example.ui.state.UsualOrderInfo? = null,
  modifier: Modifier = Modifier,
) {
  val itemName = usualInfo?.itemName ?: "Veg Sandwich + Coffee"
  val orderCount = usualInfo?.count ?: 4
  val price = usualInfo?.price ?: 90

  Card(
    modifier = modifier
      .fillMaxWidth()
      .shadow(3.dp, RoundedCornerShape(18.dp)),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = PureWhite),
    border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.7f)),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .background(PureWhite)
        .padding(horizontal = 15.dp, vertical = 13.dp),
    ) {
      // Top Row: Badge + Frequency + Cross (X) icon to minimize
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(24.dp)
              .clip(CircleShape)
              .background(Color(0xFFFFF7ED)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Default.Favorite,
              contentDescription = null,
              tint = DrawerAmber,
              modifier = Modifier.size(13.dp),
            )
          }
          Spacer(modifier = Modifier.width(7.dp))
          Text(
            text = "YOUR USUAL",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = BlackPrimary,
            letterSpacing = 0.5.sp,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(Color(0xFFF3ECE4))
              .padding(horizontal = 6.dp, vertical = 2.dp),
          ) {
            Text(
              text = "Ordered ${orderCount}x this week",
              fontSize = 9.5.sp,
              fontWeight = FontWeight.Bold,
              color = TextDark,
            )
          }
        }

        // Cross (X) Icon to Minimize / Dismiss
        Box(
          modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(SoftGray)
            .clickable { onDismiss() },
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Dismiss",
            tint = TextMuted,
            modifier = Modifier.size(14.dp),
          )
        }
      }

      Spacer(modifier = Modifier.height(11.dp))

      // Bottom Row: Food Info + Price + Reorder Button
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f),
        ) {
          FoodImagePlaceholder(
            itemName = itemName,
            compact = true,
            modifier = Modifier.size(44.dp),
          )
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = itemName,
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = TextDark,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "₹$price",
              fontSize = 15.sp,
              fontWeight = FontWeight.ExtraBold,
              color = BlackPrimary,
            )
          }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // High-contrast Black Reorder Button matching Dashboard theme
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BlackPrimary)
            .clickable { onReorder() }
            .padding(horizontal = 15.dp, vertical = 8.5.dp),
          contentAlignment = Alignment.Center,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = null,
              tint = PureWhite,
              modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Reorder",
              fontSize = 12.5.sp,
              fontWeight = FontWeight.ExtraBold,
              color = PureWhite,
            )
          }
        }
      }
    }
  }
}
