package com.example.base

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
abstract class FirestoreEmulatorTestBase {

  protected lateinit var firestore: FirebaseFirestore
  protected lateinit var auth: FirebaseAuth
  protected lateinit var databaseId: String

  @Before
  open fun setUpFirebase() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val resId = context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
    databaseId = if (resId != 0) context.getString(resId) else "ai-studio-android-friendsh-4f459fd9-67d1-4907-a908-f0926f62adda"

    val app =
      if (FirebaseApp.getApps(context).isEmpty()) {
        val newApp =
          FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder()
              .setApplicationId("com.aistudio.friendshub.vkmrqp")
              .setProjectId(PROJECT_ID)
              .setApiKey("fake-api-key-for-emulator")
              .build(),
          )
        val newFirestore = FirebaseFirestore.getInstance(newApp, databaseId)
        newFirestore.useEmulator(EMULATOR_HOST, FIRESTORE_PORT)
        newFirestore.firestoreSettings =
          FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
            .build()

        val newAuth = FirebaseAuth.getInstance(newApp)
        newAuth.useEmulator(EMULATOR_HOST, AUTH_PORT)
        newApp
      } else {
        FirebaseApp.getInstance()
      }

    firestore = FirebaseFirestore.getInstance(app, databaseId)
    auth = FirebaseAuth.getInstance(app)
  }

  @After
  open fun tearDownFirebase() {
    auth.signOut()
  }

  protected suspend fun signInTestUser(email: String): String =
    withContext(Dispatchers.IO) {
      withTimeout(AUTH_TIMEOUT_MS) {
        val result =
          try {
            auth.signInWithEmailAndPassword(email, DEFAULT_PASSWORD).await()
          } catch (unused: FirebaseAuthInvalidUserException) {
            auth.createUserWithEmailAndPassword(email, DEFAULT_PASSWORD).await()
          }
        checkNotNull(result.user?.uid) { "User auth failed" }
      }
    }

  protected suspend fun signInVerifiedAdmin(email: String = "nikhilkumarsingh2000@gmail.com"): String =
    withContext(Dispatchers.IO) {
      withTimeout(AUTH_TIMEOUT_MS) {
        val uid = signInTestUser(email)
        val user = checkNotNull(auth.currentUser)
        if (!user.isEmailVerified) {
          val idToken = checkNotNull(user.getIdToken(false).await().token)
          postJson(
            "http://$EMULATOR_HOST:$AUTH_PORT/identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=fake-api-key-for-emulator",
            org.json.JSONObject()
              .put("requestType", "VERIFY_EMAIL")
              .put("idToken", idToken)
              .toString(),
          )
          val oobListRaw =
            getText("http://$EMULATOR_HOST:$AUTH_PORT/emulator/v1/projects/$PROJECT_ID/oobCodes")
          val codes = org.json.JSONObject(oobListRaw).optJSONArray("oobCodes")
          var matchedCode: String? = null
          if (codes != null) {
            for (i in codes.length() - 1 downTo 0) {
              val item = codes.getJSONObject(i)
              if (
                item.optString("email").equals(email, ignoreCase = true) &&
                item.optString("requestType") == "VERIFY_EMAIL"
              ) {
                matchedCode = item.optString("oobCode")
                break
              }
            }
          }
          val oobCode = checkNotNull(matchedCode) { "Missing VERIFY_EMAIL oobCode for $email" }
          postJson(
            "http://$EMULATOR_HOST:$AUTH_PORT/identitytoolkit.googleapis.com/v1/accounts:update?key=fake-api-key-for-emulator",
            org.json.JSONObject().put("oobCode", oobCode).toString(),
          )
          user.reload().await()
          user.getIdToken(true).await()
        }
        uid
      }
    }

  private fun postJson(urlStr: String, body: String): String {
    val conn = java.net.URL(urlStr).openConnection() as java.net.HttpURLConnection
    conn.requestMethod = "POST"
    conn.doOutput = true
    conn.setRequestProperty("Content-Type", "application/json")
    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
    val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
    return stream?.bufferedReader()?.readText().orEmpty().also { conn.disconnect() }
  }

  private fun getText(urlStr: String): String {
    val conn = java.net.URL(urlStr).openConnection() as java.net.HttpURLConnection
    conn.requestMethod = "GET"
    val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
    return stream?.bufferedReader()?.readText().orEmpty().also { conn.disconnect() }
  }

  protected fun Throwable.unwrapFirestoreException(): FirebaseFirestoreException? {
    var current: Throwable? = this
    while (current != null) {
      if (current is FirebaseFirestoreException) return current
      current = current.cause
    }
    return null
  }

  private companion object {
    const val EMULATOR_HOST = "127.0.0.1"
    const val FIRESTORE_PORT = 8085
    const val AUTH_PORT = 9099
    const val PROJECT_ID = "demo-no-project"
    const val DEFAULT_PASSWORD = "password123"
    const val AUTH_TIMEOUT_MS = 10000L
  }
}
