# Build Troubleshooting Guide

## Quick Build Command

**Option 1: Use the batch file**
```bash
build_app.bat
```

**Option 2: Manual command**
```bash
cd ChessomaniaApp
gradlew.bat assembleDebug
```

**Option 3: With error details**
```bash
cd ChessomaniaApp
gradlew.bat clean assembleDebug --stacktrace
```

---

## Common Build Errors & Solutions

### Error 1: "SDK location not found"
**Solution:**
Create/edit `ChessomaniaApp/local.properties`:
```properties
sdk.dir=C\:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk
```
Replace `YourUsername` with your actual Windows username.

**Find your SDK location:**
- Open Android Studio
- File → Settings → Appearance & Behavior → System Settings → Android SDK
- Copy the "Android SDK Location" path

---

### Error 2: "Unsupported class file major version"
**Error message:** `Unsupported class file major version 61` or similar

**Cause:** Java version mismatch

**Solution:**
1. Check Java version:
   ```bash
   java -version
   ```
   Should show Java 17 or 11

2. If wrong version, set JAVA_HOME:
   ```bash
   set JAVA_HOME=C:\Program Files\Java\jdk-17
   set PATH=%JAVA_HOME%\bin;%PATH%
   ```

---

### Error 3: "Cannot resolve symbol 'SecurePrefs'"
**Cause:** Missing file or import

**Solution:**
Check if `SecurePrefs.kt` exists at:
`ChessomaniaApp/app/src/main/java/com/chessomania/app/net/SecurePrefs.kt`

If missing, create it:
```kotlin
package com.chessomania.app.net

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePrefs {
    private fun getPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            "chess_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveSession(context: Context, token: String, username: String) {
        getPrefs(context).edit()
            .putString("jwt_token", token)
            .putString("username", username)
            .apply()
    }

    fun getToken(context: Context): String? = getPrefs(context).getString("jwt_token", null)
    fun getUsername(context: Context): String? = getPrefs(context).getString("username", null)
    fun isLoggedIn(context: Context): Boolean = getToken(context) != null

    fun clearSession(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
```

---

### Error 4: "Cannot resolve symbol 'SSEService'"
**Cause:** Missing SSEService file

**Solution:**
Check if file exists:
`ChessomaniaApp/app/src/main/java/com/chessomania/app/net/SSEService.kt`

If missing, let me know and I'll provide the complete file.

---

### Error 5: "Cannot resolve symbol 'SseEvent'"
**Cause:** Missing data class

**Solution:**
Check if file exists:
`ChessomaniaApp/app/src/main/java/com/chessomania/app/net/SseEvent.kt`

Should contain:
```kotlin
package com.chessomania.app.net

data class SseEvent(
    val type: String,
    val username: String? = null,
    val status: String? = null,
    val from: String? = null,
    val by: String? = null,
    val gameId: String? = null,
    val white: String? = null,
    val black: String? = null,
    val challengeId: String? = null,
    val color: String? = null,
    val fen: String? = null,
    val san: String? = null,
    val winner: String? = null,
    val loser: String? = null
)
```

---

### Error 6: "Manifest merger failed"
**Error:** AndroidManifest.xml errors

**Solution:**
Check `ChessomaniaApp/app/src/main/AndroidManifest.xml` has:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<application
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
    
    <service
        android:name=".net.SSEService"
        android:enabled="true"
        android:exported="false"
        android:foregroundServiceType="dataSync" />
</application>
```

---

### Error 7: "Duplicate class found"
**Solution:**
```bash
cd ChessomaniaApp
gradlew.bat clean
gradlew.bat assembleDebug
```

---

### Error 8: Dependencies not downloading
**Error:** "Could not resolve..."

**Solution:**
1. Check internet connection
2. Check `build.gradle.kts` has correct repositories:
```kotlin
repositories {
    google()
    mavenCentral()
}
```

3. Try:
```bash
gradlew.bat --refresh-dependencies assembleDebug
```

---

### Error 9: "Out of memory"
**Error:** `OutOfMemoryError` during build

**Solution:**
Edit `ChessomaniaApp/gradle.properties`, add:
```properties
org.gradle.jvmargs=-Xmx2048m -XX:MaxPermSize=512m
org.gradle.daemon=true
org.gradle.parallel=true
```

---

### Error 10: Network/Proxy issues
**Error:** Cannot download Gradle or dependencies

**Solution:**
If behind proxy, edit `ChessomaniaApp/gradle.properties`:
```properties
systemProp.http.proxyHost=your.proxy.com
systemProp.http.proxyPort=8080
systemProp.https.proxyHost=your.proxy.com
systemProp.https.proxyPort=8080
```

---

## Build Process Verification

### Step 1: Check Prerequisites
```bash
# Check Java
java -version
# Should be Java 11 or 17

# Check Android SDK
echo %ANDROID_HOME%
# Should point to SDK folder
```

### Step 2: Check Project Structure
```
ChessomaniaApp/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/chessomania/app/
│   │       ├── res/
│   │       └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew.bat
└── local.properties (create if missing)
```

### Step 3: Verify Critical Files Exist
Run this checklist:
- [ ] `app/src/main/java/com/chessomania/app/ui/PlayFragment.kt`
- [ ] `app/src/main/java/com/chessomania/app/SettingsManager.kt`
- [ ] `app/src/main/java/com/chessomania/app/net/NetworkClient.kt`
- [ ] `app/src/main/java/com/chessomania/app/net/SecurePrefs.kt`
- [ ] `app/src/main/java/com/chessomania/app/net/SSEService.kt`
- [ ] `app/src/main/java/com/chessomania/app/net/SseEvent.kt`
- [ ] `app/src/main/java/com/chessomania/app/ui/FriendsBottomSheet.kt`
- [ ] `app/src/main/res/layout/fragment_play.xml`
- [ ] `app/src/main/AndroidManifest.xml`

### Step 4: Clean Build
```bash
cd ChessomaniaApp
gradlew.bat clean
gradlew.bat assembleDebug --stacktrace --info
```

---

## If Still Failing

### Get Detailed Error Log
```bash
cd ChessomaniaApp
gradlew.bat assembleDebug --stacktrace --info > build_log.txt 2>&1
```

Then check `build_log.txt` for the exact error.

### Common Error Patterns

**If you see:** `Cannot resolve symbol`
→ Missing file or wrong package name

**If you see:** `Unresolved reference`
→ Missing import or dependency

**If you see:** `Duplicate class`
→ Run `gradlew clean` first

**If you see:** `SDK location not found`
→ Create/fix `local.properties`

**If you see:** `Execution failed for task`
→ Check the task name, usually indicates specific file issue

---

## Quick Fix Checklist

1. [ ] Is Android SDK installed and path set in `local.properties`?
2. [ ] Is Java 11 or 17 installed and in PATH?
3. [ ] Did you run `gradlew clean` before building?
4. [ ] Are all required files present (see Step 3 above)?
5. [ ] Is internet connection working (for dependencies)?
6. [ ] Are there any red errors in the code files?
7. [ ] Did you check AndroidManifest.xml for proper service declaration?

---

## Get Help

If build still fails:
1. Run: `gradlew.bat assembleDebug --stacktrace > error.txt 2>&1`
2. Share the `error.txt` content
3. Mention which error number above seems closest
4. I'll provide specific fix

---

## Success Indicators

When build succeeds, you'll see:
```
BUILD SUCCESSFUL in Xs
```

APK will be at:
```
ChessomaniaApp\app\build\outputs\apk\debug\app-debug.apk
```

Install command:
```bash
adb install app\build\outputs\apk\debug\app-debug.apk
```

---

**Status**: Ready to build! Try `build_app.bat` first! 🚀
