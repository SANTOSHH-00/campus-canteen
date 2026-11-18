// OwnerReviewsScreen: Jetpack Compose screen for OwnerReviews user interface
package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.DrawerAmberLight
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftGray
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream

private val StarGold = Color(0xFFF59E0B)

data class OwnerReviewItem(
  val id: String,
  val studentName: String,
  val block: String,
  val dishName: String,
  val rating: Int,
  val comment: String,
  val timeAgo: String,
  val verified: Boolean = true,
)

@Composable
fun OwnerReviewsScreen(
  canteenName: String = "Campus Canteen",
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedFilter by remember { mutableStateOf("All") }

  val sampleReviews = remember {
    listOf(
      OwnerReviewItem("r1", "Aarav Sharma", "Block 26", "Special Thali", 5, "Hot, fresh, and served fast during the 1 PM rush. Truly delicious!", "15m ago"),
      OwnerReviewItem("r2", "Priya Verma", "Block 14", "Paneer Butter Masala", 5, "The paneer was soft and buttery. Best lunch on campus.", "1h ago"),
      OwnerReviewItem("r3", "Karthik R.", "Block 28", "Masala Dosa", 4, "Crispy and authentic sambar. Sambar was hot. Would definitely order again.", "3h ago"),
      OwnerReviewItem("r4", "Sneha Patel", "Block 22", "Cold Coffee", 5, "Perfect sweetness and thick texture. Great refresher between classes.", "5h ago"),
      OwnerReviewItem("r5", "Rohan Mehta", "Block 33", "Veg Hakka Noodles", 4, "Good portion size and well seasoned. Picked up right on time.", "Yesterday"),
      OwnerReviewItem("r6", "Ananya Das", "Block 19", "Chole Bhature", 5, "Bhature was fluffy and not too oily. Loved the green chutney.", "Yesterday"),
    )
  }

  val filterTabs = listOf("All", "5 Stars", "4 Stars", "3 Stars", "Recent")

  val filteredReviews = remember(selectedFilter, sampleReviews) {
    when (selectedFilter) {
      "5 Stars" -> sampleReviews.filter { it.rating == 5 }
      "4 Stars" -> sampleReviews.filter { it.rating == 4 }
      "3 Stars" -> sampleReviews.filter { it.rating == 3 }
      else -> sampleReviews
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(WarmCream)
      .statusBarsPadding()
      .navigationBarsPadding()
      .testTag("owner_reviews_screen"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(PureWhite)
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack, modifier = Modifier.testTag("owner_reviews_back_button")) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "Back",
          tint = TextDark,
          modifier = Modifier.size(22.dp),
        )
      }
      Spacer(Modifier.width(6.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Reviews & Feedback",
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = TextDark,
        )
        Text(
          text = canteenName,
          fontSize = 12.sp,
          color = TextMuted,
        )
      }
    }

    HorizontalDivider(thickness = 0.8.dp, color = BorderGray.copy(alpha = 0.6f))

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .weight(1f)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Spacer(Modifier.height(10.dp))
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = PureWhite),
          border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.7f)),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier.width(100.dp),
            ) {
              Text(
                text = "4.8",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextDark,
              )
              Row {
                repeat(5) {
                  Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = StarGold,
                    modifier = Modifier.size(16.dp),
                  )
                }
              }
              Spacer(Modifier.height(4.dp))
              Text(
                text = "128 ratings",
                fontSize = 11.5.sp,
                color = TextMuted,
              )
            }

            Spacer(Modifier.width(16.dp))

            // Rating breakdown bars
            Column(modifier = Modifier.weight(1f)) {
              RatingBarRow(stars = "5★", progress = 0.82f, count = "105")
              RatingBarRow(stars = "4★", progress = 0.12f, count = "16")
              RatingBarRow(stars = "3★", progress = 0.04f, count = "5")
              RatingBarRow(stars = "2★", progress = 0.01f, count = "1")
              RatingBarRow(stars = "1★", progress = 0.01f, count = "1")
            }
          }
        }
      }

      item {
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          items(filterTabs) { tab ->
            val isSelected = tab == selectedFilter
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (isSelected) DrawerAmber else PureWhite)
                .border(1.dp, if (isSelected) DrawerAmber else BorderGray, RoundedCornerShape(20.dp))
                .clickable { selectedFilter = tab }
                .padding(horizontal = 14.dp, vertical = 7.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = tab,
                fontSize = 12.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) PureWhite else TextDark,
              )
            }
          }
        }
      }

      if (filteredReviews.isEmpty()) {
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(40.dp),
            contentAlignment = Alignment.Center,
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(
                imageVector = Icons.Outlined.RateReview,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(42.dp),
              )
              Spacer(Modifier.height(8.dp))
              Text(
                text = "No reviews in this category",
                fontSize = 14.sp,
                color = TextMuted,
              )
            }
          }
        }
      } else {
        items(filteredReviews, key = { it.id }) { review ->
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.6f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Box(
                    modifier = Modifier
                      .size(34.dp)
                      .clip(CircleShape)
                      .background(DrawerAmberLight),
                    contentAlignment = Alignment.Center,
                  ) {
                    Text(
                      text = review.studentName.take(1).uppercase(),
                      fontSize = 14.sp,
                      fontWeight = FontWeight.Bold,
                      color = DrawerAmber,
                    )
                  }
                  Spacer(Modifier.width(10.dp))
                  Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(
                        text = review.studentName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark,
                      )
                      if (review.verified) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                          imageVector = Icons.Filled.CheckCircle,
                          contentDescription = "Verified Student",
                          tint = Color(0xFF16A34A),
                          modifier = Modifier.size(13.dp),
                        )
                      }
                    }
                    Text(
                      text = review.block,
                      fontSize = 11.5.sp,
                      color = TextMuted,
                    )
                  }
                }
                Text(
                  text = review.timeAgo,
                  fontSize = 11.5.sp,
                  color = TextMuted,
                )
              }

              Spacer(Modifier.height(8.dp))

              Row(verticalAlignment = Alignment.CenterVertically) {
                Row {
                  repeat(5) { i ->
                    Icon(
                      imageVector = if (i < review.rating) Icons.Filled.Star else Icons.Outlined.Star,
                      contentDescription = null,
                      tint = if (i < review.rating) StarGold else Color(0xFFD1D5DB),
                      modifier = Modifier.size(14.dp),
                    )
                  }
                }
                Spacer(Modifier.width(8.dp))
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFF3F4F6))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                  Text(
                    text = review.dishName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark,
                  )
                }
              }

              Spacer(Modifier.height(8.dp))

              Text(
                text = review.comment,
                fontSize = 13.5.sp,
                color = TextDark,
                lineHeight = 18.sp,
              )
            }
          }
        }
      }

      item {
        Spacer(Modifier.height(24.dp))
      }
    }
  }
}

@Composable
private fun RatingBarRow(stars: String, progress: Float, count: String) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.padding(vertical = 1.5.dp),
  ) {
    Text(
      text = stars,
      fontSize = 11.sp,
      fontWeight = FontWeight.Medium,
      color = TextMuted,
      modifier = Modifier.width(22.dp),
    )
    Spacer(Modifier.width(6.dp))
    LinearProgressIndicator(
      progress = { progress },
      modifier = Modifier
        .weight(1f)
        .height(6.dp)
        .clip(RoundedCornerShape(3.dp)),
      color = StarGold,
      trackColor = Color(0xFFF3F4F6),
    )
    Spacer(Modifier.width(8.dp))
    Text(
      text = count,
      fontSize = 11.sp,
      color = TextMuted,
      modifier = Modifier.width(24.dp),
    )
  }
}

