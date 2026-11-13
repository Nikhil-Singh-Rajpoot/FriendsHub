package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.DeepPurple
import com.example.ui.theme.DeepPurpleContainer
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnline
import com.example.util.ImageUtils

@Composable
fun ProfileAvatar(
  photoData: String,
  name: String,
  size: Dp = 56.dp,
  isVerified: Boolean = false,
  isOnline: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val decodedBitmap = remember(photoData) { ImageUtils.decodePhotoData(photoData) }
  val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "F"

  Box(modifier = modifier.size(size)) {
    if (decodedBitmap != null) {
      Image(
        bitmap = decodedBitmap.asImageBitmap(),
        contentDescription = "$name profile photo",
        contentScale = ContentScale.Crop,
        modifier =
          Modifier.size(size)
            .clip(CircleShape)
            .border(2.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape),
      )
    } else {
      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier.size(size)
            .clip(CircleShape)
            .background(
              Brush.linearGradient(
                listOf(DeepPurple, CobaltBlue),
              )
            ),
      ) {
        Text(
          text = initial,
          style =
            if (size >= 72.dp) MaterialTheme.typography.headlineMedium
            else MaterialTheme.typography.titleLarge,
          color = Color.White,
          fontWeight = FontWeight.Bold,
        )
      }
    }

    if (isOnline) {
      Box(
        modifier =
          Modifier.size((size * 0.24f).coerceAtLeast(12.dp))
            .align(Alignment.BottomEnd)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(2.dp)
            .clip(CircleShape)
            .background(EmeraldOnline),
      )
    } else if (isVerified) {
      Icon(
        imageVector = Icons.Rounded.Verified,
        contentDescription = "Verified member",
        tint = CobaltBlue,
        modifier =
          Modifier.size((size * 0.3f).coerceAtLeast(16.dp))
            .align(Alignment.BottomEnd)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(1.dp),
      )
    }
  }
}

@Composable
fun StatusPill(
  text: String,
  containerColor: Color = DeepPurpleContainer,
  contentColor: Color = DeepPurple,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = containerColor,
    shape = RoundedCornerShape(50),
    modifier = modifier,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelMedium,
      color = contentColor,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )
  }
}

@Composable
fun SafetyNoticeCard(
  title: String,
  message: String,
  modifier: Modifier = Modifier,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    shape = MaterialTheme.shapes.medium,
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Icon(
        imageVector = Icons.Rounded.Shield,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(22.dp),
      )
      Spacer(modifier = Modifier.width(10.dp))
      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
fun EmptyIllustrationCard(
  title: String,
  subtitle: String,
  showIllustration: Boolean = true,
  modifier: Modifier = Modifier,
  action: @Composable (() -> Unit)? = null,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = MaterialTheme.shapes.large,
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      if (showIllustration) {
        Image(
          painter = painterResource(id = R.drawable.img_empty_nearby),
          contentDescription = title,
          contentScale = ContentScale.Crop,
          modifier =
            Modifier.fillMaxWidth()
              .height(160.dp)
              .clip(MaterialTheme.shapes.medium),
        )
        Spacer(modifier = Modifier.height(16.dp))
      }
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (action != null) {
        Spacer(modifier = Modifier.height(16.dp))
        action()
      }
    }
  }
}

@Composable
fun LiveBadge() {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    modifier =
      Modifier.clip(RoundedCornerShape(50))
        .background(EmeraldContainer)
        .padding(horizontal = 10.dp, vertical = 4.dp),
  ) {
    Icon(
      imageVector = Icons.Rounded.CheckCircle,
      contentDescription = null,
      tint = EmeraldOnline,
      modifier = Modifier.size(14.dp),
    )
    Text(
      text = "Available",
      style = MaterialTheme.typography.labelMedium,
      color = EmeraldOnline,
    )
  }
}
