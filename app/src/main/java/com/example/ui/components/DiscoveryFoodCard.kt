// DiscoveryFoodCard: Reusable Jetpack Compose UI component
package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FoodItem
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RatingStarYellow
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted

/**
 * Modern, consistent horizontal food discovery card for Empty Cart sections:
 *  1. Suggested For You
 *  2. Recently Viewed
 *  3. Recently Ordered
 *  4. Your Favourites
 */
@Composable
fun DiscoveryFoodCard(
  foodItem: FoodItem,
  onAddToCart: (FoodItem) -> Unit,
  onItemClick: (FoodItem) -> Unit,
  modifier: Modifier = Modifier,
) {
  val isAvailable = foodItem.isAvailable

  Card(
    modifier = modifier
      .width(165.dp)
      .height(218.dp)
      .clickable { onItemClick(foodItem) },
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = PureWhite),
    border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.65f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(115.dp),
      ) {
        FoodImagePlaceholder(
          itemName = foodItem.name,
          imageUrl = foodItem.imageUrl,
          modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
        )

        // Rating pill top-right
        Box(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(7.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(PureWhite.copy(alpha = 0.94f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Star,
              contentDescription = "Rating",
              tint = RatingStarYellow,
              modifier = Modifier.size(11.dp),
            )
            Spacer(modifier = Modifier.width(2.5.dp))
            Text(
              text = "${foodItem.rating ?: 4.8}",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = BlackPrimary,
            )
          }
        }

        // Sold out badge if unavailable
        if (!isAvailable) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
          ) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
              Text(
                text = "Unavailable",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = PureWhite,
              )
            }
          }
        }
      }

      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        Column {
          Text(
            text = foodItem.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = BlackPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = foodItem.prepTime.ifBlank { foodItem.category.displayName },
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        // Price and Add Button Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "₹${foodItem.price}",
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextDark,
          )

          if (isAvailable) {
            Box(
              modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(DrawerAmber)
                .clickable { onAddToCart(foodItem) },
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add to cart",
                tint = PureWhite,
                modifier = Modifier.size(16.dp),
              )
            }
          }
        }
      }
    }
  }
}
