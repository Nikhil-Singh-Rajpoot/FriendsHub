package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FaceRetouchingNatural
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.UserAccount
import com.example.data.model.UserProfile
import com.example.ui.components.SafetyNoticeCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.CobaltBlueContainer
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnline
import com.example.util.ImageUtils
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityVerificationScreen(
  account: UserAccount?,
  profile: UserProfile,
  onBack: () -> Unit,
  onPhoneVerified: (String) -> Unit,
  onSubmitLiveSelfie: (String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  // Level 1: Phone OTP State
  var phoneInput by remember(account?.verifiedPhoneNumber) {
    mutableStateOf(account?.verifiedPhoneNumber?.ifBlank { "+91" } ?: "+91")
  }
  var otpCode by remember { mutableStateOf("") }
  var verificationId by remember { mutableStateOf<String?>(null) }
  var isSendingOtp by remember { mutableStateOf(false) }
  var isVerifyingOtp by remember { mutableStateOf(false) }
  var phoneError by remember { mutableStateOf<String?>(null) }
  var phoneSuccessMessage by remember { mutableStateOf<String?>(null) }

  // Level 2: Real-Time Selfie State
  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  var showLiveCamera by remember { mutableStateOf(false) }
  var capturedSelfieData by remember(profile.selfiePhotoData) { mutableStateOf(profile.selfiePhotoData) }
  var isCapturingSelfie by remember { mutableStateOf(false) }
  var selfieError by remember { mutableStateOf<String?>(null) }

  val cameraPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasCameraPermission = granted
      if (granted) {
        showLiveCamera = true
        selfieError = null
      } else {
        selfieError = "Camera permission is required to capture a real-time selfie."
      }
    }

  fun linkPhoneCredential(credential: PhoneAuthCredential, normalizedPhone: String) {
    val currentUser = Firebase.auth.currentUser
    if (currentUser == null) {
      isVerifyingOtp = false
      phoneError = "Please sign in first."
      return
    }
    val existingPhone = currentUser.phoneNumber
    if (!existingPhone.isNullOrBlank() && existingPhone == normalizedPhone) {
      isVerifyingOtp = false
      phoneSuccessMessage = "Phone number verified via Firebase Auth."
      onPhoneVerified(normalizedPhone)
      return
    }
    currentUser
      .linkWithCredential(credential)
      .addOnSuccessListener {
        isVerifyingOtp = false
        phoneError = null
        phoneSuccessMessage = "Phone OTP verified!"
        onPhoneVerified(normalizedPhone)
      }
      .addOnFailureListener { exc ->
        // If phone provider was already linked or credential needs updatePhoneNumber
        currentUser
          .updatePhoneNumber(credential)
          .addOnSuccessListener {
            isVerifyingOtp = false
            phoneError = null
            phoneSuccessMessage = "Phone OTP verified!"
            onPhoneVerified(normalizedPhone)
          }
          .addOnFailureListener { updateErr ->
            isVerifyingOtp = false
            phoneError = updateErr.localizedMessage ?: exc.localizedMessage ?: "OTP verification failed."
          }
      }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Identity Verification") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
          .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Overall Status Card
      Card(
        colors =
          CardDefaults.cardColors(
            containerColor = if (profile.isVerified) CobaltBlueContainer else MaterialTheme.colorScheme.surface,
          ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().testTag("verification_overview_card"),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Verification Progress", style = MaterialTheme.typography.titleLarge)
            StatusPill(
              text = profile.verificationLevelLabel(),
              containerColor = if (profile.isVerified) EmeraldContainer else AmberContainer,
              contentColor = if (profile.isVerified) EmeraldOnline else AmberWarm,
            )
          }
          Text(
            text =
              "Only verified members can send or receive connection requests. Complete Level 1 (Phone OTP) and Level 2 (Real-Time Selfie) to earn your Blue Tick.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // -----------------------------------------------------------------------
      // LEVEL 1: Phone Number Verification with OTP
      // -----------------------------------------------------------------------
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().testTag("level1_phone_card"),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(
                imageVector = Icons.Rounded.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text("Level 1: Phone Number OTP", style = MaterialTheme.typography.titleMedium)
            }
            if (profile.phoneVerified || account?.phoneVerified == true) {
              StatusPill(
                text = "Verified",
                containerColor = EmeraldContainer,
                contentColor = EmeraldOnline,
              )
            } else {
              StatusPill(
                text = "Required",
                containerColor = AmberContainer,
                contentColor = AmberWarm,
              )
            }
          }

          if (profile.phoneVerified || account?.phoneVerified == true) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = EmeraldOnline)
              Text(
                text =
                  "Phone verified${account?.verifiedPhoneNumber?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""}. Your number is kept strictly private.",
                style = MaterialTheme.typography.bodyMedium,
              )
            }
          } else {
            Text(
              text = "Verify your mobile number via SMS OTP. Your phone number is never shown to other users.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
              value = phoneInput,
              onValueChange = { phoneInput = it; phoneError = null },
              label = { Text("Mobile Number (with country code)") },
              placeholder = { Text("+919876543210") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("level1_phone_input"),
            )

            Button(
              enabled = !isSendingOtp && phoneInput.trim().length >= 10,
              onClick = {
                val normalized = normalizePhoneE164(phoneInput)
                if (normalized == null) {
                  phoneError = "Enter a valid phone number starting with + and country code (e.g., +919876543210)."
                  return@Button
                }
                val activity = context.findActivity()
                if (activity == null) {
                  phoneError = "Unable to launch phone verification from this context."
                  return@Button
                }
                isSendingOtp = true
                phoneError = null
                phoneSuccessMessage = null

                val callbacks =
                  object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                      isSendingOtp = false
                      isVerifyingOtp = true
                      linkPhoneCredential(credential, normalized)
                    }

                    override fun onVerificationFailed(e: FirebaseException) {
                      isSendingOtp = false
                      val rawMsg = e.localizedMessage.orEmpty()
                      phoneError =
                        when {
                          rawMsg.contains("region", ignoreCase = true) ||
                            rawMsg.contains("BILLING_NOT_ENABLED", ignoreCase = true) ->
                            "Firebase blocked SMS for this country or plan. Enable your country under Firebase Console → Authentication → Settings → SMS region policy (real SMS requires the Blaze plan; Firebase Test Phone Numbers work free)."
                          rawMsg.contains("operation is not allowed", ignoreCase = true) ||
                            rawMsg.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) ->
                            "Phone Sign-In is disabled in Firebase Console. Enable Phone under Authentication → Sign-in method."
                          else ->
                            rawMsg.ifBlank { "Could not send SMS OTP. Check number & Firebase Phone Auth setup." }
                        }
                    }

                    override fun onCodeSent(
                      verifId: String,
                      token: PhoneAuthProvider.ForceResendingToken,
                    ) {
                      isSendingOtp = false
                      verificationId = verifId
                      phoneSuccessMessage = "6-digit OTP sent to $normalized."
                    }
                  }

                val options =
                  PhoneAuthOptions.newBuilder(Firebase.auth)
                    .setPhoneNumber(normalized)
                    .setTimeout(60L, TimeUnit.SECONDS)
                    .setActivity(activity)
                    .setCallbacks(callbacks)
                    .build()
                PhoneAuthProvider.verifyPhoneNumber(options)
              },
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("send_otp_button"),
            ) {
              if (isSendingOtp) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
              }
              Text(if (verificationId == null) "Send OTP" else "Resend OTP")
            }

            if (verificationId != null) {
              OutlinedTextField(
                value = otpCode,
                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) otpCode = it },
                label = { Text("Enter 6-Digit OTP") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("level1_otp_input"),
              )
              Button(
                enabled = !isVerifyingOtp && otpCode.length == 6,
                onClick = {
                  val verifId = verificationId ?: return@Button
                  val normalized = normalizePhoneE164(phoneInput) ?: phoneInput.trim()
                  isVerifyingOtp = true
                  phoneError = null
                  val credential = PhoneAuthProvider.getCredential(verifId, otpCode)
                  linkPhoneCredential(credential, normalized)
                },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("verify_otp_button"),
              ) {
                if (isVerifyingOtp) {
                  CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                  Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Verify OTP")
              }
            }

            phoneSuccessMessage?.let { msg ->
              Text(msg, color = EmeraldOnline, style = MaterialTheme.typography.bodyMedium)
            }
            phoneError?.let { err ->
              Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
          }
        }
      }

      // -----------------------------------------------------------------------
      // LEVEL 2: Real-Time Selfie Photo (CameraX Front Camera)
      // -----------------------------------------------------------------------
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().testTag("level2_selfie_card"),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(
                imageVector = Icons.Rounded.FaceRetouchingNatural,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text("Level 2: Real-Time Live Selfie", style = MaterialTheme.typography.titleMedium)
            }
            val level2Pill =
              when {
                profile.isVerified || profile.selfieVerified -> Triple("Approved", EmeraldContainer, EmeraldOnline)
                profile.selfieStatus == "PENDING" -> Triple("Pending Review", AmberContainer, AmberWarm)
                profile.selfieStatus == "REJECTED" -> Triple("Retake Needed", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error)
                else -> Triple("Required", AmberContainer, AmberWarm)
              }
            StatusPill(
              text = level2Pill.first,
              containerColor = level2Pill.second,
              contentColor = level2Pill.third,
            )
          }

          Text(
            text = "Take a live front-camera selfie right now (gallery uploads are disabled to prevent spoofing). Look straight at the camera in good lighting.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          if (profile.isVerified) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Rounded.Verified, contentDescription = null, tint = CobaltBlue)
              Text(
                text = "Your live selfie and identity are verified. Blue tick active!",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
              )
            }
          } else if (showLiveCamera && hasCameraPermission) {
            RealTimeSelfieCameraBox(
              isCapturing = isCapturingSelfie,
              onCaptured = { encodedBase64 ->
                isCapturingSelfie = false
                showLiveCamera = false
                if (encodedBase64 != null) {
                  capturedSelfieData = encodedBase64
                  selfieError = null
                } else {
                  selfieError = "Could not process selfie image. Please try again."
                }
              },
              onCaptureStarted = { isCapturingSelfie = true },
              onError = { msg ->
                isCapturingSelfie = false
                selfieError = msg
              },
              onCancel = { showLiveCamera = false },
            )
          } else {
            val previewBitmap = remember(capturedSelfieData) { ImageUtils.decodePhotoData(capturedSelfieData) }
            if (previewBitmap != null) {
              Box(
                modifier =
                  Modifier.fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
              ) {
                Image(
                  bitmap = previewBitmap.asImageBitmap(),
                  contentDescription = "Captured real-time selfie",
                  contentScale = ContentScale.Crop,
                  modifier =
                    Modifier.size(160.dp)
                      .clip(CircleShape)
                      .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              OutlinedButton(
                onClick = {
                  if (hasCameraPermission) {
                    showLiveCamera = true
                    selfieError = null
                  } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                  }
                },
                modifier = Modifier.weight(1f).height(48.dp).testTag("open_live_selfie_camera_button"),
              ) {
                Icon(
                  imageVector = if (previewBitmap == null) Icons.Rounded.CameraAlt else Icons.Rounded.Refresh,
                  contentDescription = null,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (previewBitmap == null) "Open Camera" else "Retake Selfie")
              }

              if (previewBitmap != null && capturedSelfieData != profile.selfiePhotoData) {
                Button(
                  onClick = { scope.launch { onSubmitLiveSelfie(capturedSelfieData) } },
                  modifier = Modifier.weight(1f).height(48.dp).testTag("submit_live_selfie_button"),
                ) {
                  Text("Submit Selfie")
                }
              } else if (previewBitmap != null && profile.selfieStatus == "NONE") {
                Button(
                  onClick = { onSubmitLiveSelfie(capturedSelfieData) },
                  modifier = Modifier.weight(1f).height(48.dp).testTag("submit_live_selfie_button"),
                ) {
                  Text("Submit Selfie")
                }
              }
            }
          }

          selfieError?.let { err ->
            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
          }
        }
      }

      SafetyNoticeCard(
        title = "Your Privacy & Safety",
        message = "Selfie photos and verified mobile numbers are reviewed solely for identity verification and never displayed on public discovery cards.",
      )
    }
  }
}

@Composable
private fun RealTimeSelfieCameraBox(
  isCapturing: Boolean,
  onCaptureStarted: () -> Unit,
  onCaptured: (String?) -> Unit,
  onError: (String) -> Unit,
  onCancel: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val scope = rememberCoroutineScope()
  val imageCapture = remember {
    ImageCapture.Builder()
      .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
      .build()
  }
  val previewView = remember { PreviewView(context) }

  DisposableEffect(lifecycleOwner) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    val mainExecutor = ContextCompat.getMainExecutor(context)
    var cameraProvider: ProcessCameraProvider? = null

    cameraProviderFuture.addListener(
      {
        runCatching {
          val provider = cameraProviderFuture.get()
          cameraProvider = provider
          val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
          }
          val selector =
            if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
              CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
              CameraSelector.DEFAULT_BACK_CAMERA
            }
          provider.unbindAll()
          provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
        }.onFailure { err ->
          onError(err.localizedMessage ?: "Front camera unavailable on this device.")
        }
      },
      mainExecutor,
    )

    onDispose {
      runCatching { cameraProvider?.unbindAll() }
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .aspectRatio(3f / 4f)
          .clip(RoundedCornerShape(20.dp))
          .background(Color.Black),
      contentAlignment = Alignment.Center,
    ) {
      AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
      )
      // Subtle oval alignment guide
      Box(
        modifier =
          Modifier.size(width = 190.dp, height = 240.dp)
            .border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(50)),
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      OutlinedButton(
        onClick = onCancel,
        enabled = !isCapturing,
        modifier = Modifier.weight(1f).height(48.dp),
      ) {
        Text("Cancel")
      }
      Button(
        enabled = !isCapturing,
        onClick = {
          onCaptureStarted()
          val tempFile = File(context.cacheDir, "selfie_${System.currentTimeMillis()}.jpg")
          val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()
          imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
              override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                scope.launch {
                  val encoded = ImageUtils.encodeCapturedSelfieFile(tempFile)
                  runCatching { tempFile.delete() }
                  onCaptured(encoded)
                }
              }

              override fun onError(exception: ImageCaptureException) {
                runCatching { tempFile.delete() }
                onError(exception.localizedMessage ?: "Failed to capture selfie.")
              }
            },
          )
        },
        modifier = Modifier.weight(1f).height(48.dp).testTag("capture_selfie_shutter_button"),
      ) {
        if (isCapturing) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
          Spacer(modifier = Modifier.width(6.dp))
        } else {
          Icon(Icons.Rounded.CameraAlt, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
        }
        Text("Capture Selfie")
      }
    }
  }
}

private fun normalizePhoneE164(raw: String): String? {
  val digitsOnly = raw.trim().filter { it.isDigit() || it == '+' }
  val normalized =
    when {
      digitsOnly.startsWith("+") -> digitsOnly
      digitsOnly.length == 10 -> "+91$digitsOnly"
      else -> "+$digitsOnly"
    }
  val countDigits = normalized.drop(1).count { it.isDigit() }
  return if (normalized.startsWith("+") && countDigits in 10..15) normalized else null
}

private tailrec fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }
