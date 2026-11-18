// FoodReviewsBottomSheet: Reusable Jetpack Compose UI component
package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FoodItem
import com.example.data.FoodReview
import com.example.data.api.ApiClient
import com.example.data.api.MongoRepository
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DrawerAmber
import com.example.ui.theme.DrawerAmberDark
import com.example.ui.theme.DrawerAmberLight
import com.example.ui.theme.CardSurface
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RatingStarYellow
import com.example.ui.theme.SoftGray
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarmCream
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodReviewsBottomSheet(
  foodItem: FoodItem,
  onDismiss: () -> Unit,
  studentName: String = "Student",
  sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
  val scope = rememberCoroutineScope()
  val mongoRepo = remember { MongoRepository() }

  var isLoading by remember { mutableStateOf(true) }
  var averageRating by remember { mutableDoubleStateOf(foodItem.rating ?: 4.5) }
  var totalReviewsCount by remember { mutableIntStateOf(foodItem.reviewCount) }
  val reviewsList = remember { mutableStateListOf<FoodReview>() }

  // Review submission form state
  var showAddReviewForm by remember { mutableStateOf(false) }
  var userRatingInput by remember { mutableIntStateOf(5) }
  var userCommentInput by remember { mutableStateOf("") }
  var isSubmittingReview by remember { mutableStateOf(false) }
  var submitErrorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(foodItem.id) {
    isLoading = true
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
            userName = r.userName.ifBlank { "Campus Foodie" },
            rating = r.rating,
            comment = r.comment,
            date = formattedDate,
          )
        )
      }
    }.onFailure {
      // Gracefully fall back to local rating
      averageRating = foodItem.rating ?: 4.5
    }
    isLoading = false
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = WarmCream,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    dragHandle = {
      Box(
        modifier = Modifier
          .padding(top = 12.dp, bottom = 8.dp)
          .size(width = 44.dp, height = 4.dp)
          .clip(CircleShape)
          .background(BorderGray.copy(alpha = 0.8f))
      )
    },
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 28.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = "Ratings & Reviews",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = TextDark,
          )
          Text(
            text = foodItem.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextMuted,
          )
        }

        IconButton(
          onClick = onDismiss,
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(PureWhite),
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = TextDark,
            modifier = Modifier.size(18.dp),
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(20.dp))
          .background(PureWhite)
          .border(1.dp, BorderGray.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
          .padding(16.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Row(verticalAlignment = Alignment.Bottom) {
              Text(
                text = String.format(Locale.US, "%.1f", averageRating),
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = TextDark,
              )
              Text(
                text = " / 5.0",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
                modifier = Modifier.padding(bottom = 4.dp),
              )
            }

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp),
              modifier = Modifier.padding(top = 2.dp),
            ) {
              val fullStars = averageRating.toInt()
              for (i in 1..5) {
                Icon(
                  imageVector = if (i <= fullStars) Icons.Filled.Star else Icons.Outlined.StarBorder,
                  contentDescription = null,
                  tint = if (i <= fullStars) RatingStarYellow else BorderGray,
                  modifier = Modifier.size(16.dp),
                )
              }
            }

            Text(
              text = if (totalReviewsCount > 0) "Based on $totalReviewsCount verified review${if (totalReviewsCount > 1) "s" else ""}" else "No verified reviews yet",
              fontSize = 12.sp,
              color = TextMuted,
              fontWeight = FontWeight.Medium,
              modifier = Modifier.padding(top = 4.dp),
            )
          }

          // "Write Review" button
          Button(
            onClick = { showAddReviewForm = !showAddReviewForm },
            colors = ButtonDefaults.buttonColors(containerColor = DrawerAmber),
            shape = RoundedCornerShape(14.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.RateReview,
              contentDescription = null,
              tint = PureWhite,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (showAddReviewForm) "Cancel" else "Review",
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = PureWhite,
            )
          }
        }
      }

      if (showAddReviewForm) {
        Spacer(modifier = Modifier.height(14.dp))
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PureWhite)
            .border(1.dp, DrawerAmber.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        ) {
          Column {
            Text(
              text = "Rate this dish",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = TextDark,
            )

            // Star selector
            Row(
              modifier = Modifier.padding(vertical = 8.dp),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              for (i in 1..5) {
                Icon(
                  imageVector = if (i <= userRatingInput) Icons.Filled.Star else Icons.Outlined.StarBorder,
                  contentDescription = "$i stars",
                  tint = if (i <= userRatingInput) RatingStarYellow else BorderGray,
                  modifier = Modifier
                    .size(30.dp)
                    .clickable { userRatingInput = i },
                )
              }
            }

            OutlinedTextField(
              value = userCommentInput,
              onValueChange = { userCommentInput = it; submitErrorMessage = null },
              placeholder = { Text("What did you like about this dish?", fontSize = 13.sp, color = TextMuted) },
              modifier = Modifier.fillMaxWidth(),
              maxLines = 3,
              shape = RoundedCornerShape(12.dp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DrawerAmber,
                unfocusedBorderColor = BorderGray,
              ),
            )

            if (submitErrorMessage != null) {
              Text(
                text = submitErrorMessage!!,
                color = Color(0xFFDC2626),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
              onClick = {
                if (userCommentInput.isBlank()) {
                  submitErrorMessage = "Please write a short review before submitting."
                  return@Button
                }
                isSubmittingReview = true
                scope.launch {
                  val res = mongoRepo.submitItemReview(
                    itemId = foodItem.id,
                    rating = userRatingInput.toDouble(),
                    comment = userCommentInput.trim(),
                    userName = studentName.ifBlank { "Campus Foodie" },
                  )
                  res.onSuccess {
                    averageRating = it.averageRating
                    totalReviewsCount = it.totalReviews
                    val newRev = FoodReview(
                      id = it.review?.id ?: "rev_local_${System.currentTimeMillis()}",
                      itemId = foodItem.id,
                      userName = studentName.ifBlank { "Campus Foodie" },
                      rating = userRatingInput.toDouble(),
                      comment = userCommentInput.trim(),
                      date = "Just now",
                    )
                    reviewsList.add(0, newRev)
                    userCommentInput = ""
                    showAddReviewForm = false
                  }.onFailure { err ->
                    submitErrorMessage = err.message ?: "Failed to submit review."
                  }
                  isSubmittingReview = false
                }
              },
              enabled = !isSubmittingReview,
              colors = ButtonDefaults.buttonColors(containerColor = DrawerAmber),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.align(Alignment.End),
            ) {
              if (isSubmittingReview) {
                CircularProgressIndicator(color = PureWhite, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
              } else {
                Text("Submit Review", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PureWhite)
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))
      HorizontalDivider(thickness = 0.8.dp, color = BorderGray.copy(alpha = 0.6f))
      Spacer(modifier = Modifier.height(12.dp))

      if (isLoading) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(color = DrawerAmber, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
        }
      } else if (reviewsList.isEmpty()) {
        // Clean empty state
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Box(
            modifier = Modifier
              .size(54.dp)
              .clip(CircleShape)
              .background(DrawerAmberLight),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Outlined.RateReview,
              contentDescription = null,
              tint = DrawerAmber,
              modifier = Modifier.size(28.dp),
            )
          }

          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = "No reviews yet",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Be the first campus foodie to share feedback on this item!",
            fontSize = 12.5.sp,
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.85f),
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 380.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          items(reviewsList, key = { it.id }) { review ->
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PureWhite)
                .border(0.8.dp, BorderGray.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(14.dp),
            ) {
              Column {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                      modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(DrawerAmberLight),
                      contentAlignment = Alignment.Center,
                    ) {
                      Text(
                        text = review.userName.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = DrawerAmber,
                      )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = review.userName,
                      fontSize = 13.5.sp,
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

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                  for (i in 1..5) {
                    Icon(
                      imageVector = if (i <= review.rating.toInt()) Icons.Filled.Star else Icons.Outlined.StarBorder,
                      contentDescription = null,
                      tint = if (i <= review.rating.toInt()) RatingStarYellow else BorderGray,
                      modifier = Modifier.size(13.dp),
                    )
                  }
                }

                if (review.comment.isNotBlank()) {
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = review.comment,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    color = TextDark,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
