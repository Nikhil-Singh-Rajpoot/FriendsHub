package com.example.ui.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import java.security.MessageDigest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object GoogleSignInHelper {
  private const val TAG = "FriendsHubAuth"

  @SuppressLint("DiscouragedApi")
  private fun resolveDefaultWebClientId(context: Context): String? {
    val resId =
      context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    if (resId == 0) return null
    return runCatching { context.getString(resId) }.getOrNull()?.takeIf { it.isNotBlank() }
  }

  fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope,
  ) {
    if (Firebase.auth.currentUser != null) {
      onAuthSuccess()
      return
    }
    val clientId = resolveDefaultWebClientId(context)
    if (clientId == null) {
      onUnauthenticated()
      return
    }

    val googleIdOption =
      GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

    scope.launch {
      try {
        val result = credentialManager.getCredential(context, request)
        val credential = result.credential
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
          val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
          val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
          Firebase.auth.signInWithCredential(authCredential).await()
          onAuthSuccess()
        } else {
          onUnauthenticated()
        }
      } catch (e: Exception) {
        onUnauthenticated()
      }
    }
  }

  private fun findActivity(context: Context): Activity? {
    var current: Context? = context
    while (current is ContextWrapper) {
      if (current is Activity) return current
      current = current.baseContext
    }
    return current as? Activity
  }

  @Suppress("DEPRECATION")
  fun getAppSha1Fingerprint(context: Context): String? =
    runCatching {
      val pm = context.packageManager
      val pkgName = context.packageName
      val signature =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNING_CERTIFICATES)
          info.signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
          val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNATURES)
          info.signatures?.firstOrNull()
        } ?: return null
      val digest = MessageDigest.getInstance("SHA-1").digest(signature.toByteArray())
      digest.joinToString(":") { byte -> "%02X".format(byte) }
    }.getOrNull()

  fun startInteractiveSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    onAuthCancelled: (String) -> Unit,
    scope: CoroutineScope,
  ) {
    val clientId = resolveDefaultWebClientId(context)
    if (clientId == null) {
      onAuthError("Google Sign-In configuration missing (google-services.json required).")
      return
    }

    val activity = findActivity(context)
    if (activity == null) {
      onAuthError("Sign-in requires an active screen context.")
      return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

    scope.launch {
      try {
        val result = credentialManager.getCredential(activity, request)
        val credential = result.credential
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
          val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
          val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
          Firebase.auth.signInWithCredential(authCredential).await()
          onAuthSuccess()
        } else {
          onAuthError("Unexpected credential type returned.")
        }
      } catch (e: GetCredentialCancellationException) {
        val sha1 = getAppSha1Fingerprint(context)
        Log.w(TAG, "Google Sign-In cancelled or blocked (SHA1=$sha1): ${e.message}", e)
        val hint =
          if (sha1 != null) {
            "Sign-in was cancelled. If you chose a Google account and the popup closed, add this app's SHA-1 fingerprint to Firebase Console -> Project Settings -> Your apps (${context.packageName}):\n$sha1"
          } else {
            "Sign-in was cancelled. Please select your Google account again or verify your app's SHA-1 in Firebase Console."
          }
        onAuthCancelled(hint)
      } catch (e: Exception) {
        Log.e(TAG, "Google Sign-In failed", e)
        onAuthError(e.localizedMessage ?: "Google Sign-In failed")
      }
    }
  }

  fun signOut(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onComplete: () -> Unit = {},
  ) {
    scope.launch {
      withContext(NonCancellable) {
        try {
          credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
          Log.w(TAG, "Failed to clear credential state: ${e.message}")
        } finally {
          Firebase.auth.signOut()
          onComplete()
        }
      }
    }
  }
}
