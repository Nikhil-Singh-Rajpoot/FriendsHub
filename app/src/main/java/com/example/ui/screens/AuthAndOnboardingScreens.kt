package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.model.ActivityCategory
import com.example.ui.auth.GoogleSignInHelper
import com.example.ui.components.ProfileAvatar
import com.example.ui.components.SafetyNoticeCard
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.DeepPurple
import com.example.util.ImageUtils
import com.example.util.LocationPrivacy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun AuthWelcomeScreen(
  onAuthSuccess: () -> Unit,
  onOpenPrivacy: () -> Unit,
  onOpenTerms: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }
  var isLoading by remember { mutableStateOf(false) }
  var errorMsg by remember { mutableStateOf<String?>(null) }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(
          Brush.verticalGradient(
            listOf(
              MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
              MaterialTheme.colorScheme.background,
            )
          )
        )
        .statusBarsPadding()
        .navigationBarsPadding(),
  ) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .widthIn(max = 560.dp)
          .align(Alignment.Center)
          .verticalScroll(rememberScrollState())
          .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Image(
          painter = painterResource(id = R.drawable.ic_friendshub_logo),
          contentDescription = "FriendsHub logo",
          modifier = Modifier.size(44.dp).clip(MaterialTheme.shapes.small),
          contentScale = ContentScale.Crop,
        )
        Column {
          Text(
            text = "FriendsHub",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = "Find a friend. Share a moment.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))
      Image(
        painter = painterResource(id = R.drawable.img_welcome_friends),
        contentDescription = "Friends enjoying chai and conversation at a cafe",
        contentScale = ContentScale.Crop,
        modifier =
          Modifier.fillMaxWidth()
            .height(210.dp)
            .clip(MaterialTheme.shapes.large),
      )

      Spacer(modifier = Modifier.height(22.dp))
      Text(
        text = "Real-world company, when you want it.",
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onBackground,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = "Connect with verified 18+ adults nearby for chai, coffee, food, a walk, or good conversation — strictly friendship, never dating.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(modifier = Modifier.height(18.dp))
      FeatureTrustRow(
        icon = Icons.Rounded.VerifiedUser,
        title = "18+ Verified & Privacy-First",
        subtitle = "No phone numbers or exact GPS coordinates are ever shared.",
      )
      Spacer(modifier = Modifier.height(10.dp))
      FeatureTrustRow(
        icon = Icons.Rounded.PhoneInTalk,
        title = "Private In-App Voice Calls",
        subtitle = "Talk securely inside FriendsHub before meeting in public.",
      )
      Spacer(modifier = Modifier.height(10.dp))
      FeatureTrustRow(
        icon = Icons.Rounded.Coffee,
        title = "Public Cafes & Meetups",
        subtitle = "Plan mutual meetups at trusted public spaces nearby.",
      )

      Spacer(modifier = Modifier.height(24.dp))
      if (errorMsg != null) {
        Surface(
          color = MaterialTheme.colorScheme.errorContainer,
          shape = MaterialTheme.shapes.small,
          modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        ) {
          Text(
            text = errorMsg!!,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(12.dp),
          )
        }
      }

      Button(
        onClick = {
          errorMsg = null
          isLoading = true
          GoogleSignInHelper.startInteractiveSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
              isLoading = false
              onAuthSuccess()
            },
            onAuthError = { msg ->
              isLoading = false
              errorMsg = msg
            },
            onAuthCancelled = { isLoading = false },
            scope = scope,
          )
        },
        enabled = !isLoading,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = DeepPurple),
        modifier = Modifier.fillMaxWidth().height(54.dp).testTag("google_sign_in_button"),
      ) {
        if (isLoading) {
          CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = Color.White,
            strokeWidth = 2.dp,
          )
        } else {
          Text("Continue with Google", style = MaterialTheme.typography.labelLarge)
        }
      }

      Spacer(modifier = Modifier.height(14.dp))
      Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(onClick = onOpenPrivacy) {
          Text("Privacy Policy", style = MaterialTheme.typography.labelMedium)
        }
        Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onOpenTerms) {
          Text("Terms & Safety", style = MaterialTheme.typography.labelMedium)
        }
      }
    }
  }
}

@Composable
private fun FeatureTrustRow(icon: ImageVector, title: String, subtitle: String) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = MaterialTheme.shapes.medium,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier.size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
      ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
          subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingFlowScreen(
  defaultName: String,
  userEmail: String,
  onCompleteOnboarding: (
    displayName: String,
    phoneNote: String,
    dobMillis: Long,
    gender: String,
    bio: String,
    cityArea: String,
    photoData: String,
    interests: List<String>,
  ) -> Unit,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var step by remember { mutableIntStateOf(0) }
  var fullName by remember { mutableStateOf(defaultName) }
  var phoneNote by remember { mutableStateOf("") }
  var dobMillis by remember { mutableLongStateOf(0L) }
  var showDatePicker by remember { mutableStateOf(false) }
  var gender by remember { mutableStateOf("") }
  var cityArea by remember { mutableStateOf("Sector 29, Gurugram") }
  var bio by remember { mutableStateOf("") }
  var photoData by remember { mutableStateOf("") }
  var selectedInterests by remember { mutableStateOf(setOf(ActivityCategory.TEA_COFFEE.label)) }
  var locationGranted by remember { mutableStateOf(false) }
  var errorText by remember { mutableStateOf<String?>(null) }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        scope.launch {
          val encoded = ImageUtils.encodePickerUriToDataString(context, uri)
          if (encoded != null) {
            photoData = encoded
          } else {
            errorText = "Could not process selected photo. Try another image."
          }
        }
      }
    }

  val locationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
      locationGranted =
        result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
          result[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

  val datePickerState = rememberDatePickerState()
  if (showDatePicker) {
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            datePickerState.selectedDateMillis?.let { dobMillis = it }
            showDatePicker = false
          },
        ) {
          Text("Confirm")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
      },
    ) {
      DatePicker(state = datePickerState)
    }
  }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .statusBarsPadding()
        .navigationBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(20.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      if (step > 0) {
        IconButton(onClick = { step -= 1; errorText = null }) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
      } else {
        Spacer(modifier = Modifier.size(48.dp))
      }
      Text(
        text = "Step ${step + 1} of 4",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
      )
      TextButton(onClick = onSignOut) { Text("Sign Out") }
    }

    LinearProgressIndicator(
      progress = { (step + 1) / 4f },
      modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.small),
    )

    Spacer(modifier = Modifier.height(20.dp))

    when (step) {
      0 -> {
        Text("Create your FriendsHub account", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          "FriendsHub is strictly for adults 18+. Your private details are never shown to other users.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(18.dp))

        OutlinedTextField(
          value = fullName,
          onValueChange = { fullName = it },
          label = { Text("Full Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("onboarding_name_input"),
        )
        Spacer(modifier = Modifier.height(12.dp))

        val dobLabel =
          if (dobMillis > 0L) {
            val age = LocationPrivacy.calculateAge(dobMillis)
            val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(dobMillis))
            "$fmt ($age years)"
          } else {
            "Select Date of Birth (18+ required)"
          }

        OutlinedButton(
          onClick = { showDatePicker = true },
          modifier = Modifier.fillMaxWidth().height(54.dp).testTag("onboarding_dob_button"),
          shape = MaterialTheme.shapes.small,
        ) {
          Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(dobLabel)
        }
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
          value = cityArea,
          onValueChange = { cityArea = it },
          label = { Text("City / Neighborhood (e.g. Sector 29, Gurugram)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("onboarding_city_input"),
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
          value = phoneNote,
          onValueChange = { phoneNote = it.filter { ch -> ch.isDigit() || ch == '+' }.take(14) },
          label = { Text("Mobile Number (Optional, kept private)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text("Gender (Optional)", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf("Woman", "Man", "Non-binary", "Prefer not to say").forEach { g ->
            FilterChip(
              selected = gender == g,
              onClick = { gender = if (gender == g) "" else g },
              label = { Text(g) },
            )
          }
        }
      }

      1 -> {
        Text("Set up your friendly profile", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          "Add a warm smile and a short bio so people nearby know what kind of company you enjoy.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))

        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          ProfileAvatar(photoData = photoData, name = fullName.ifBlank { "F" }, size = 96.dp)
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedButton(
            onClick = {
              photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
          ) {
            Icon(Icons.Rounded.AddAPhoto, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (photoData.isBlank()) "Choose Photo" else "Change Photo")
          }
        }
        Spacer(modifier = Modifier.height(18.dp))

        OutlinedTextField(
          value = bio,
          onValueChange = { if (it.length <= 280) bio = it },
          label = { Text("Short Bio (e.g. Love evening chai & exploring cafes)") },
          minLines = 3,
          maxLines = 5,
          modifier = Modifier.fillMaxWidth().testTag("onboarding_bio_input"),
        )
        Text(
          "${bio.length}/280",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
        )
      }

      2 -> {
        Text("What would you like to do with a new friend?", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          "Select one or more activities you enjoy.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(18.dp))

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          ActivityCategory.allLabels.forEach { label ->
            val selected = label in selectedInterests
            FilterChip(
              selected = selected,
              onClick = {
                selectedInterests =
                  if (selected && selectedInterests.size > 1) selectedInterests - label
                  else selectedInterests + label
              },
              label = { Text(label, modifier = Modifier.padding(vertical = 6.dp)) },
              leadingIcon =
                if (selected) {
                  { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                  selectedLabelColor = MaterialTheme.colorScheme.primary,
                ),
            )
          }
        }
      }

      3 -> {
        Text("Location & Privacy Consent", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        SafetyNoticeCard(
          title = "Your exact location stays private",
          message = "FriendsHub only uses coarse area coordinates while your availability toggle is ON. Turn it OFF anytime to stop location sharing immediately.",
        )
        Spacer(modifier = Modifier.height(16.dp))

        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              imageVector = Icons.Rounded.LocationOn,
              contentDescription = null,
              tint = CobaltBlue,
              modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = if (locationGranted) "Location permission granted!" else "Enable nearby distance discovery",
              style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Used only to calculate approximate KM distance when you switch Available ON.",
              style = MaterialTheme.typography.bodyMedium,
              textAlign = TextAlign.Center,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(
              onClick = {
                locationPermissionLauncher.launch(
                  arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                  )
                )
              },
            ) {
              Icon(Icons.Rounded.Lock, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text(if (locationGranted) "Permission Ready" else "Allow Location Access")
            }
          }
        }
      }
    }

    if (errorText != null) {
      Spacer(modifier = Modifier.height(12.dp))
      Text(
        text = errorText!!,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )
    }

    Spacer(modifier = Modifier.height(24.dp))
    Button(
      onClick = {
        errorText = null
        when (step) {
          0 -> {
            if (fullName.trim().length < 2) {
              errorText = "Please enter your full name."
            } else if (dobMillis <= 0L) {
              errorText = "Please select your date of birth."
            } else if (!LocationPrivacy.isAdult(dobMillis)) {
              errorText = "You must be at least 18 years old to use FriendsHub."
            } else if (cityArea.trim().length < 2) {
              errorText = "Please enter your city or neighborhood."
            } else {
              step = 1
            }
          }
          1 -> step = 2
          2 -> {
            if (selectedInterests.isEmpty()) {
              errorText = "Please choose at least one activity."
            } else {
              step = 3
            }
          }
          3 -> {
            onCompleteOnboarding(
              fullName.trim(),
              phoneNote.trim(),
              dobMillis,
              gender,
              bio.trim(),
              cityArea.trim(),
              photoData,
              selectedInterests.toList(),
            )
          }
        }
      },
      shape = MaterialTheme.shapes.medium,
      modifier = Modifier.fillMaxWidth().height(54.dp).testTag("onboarding_next_button"),
    ) {
      Text(if (step < 3) "Continue" else "Finish & Enter FriendsHub")
    }
  }
}
