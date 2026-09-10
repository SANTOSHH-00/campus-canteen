package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BlackPrimary
import com.example.ui.theme.SoftGray

@Composable
fun TopBar(
  modifier: Modifier = Modifier,
  onMenuClick: () -> Unit = {},
  onProfileClick: () -> Unit = {},
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Menu icon button
    Box(
      modifier =
        Modifier
          .size(44.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(SoftGray)
          .clickable(role = Role.Button, onClick = onMenuClick)
          .testTag("top_menu_button"),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Default.Menu,
        contentDescription = "Canteen Navigation Menu",
        tint = BlackPrimary,
        modifier = Modifier.size(22.dp),
      )
    }

    // Profile icon button
    Box(
      modifier =
        Modifier
          .size(44.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(SoftGray)
          .clickable(role = Role.Button, onClick = onProfileClick)
          .testTag("top_profile_button"),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Outlined.Person,
        contentDescription = "User Profile",
        tint = BlackPrimary,
        modifier = Modifier.size(22.dp),
      )
    }
  }
}
