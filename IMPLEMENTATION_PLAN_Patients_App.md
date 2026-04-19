# 🚑 Emergency Response: Patient App — Implementation Plan
### For: Antigravity Development Team
### Version: 1.1 | Platform: Android Native (Java)

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Prerequisites & Environment Setup](#2-prerequisites--environment-setup)
3. [Architecture Overview](#3-architecture-overview)
4. [Phase 1 — Core Setup, Onboarding & Security](#4-phase-1--core-setup-onboarding--security)
5. [Phase 2 — Lock Screen SOS Trigger](#5-phase-2--lock-screen-sos-trigger)
6. [Phase 3 — Dual-Channel Emergency Dispatch](#6-phase-3--dual-channel-emergency-dispatch)
7. [Phase 4 — AI Document Extraction & Human Validation](#7-phase-4--ai-document-extraction--human-validation)
8. [Security Implementation](#8-security-implementation)
9. [Battery & Performance Optimization](#9-battery--performance-optimization)
10. [Testing Checklist](#10-testing-checklist)
11. [File Structure Reference](#11-file-structure-reference)
12. [Dependency Reference](#12-dependency-reference)
13. [Backend Integration Points](#13-backend-integration-points)
14. [Known Risks & Mitigations](#14-known-risks--mitigations)
15. [UI Design Specification — Pixel-Perfect Reference](#15-ui-design-specification--pixel-perfect-reference)

---

## 1. Project Overview

A life-critical Android application that enables patients to trigger emergency assistance with a fault-tolerant, dual-channel dispatch system (WebSocket + SMS fallback), an always-accessible lock-screen SOS widget, and an AI-powered medical records pipeline with mandatory human validation.

### Core Principles
- **Reliability over elegance** — every feature must degrade gracefully
- **Anti-accidental activation** — deliberate friction prevents false alarms
- **Privacy by design** — location only fetched when SOS is actively triggered
- **Human in the loop** — AI-extracted medical data requires patient approval before it is persisted

---

## 2. Prerequisites & Environment Setup

### Required Tools
| Tool | Version | Purpose |
|------|---------|---------|
| Android Studio | Arctic Fox+ | Primary IDE |
| Android SDK | API 26+ (Android 8.0+) | Minimum target |
| Java | JDK 11+ | Language runtime |
| Google Play Services | Latest | Maps, Location, Push |
| Git | Any | Version control |

### Project Initialization Steps

1. Create a new Android project in Android Studio
   - Package: `com.emergency.patient`
   - Min SDK: API 26
   - Language: Java
   - Template: Empty Activity

2. Configure `build.gradle` (app level) with all dependencies (see [Section 12](#12-dependency-reference))

3. Set up `AndroidManifest.xml` with required permissions:

```xml
<uses-permission android:name="android.permission.SEND_SMS"/>
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
```

4. Register all Services and Activities in `AndroidManifest.xml`:

```xml
<service
    android:name=".services.EmergencyBackgroundService"
    android:foregroundServiceType="location"
    android:exported="false"/>

<activity
    android:name=".activities.LockScreenActivity"
    android:showOnLockScreen="true"
    android:turnScreenOn="true"
    android:excludeFromRecents="true"/>
```

5. Add your Google Maps API key to `local.properties` and reference in `AndroidManifest.xml`:

```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="${MAPS_API_KEY}"/>
```

---

## 3. Architecture Overview

```
┌─────────────────────────────────────────────────┐
│                   Patient App                   │
│                                                 │
│  ┌──────────────┐      ┌──────────────────────┐ │
│  │  UI Layer    │      │  Background Service  │ │
│  │  Activities  │◄────►│  EmergencyService    │ │
│  └──────┬───────┘      └──────────┬───────────┘ │
│         │                         │             │
│  ┌──────▼───────────────────────  ▼──────────┐  │
│  │              Network Layer                │  │
│  │   SocketManager  │  ApiClient  │  SMS     │  │
│  └──────────────────┬────────────────────────┘  │
│                     │                           │
│  ┌──────────────────▼────────────────────────┐  │
│  │             Security Layer                │  │
│  │   TokenManager (EncryptedSharedPrefs)     │  │
│  │   AuthInterceptor (JWT on every request)  │  │
│  └───────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
         │                        │
         ▼                        ▼
  ┌─────────────┐        ┌─────────────────┐
  │  Backend    │        │  Twilio SMS GW  │
  │  WebSocket  │        │  (Fallback)     │
  └─────────────┘        └─────────────────┘
```

### Data Flow — Emergency Trigger

```
User swipes SOS
      │
      ▼
10-second countdown (CANCEL available)
      │
      ▼
FusedLocationProvider fetches GPS
      │
      ▼
SocketManager.emitEmergency(data)
      │
      ├──── Socket ACK within 3s ──► "Help is on the way" ✅
      │
      └──── Timeout (3s) ──────────► SmsFallbackManager.send() ─► Twilio ─► Backend
```

---

## 4. Phase 1 — Core Setup, Onboarding & Security

**Estimated effort:** 3–4 days  
**Depends on:** Nothing (starting point)

### 4.1 TokenManager.java

Responsible for all secure credential storage. Use `EncryptedSharedPreferences` — never plain `SharedPreferences` for tokens.

**Implementation steps:**
1. Initialize `EncryptedSharedPreferences` with `MasterKeys.AES256_GCM_SPEC`
2. Implement `saveJWT(String token)` and `getJWT()` methods
3. Implement `saveUUID(String uuid)` and `getUUID()` methods
4. Add token expiration check: decode JWT payload (Base64), parse `exp` field, compare against `System.currentTimeMillis() / 1000`
5. Implement `clearAll()` for logout

```java
// Key initialization pattern
MasterKey masterKey = new MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build();

SharedPreferences encryptedPrefs = EncryptedSharedPreferences.create(
    context,
    "patient_secure_prefs",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
);
```

### 4.2 OnboardingActivity.java

Single-pass setup screen shown only on first launch. Gate entry with a boolean in (encrypted) prefs.

**Implementation steps:**
1. Check `isOnboardingComplete` flag on app start — skip to `MainActivity` if true
2. Build multi-step form UI:
   - Step 1: Full name, blood group (spinner), date of birth
   - Step 2: Emergency contact name + phone number
   - Step 3: Basic medical conditions (multi-select checkboxes: Diabetes, Hypertension, Heart Disease, Asthma, Other)
3. On form completion:
   - Generate `UUID.randomUUID().toString()` as Patient UUID
   - POST registration payload to backend `/api/patient/register`
   - Store returned JWT and UUID via `TokenManager`
4. Call `PermissionHelper.requestAllPermissions(this)` after registration
5. Set `isOnboardingComplete = true` only after all permissions are granted

### 4.3 QrGenerator.java

Generates a static QR code encoding only the patient UUID. This is intentionally opaque to bystanders.

**Implementation steps:**
1. Accept `patientUUID` as input
2. Use `MultiFormatWriter` from ZXing to encode as `BarcodeFormat.QR_CODE`
3. Target size: 512×512px
4. Return `Bitmap` for display in `MainActivity`
5. Cache the generated bitmap — UUID never changes, no need to regenerate

```java
public static Bitmap generateQR(String patientUUID, int size) throws WriterException {
    BitMatrix bitMatrix = new MultiFormatWriter().encode(
        patientUUID, BarcodeFormat.QR_CODE, size, size
    );
    return BarcodeEncoder.createBitmap(bitMatrix);
}
```

### 4.4 PermissionHelper.java

Centralizes all runtime permission requests.

**Permissions to request and why:**

| Permission | Reason | Type |
|---|---|---|
| `SEND_SMS` | SMS fallback dispatch | Dangerous — runtime request |
| `ACCESS_FINE_LOCATION` | GPS on SOS | Dangerous — runtime request |
| `SYSTEM_ALERT_WINDOW` | Lock screen overlay | Special — intent to Settings |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Keep service alive | Special — intent to Settings |

**Implementation steps:**
1. Check each permission with `ActivityCompat.checkSelfPermission()`
2. Request dangerous permissions in a single `requestPermissions()` call
3. Handle `SYSTEM_ALERT_WINDOW` separately — open Settings via `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` intent
4. Handle battery optimization separately — open Settings via `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent
5. Show rationale dialogs before each special permission request

---

## 5. Phase 2 — Lock Screen SOS Trigger

**Estimated effort:** 3–4 days  
**Depends on:** Phase 1 (TokenManager, PermissionHelper)

### 5.1 EmergencyBackgroundService.java

Persistent foreground service that keeps the app responsive even when killed by the OS.

**Implementation steps:**
1. Extend `Service`, override `onStartCommand()` returning `START_STICKY`
2. Call `startForeground()` with a persistent notification immediately in `onCreate()`
   - Notification channel ID: `emergency_service_channel`
   - Notification importance: `IMPORTANCE_LOW` (silent, no alert sound)
   - Content: "Emergency service active — tap to open app"
3. Register a `BroadcastReceiver` for `Intent.ACTION_SCREEN_OFF` to show `LockScreenActivity`
4. Register `BOOT_COMPLETED` receiver in manifest to restart service after device reboot
5. **Do NOT start GPS here** — location is only fetched when SOS is triggered

```java
// In service onCreate
startForeground(NOTIFICATION_ID, buildNotification());

// Register screen-off receiver
IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
registerReceiver(screenOffReceiver, filter);
```

### 5.2 LockScreenActivity.java

The UI that appears on the lock screen. Must be accessible without unlocking the device.

**Implementation steps:**
1. In `onCreate()`, configure window flags:

```java
getWindow().addFlags(
    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
);
```

2. Build the "Slide to SOS" gesture using a horizontal `SeekBar` or custom `GestureDetector`
   - Require a full-width swipe (>90% of screen width) to register
   - Snap back to start if gesture is incomplete

3. On completed swipe — start the 10-second countdown:
   - Use `CountDownTimer` with 1-second ticks
   - Display large numeric countdown (full-screen, red background)
   - Play loud audio using `AudioManager` at max stream volume (`STREAM_ALARM`)
   - Show a prominent CANCEL button (minimum 80dp height, contrasting color)

4. On CANCEL — immediately stop audio, dismiss timer, return to idle lock screen state

5. On countdown completion — call `triggerEmergency()`:
   - Request single location fix via `FusedLocationProviderClient`
   - On location received → hand off to `SocketManager`

**Anti-accidental activation summary:**
- Full-width swipe required (not a tap)
- 10-second audio countdown
- Large, always-visible CANCEL button
- Visual countdown timer

### 5.3 Location Strategy

Use `FusedLocationProviderClient` for a single, on-demand high-accuracy fix:

```java
LocationRequest locationRequest = LocationRequest.create()
    .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
    .setNumUpdates(1)           // Single fix only
    .setExpirationDuration(10000); // 10-second timeout

fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
```

- Set a 10-second hard timeout. If no fix by then, use last known location as fallback.
- Remove updates immediately after first fix to conserve battery.

---

## 6. Phase 3 — Dual-Channel Emergency Dispatch

**Estimated effort:** 2–3 days  
**Depends on:** Phase 1 (TokenManager), Phase 2 (Location)

### 6.1 SocketManager.java

Manages the WebSocket connection lifecycle and emergency event emission.

**Implementation steps:**
1. Initialize `socket` with JWT in handshake auth header:

```java
IO.Options options = new IO.Options();
options.auth = Collections.singletonMap("token", TokenManager.getJWT());
socket = IO.socket("https://your-backend.com", options);
```

2. Implement `connect()` — called when app foregrounds, not on service start
3. Implement `emitEmergency(EmergencyData data)`:
   - Build JSON payload: `{ jwt, latitude, longitude, timestamp, patientUUID }`
   - Emit on `"emergency_request"` event
   - Start a 3-second `Handler.postDelayed` fallback timer
4. Listen for ACK from server — on receipt, cancel fallback timer and show success UI
5. Implement reconnection logic with exponential backoff (built into Socket.io client)
6. Handle `connect_error` and `disconnect` events gracefully

### 6.2 SmsFallbackManager.java

Triggered automatically if WebSocket ACK is not received within 3 seconds.

**Implementation steps:**
1. Format message as: `SOS|{UUID}|{LAT}|{LNG}` (pipe-delimited for backend parsing)
2. Use `SmsManager.getDefault().sendTextMessage()` targeting the Twilio backend number
3. Register `PendingIntent` for sent/delivered callbacks to confirm delivery
4. Update UI to inform user which channel succeeded: "Emergency sent via SMS"

```java
String message = String.format("SOS|%s|%.6f|%.6f", uuid, lat, lng);
PendingIntent sentIntent = PendingIntent.getBroadcast(context, 0,
    new Intent("SMS_SENT"), PendingIntent.FLAG_IMMUTABLE);
SmsManager.getDefault().sendTextMessage(TWILIO_NUMBER, null, message, sentIntent, null);
```

### 6.3 Dispatch Logic (Complete Flow)

```
triggerEmergency()
├── fetchLocation()
│     └── onLocationReceived(lat, lng)
│           ├── SocketManager.emitEmergency(lat, lng)
│           │     └── startFallbackTimer(3000ms)
│           │           ├── [ACK received] → cancelTimer() → showSuccess("WebSocket")
│           │           └── [Timer fires] → SmsFallbackManager.send() → showSuccess("SMS")
│           └── updateUI("Dispatching...")
└── [Location timeout] → useLastKnownLocation() → continue above
```

---

## 7. Phase 4 — AI Document Extraction & Human Validation

**Estimated effort:** 3–4 days  
**Depends on:** Phase 1 (TokenManager, ApiClient), Backend AI pipeline

### 7.1 DocumentUploadActivity.java

Allows patients to upload medical reports (PDFs, images) via Android's Storage Access Framework (SAF).

**Implementation steps:**
1. Use `DocumentPickerHelper` to launch SAF picker:

```java
Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
intent.addCategory(Intent.CATEGORY_OPENABLE);
intent.setType("*/*");
intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/pdf", "image/*"});
startActivityForResult(intent, PICK_DOCUMENT_REQUEST);
```

2. In `onActivityResult()`, get the `Uri` and take persistent URI permission:

```java
getContentResolver().takePersistableUriPermission(uri,
    Intent.FLAG_GRANT_READ_URI_PERMISSION);
```

3. Use `ApiClient` to POST the document as `multipart/form-data` to `/api/documents/upload`
   - Include `patientUUID` and JWT in the request
4. Show upload progress bar
5. On success, store the returned `processing_id` — needed to match incoming push notification
6. Display "Document uploaded — you'll be notified when processing is complete"

### 7.2 ValidationActivity.java

Launched by a push notification when backend AI has finished extracting data.

**Implementation steps:**
1. Receive push notification payload containing `processing_id` and `extraction_status`
2. On notification tap, launch `ValidationActivity` with `processing_id` as intent extra
3. GET `/api/documents/{processing_id}/extraction` to fetch AI-extracted data
4. Build the review UI:
   - Left panel: thumbnail/preview of the original document
   - Right panel: extracted data in clearly labeled fields:
     - Allergies
     - Current Medications
     - Diagnoses
     - Test Results (key values)
   - Each field should be editable in case of minor corrections
5. APPROVE button → POST `/api/documents/{processing_id}/approve` → backend commits data to patient profile
6. REJECT button → options: "Re-process" or "Enter manually"
7. Show confirmation on approval: "Your health profile has been updated"

### 7.3 AI Validation Flow

```
Patient uploads document
        │
        ▼
Backend AI agent processes (async)
        │
        ▼
Push notification → patient device
        │
        ▼
ValidationActivity opens
        │
   ┌────┴────┐
   │         │
APPROVE    REJECT
   │         │
   ▼         ▼
Backend   Re-process
commits   or manual
data      entry
```

> ⚠️ **Critical:** The backend must **never** auto-commit AI-extracted data to the patient profile. Data is only written after explicit patient approval (`onApprove()` is called).

---

## 8. Security Implementation

### 8.1 JWT Storage
- Always use `EncryptedSharedPreferences` (AES256-GCM)
- Never log JWT values, even in debug builds — use ProGuard to strip logs in release
- Implement automatic refresh: check expiration before every API call, refresh if within 5 minutes of expiry

### 8.2 AuthInterceptor.java

OkHttp interceptor that attaches JWT to every outgoing HTTP request:

```java
@Override
public Response intercept(Chain chain) throws IOException {
    Request original = chain.request();
    Request authenticated = original.newBuilder()
        .header("Authorization", "Bearer " + TokenManager.getJWT())
        .build();
    return chain.proceed(authenticated);
}
```

Add to Retrofit's `OkHttpClient`:

```java
OkHttpClient client = new OkHttpClient.Builder()
    .addInterceptor(new AuthInterceptor())
    .build();
```

### 8.3 QR Code Security Model

| What the QR contains | What it means to a scanner |
|---|---|
| Patient UUID only | Meaningless random string |
| No name, no medical data | Safe if photographed or shared |
| Backend maps UUID → patient | Valid JWT required to access data |

### 8.4 Location Privacy
- GPS is activated only after the 10-second countdown completes
- Location data is never persisted locally beyond the current session
- No geofencing, no passive location tracking, no location history

---

## 9. Battery & Performance Optimization

### Service Strategy

| Approach | Status |
|---|---|
| Continuous GPS polling | ❌ Never |
| Continuous network polling | ❌ Never |
| Foreground service with notification | ✅ Required |
| Wake lock during countdown only | ✅ Acceptable |
| Single GPS fix per emergency | ✅ Required |

### Foreground Service Notification
- Use `IMPORTANCE_LOW` notification channel (no sound, no vibration)
- Always show a brief, non-alarming title: "Emergency service active"
- Deep link the notification to `MainActivity`

### WorkManager (Optional Enhancement)
- For periodic JWT refresh, consider `WorkManager` with `PeriodicWorkRequest` (interval: 23 hours)
- This avoids keeping a network connection alive just for token management

---

## 10. Testing Checklist

### Phase 1 — Core Setup
- [ ] First launch shows `OnboardingActivity`, subsequent launches skip it
- [ ] All form fields validate correctly (non-empty name, valid blood group, valid phone)
- [ ] Patient UUID generated and stored persistently
- [ ] JWT stored in `EncryptedSharedPreferences` (verify using Android Device File Explorer — should be unreadable)
- [ ] QR code renders correctly and encodes only the UUID
- [ ] All 4 permissions requested and granted

### Phase 2 — Lock Screen
- [ ] `EmergencyBackgroundService` starts on app launch and shows persistent notification
- [ ] Service restarts after device reboot (BOOT_COMPLETED receiver)
- [ ] `LockScreenActivity` appears on screen-off
- [ ] SOS swipe requires full-width gesture (partial swipe does not trigger)
- [ ] Countdown audio plays at alarm volume
- [ ] CANCEL button stops countdown immediately at any point (1s through 9s)
- [ ] Countdown completes and location fetch begins
- [ ] Location fetched successfully (test indoors with Wi-Fi fallback and outdoors with GPS)

### Phase 3 — Dispatch
- [ ] WebSocket connects with valid JWT
- [ ] Emergency payload emitted with correct fields
- [ ] Server ACK cancels fallback timer
- [ ] Simulated WebSocket failure triggers SMS within 3 seconds
- [ ] SMS format correct: `SOS|UUID|LAT|LNG` (verify with backend team)
- [ ] UI correctly shows which channel succeeded
- [ ] Test full flow with airplane mode (SMS only) and with internet (WebSocket)

### Phase 4 — Document Pipeline
- [ ] SAF picker opens and allows PDF + image selection
- [ ] Document uploads successfully (check network tab for 200 response)
- [ ] Push notification received after backend processing (simulate with backend test endpoint)
- [ ] `ValidationActivity` displays extracted fields correctly
- [ ] Editable fields allow corrections before approval
- [ ] APPROVE sends correct payload to backend
- [ ] Backend profile reflects approved data
- [ ] REJECT does not modify backend profile

---

## 11. File Structure Reference

```
app/src/main/java/com/emergency/patient/
│
├── activities/
│   ├── MainActivity.java           # QR display, active tracking dashboard
│   ├── LockScreenActivity.java     # "Slide to SOS" + 10s countdown
│   ├── OnboardingActivity.java     # First-time registration flow
│   ├── DocumentUploadActivity.java # SAF document picker + upload
│   └── ValidationActivity.java    # AI data review — approve / reject
│
├── services/
│   └── EmergencyBackgroundService.java  # Persistent foreground service
│
├── network/
│   ├── SocketManager.java          # WebSocket with JWT auth
│   ├── ApiClient.java              # Retrofit REST client
│   └── SmsFallbackManager.java     # SmsManager → Twilio
│
├── security/
│   ├── TokenManager.java           # EncryptedSharedPreferences wrapper
│   └── AuthInterceptor.java        # OkHttp JWT interceptor
│
└── utils/
    ├── QrGenerator.java            # ZXing UUID → Bitmap
    ├── PermissionHelper.java       # Runtime permission orchestrator
    └── DocumentPickerHelper.java   # SAF wrapper utility
```

---

## 12. Dependency Reference

Add to `app/build.gradle`:

```gradle
dependencies {
    // Socket.IO
    implementation 'io.socket:socket.io-client:2.1.0'

    // ZXing — QR generation
    implementation 'com.google.zxing:core:3.5.0'
    implementation 'com.journeyapps:zxing-android-embedded:4.3.0'

    // Google Play Services — Location & Maps
    implementation 'com.google.android.gms:play-services-location:21.0.1'
    implementation 'com.google.android.gms:play-services-maps:18.1.0'

    // Security — Encrypted storage
    implementation 'androidx.security:security-crypto:1.1.0-alpha06'

    // Networking — Retrofit + OkHttp
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
    implementation 'com.squareup.okhttp3:logging-interceptor:4.11.0'

    // Push notifications
    implementation 'com.google.firebase:firebase-messaging:23.1.2'
}
```

---

## 13. Backend Integration Points

The following endpoints must exist on the backend before Phase integration can be completed:

| Phase | Method | Endpoint | Purpose |
|---|---|---|---|
| 1 | POST | `/api/patient/register` | Register patient, return JWT + UUID |
| 1 | POST | `/api/auth/refresh` | Refresh expiring JWT |
| 3 | WS | `emergency_request` event | Receive SOS via WebSocket |
| 3 | SMS | Twilio webhook | Receive SOS via SMS `SOS\|UUID\|LAT\|LNG` |
| 4 | POST | `/api/documents/upload` | Receive medical document, return `processing_id` |
| 4 | GET | `/api/documents/{id}/extraction` | Fetch AI-extracted data for validation |
| 4 | POST | `/api/documents/{id}/approve` | Commit extracted data to patient profile |
| 4 | POST | `/api/documents/{id}/reject` | Flag for re-processing |

### Push Notification Payload (Phase 4)

Backend must send an FCM push notification in this format when AI processing completes:

```json
{
  "data": {
    "type": "extraction_complete",
    "processing_id": "abc-123",
    "status": "success"
  }
}
```

---

## 14. Known Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| GPS unavailable indoors | High | High | Use last known location as fallback; notify user |
| WebSocket connection drops | Medium | High | 3-second timeout + automatic SMS fallback |
| Battery optimization kills service | Medium | Critical | `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission + foreground service |
| Accidental SOS trigger | Medium | Medium | Full-width swipe + 10s countdown + CANCEL button |
| SMS delivery failure | Low | High | Delivery receipt callback; advise user to retry |
| JWT expires during emergency | Low | High | Check and refresh token before every emit |
| AI extracts incorrect medical data | Medium | Critical | Human validation gate — no auto-commit |
| Lock screen permission denied | Low | High | Explain to user in onboarding with clear rationale |

---

## 15. UI Design Specification — Pixel-Perfect Reference

This section documents the three confirmed screens from the approved design mockups. All measurements, colours, typography, spacing, and interaction notes are specified for pixel-perfect Android implementation using `ConstraintLayout` with `dp`/`sp` units. The design language is clean, white-dominant with coral-red emergency accents and orange secondary accents.

---

### 15.1 Design Tokens (Global)

Apply these values across all three screens as a consistent design system.

#### Colour Palette

| Token | Hex | Usage |
|---|---|---|
| `color_background` | `#F0F4F8` | Screen/page background (cool off-white) |
| `color_surface` | `#FFFFFF` | Cards and panels |
| `color_primary_orange` | `#F26522` | Edit button, "Doctor Access Only" badge, active nav icon, condition chip border |
| `color_emergency_red` | `#E84B4B` | SOS cross icon, countdown text, emergency banner text, CANCEL SOS stroke, "102 POLICE" text |
| `color_emergency_banner_bg` | `#FDEAEA` | Emergency Activated banner background (very light red tint) |
| `color_green_call` | `#2DB55D` | Call button circle, "CALL ALL" button, Live Feed checkmark icons |
| `color_text_primary` | `#1A2B3C` | Patient name (H1), section headings, all body — very dark navy |
| `color_text_secondary` | `#8A96A3` | Subtitle text (age, nav labels, timestamps) |
| `color_condition_chip_bg` | `#FEF0EC` | Condition tag background (light coral tint) |
| `color_doctor_badge_bg` | `#FFF3E0` | "Doctor Access Only" badge background (warm amber tint) |
| `color_nav_inactive` | `#B0BAC5` | Inactive bottom nav icons and labels |
| `color_divider` | `#E8ECF0` | Card borders, subtle separators |
| `color_qr_bg_teal` | `#4DBDAD` | QR card background on Medical ID screen |
| `color_spinner_inactive` | `#D0D8E0` | SOS cross background glow (very faint red tint `#FDEAEA`) |

#### Typography

| Role | Font | Weight | Size | Colour |
|---|---|---|---|---|
| Screen Title (nav bar) | System / Roboto | Bold (700) | `18sp` | `color_text_primary` |
| Patient Name H1 | Roboto | ExtraBold (800) | `26sp` | `color_text_primary` |
| Patient Subhead | Roboto | Regular (400) | `15sp` | `color_text_secondary` |
| Section Label (ALL CAPS) | Roboto | Bold (700) | `12sp` | `color_text_primary` (letter-spacing +0.08em) |
| Body / Name in card | Roboto | SemiBold (600) | `15sp` | `color_text_primary` |
| Tag / Badge text | Roboto | SemiBold (600) | `11sp` | varies per badge |
| Timestamp text | Roboto | Regular (400) | `12sp` | `color_text_secondary` |
| Lock screen patient name | Roboto | Bold (700) | `34sp` | `color_text_primary` |
| Lock screen time | Roboto | Light (300) | `64sp` | `color_text_primary` |
| Lock screen date | Roboto | Regular (400) | `17sp` | `color_text_secondary` |
| Lock screen CTA label | Roboto | SemiBold (600) | `11sp` | `color_emergency_red` (letter-spacing +0.15em) |
| Bottom nav label | Roboto | Regular (400) | `11sp` | active: `color_primary_orange`, inactive: `color_nav_inactive` |

#### Spacing & Shape

| Token | Value |
|---|---|
| Screen horizontal margin | `16dp` |
| Card corner radius | `16dp` |
| Card padding (internal) | `16dp` |
| Card shadow elevation | `2dp` (subtle, `color: #00000014`) |
| Card gap (vertical between cards) | `12dp` |
| Bottom nav height | `64dp` |
| Condition chip corner radius | `20dp` (fully rounded pill) |
| Badge corner radius | `8dp` |
| Call button circle diameter | `48dp` |
| Bottom action button height | `52dp`, corner radius `26dp` (pill) |

---

### 15.2 Screen 1 — Lock Screen (`LockScreenActivity`)

> **Triggered by:** Device screen-off event from `EmergencyBackgroundService`
> **File:** `activities/LockScreenActivity.java` + `res/layout/activity_lock_screen.xml`

#### Layout (top → bottom, full screen)

```
┌────────────────────────────────────────────┐
│  [Status bar — system, translucent]        │  ~24dp
│                                            │
│                                            │
│           Benjamin                         │  ~34sp Bold
│         [40dp below top center]            │
│                                            │
│                                            │
│              [✚ SOS Cross]                 │  see below
│            TAP IN EMERGENCY                │  11sp, all-caps, red
│                                            │
│                                            │
│                                            │
│              14:10                         │  64sp Light
│         Saturday, March 14                 │  17sp Regular
│                                            │
│  [Home indicator]                          │  ~34dp
└────────────────────────────────────────────┘
```

**Background:** `#F5F5F7` — near-white with very slight warm grey, full bleed, no cards.

**Patient Name:**
- Text: Patient's first name only (fetched from `TokenManager`)
- Position: Horizontally centred, vertically at ~28% from top
- Font: Roboto Bold 34sp, `color_text_primary` (`#1A2B3C`)

**SOS Cross Icon:**
- Custom drawable: a medical cross (plus sign), two overlapping rectangles
- Size: `72dp × 72dp` bounding box
- Cross arm thickness: `18dp`
- Colour: `#E8574A` (slightly warm coral-red)
- **Background glow effect:** `ShapeDrawable` circle, diameter `96dp`, colour `#FFE8E6` (very light red), centred behind the cross. This creates the faint pulsing halo visible in the mockup.
- Implement a subtle `ScaleAnimation` pulse: scale 1.0 → 1.07 → 1.0, duration 1400ms, repeat infinite, `RELATIVE_TO_SELF` pivot
- Position: Horizontally centred, ~12dp below the patient name

**"TAP IN EMERGENCY" label:**
- Text: `TAP IN EMERGENCY` (all caps, hardcoded string resource)
- Font: Roboto SemiBold 11sp, letter-spacing 0.15em
- Colour: `#E8574A`
- Position: Centred, `16dp` below the cross icon
- `android:letterSpacing="0.15"`

**Time display:**
- Text: Live `HH:mm` from `java.time.LocalTime`, updated every second via `Handler`
- Font: Roboto Light (weight 300) 64sp, `color_text_primary`
- Position: Centred, placed at ~68% from top

**Date display:**
- Text: `EEEE, MMMM d` format (e.g. `Saturday, March 14`)
- Font: Roboto Regular 17sp, `color_text_secondary`
- Position: Centred, `8dp` below time

**Tap Interaction:**
- The SOS cross AND the "TAP IN EMERGENCY" label are wrapped in a single `FrameLayout` with `OnClickListener`
- On tap → launch 10-second `CountDownTimer` with full-screen red countdown overlay (existing Phase 2 logic)
- Do **not** require a swipe — the mockup uses a tap gesture (`OnClickListener`), not a `SeekBar` swipe

> ⚠️ **Design change from original spec:** The approved mockup uses a **tap**, not a swipe gesture. Update `LockScreenActivity` accordingly. Anti-accidental protection is provided by the 10-second countdown + CANCEL button, not by the gesture complexity.

---

### 15.3 Screen 2 — Medical ID (`MedicalIdFragment` / `MainActivity` tab)

> **Accessed via:** Bottom nav — "Medical ID" tab (3rd icon, person with lines)
> **File:** `activities/MainActivity.java` + `res/layout/fragment_medical_id.xml`

#### Top Navigation Bar

- Background: `color_background` (`#F0F4F8`), no elevation
- Left: `<` back chevron, `24dp`, `color_text_primary`
- Centre: "Medical ID", Roboto Bold 18sp, `color_text_primary`
- Right: "Edit" text button, Roboto SemiBold 16sp, `color_primary_orange` (`#F26522`)
- Height: `56dp`

#### Patient Profile Card

- Background: `color_surface` white
- Corner radius: `16dp`
- Elevation: `2dp`
- Margin: `16dp` horizontal, `12dp` top from nav bar
- Padding: `20dp` all sides
- Internal layout (centred column):

  **Avatar:**
  - Circular `ImageView`, diameter `80dp`
  - Border: none (clip to circle using `ShapeableImageView` with `cornerFamily="rounded"` at 50%)
  - Bottom-right badge overlay: `ImageView` `28dp × 28dp`, orange pill shape (`color_primary_orange`), white asterisk/medical cross icon centred inside it. Position: `+4dp` right, `+4dp` bottom of avatar bounds.

  **Patient Name:**
  - "Benjamin Miller" — Roboto ExtraBold 26sp, `color_text_primary`
  - Margin top: `12dp` from avatar

  **Age:**
  - "32 years old" — Roboto Regular 15sp, `color_text_secondary`
  - Margin top: `4dp`

  **Condition Chip:**
  - "Type 1 Diabetes" — pill-shaped tag
  - Background: `color_condition_chip_bg` (`#FEF0EC`)
  - Border: `1.5dp` stroke, `color_primary_orange` (`#F26522`), corner radius `20dp`
  - Text: Roboto SemiBold 13sp, `color_primary_orange`
  - Padding: `6dp` vertical, `16dp` horizontal
  - Margin top: `10dp`

#### Emergency QR Code Card

- Background: `color_surface` white
- Corner radius: `16dp`
- Elevation: `2dp`
- Margin: `16dp` horizontal, `12dp` top from profile card
- Padding: `16dp`

  **Header row (horizontal):**
  - Left: "Emergency QR Code", Roboto Bold 16sp, `color_text_primary`
  - Right: "Doctor Access Only" badge:
    - Background: `color_doctor_badge_bg` (`#FFF3E0`), corner radius `8dp`
    - Padding: `6dp` vertical, `10dp` horizontal
    - Left icon: lock icon `16dp`, `color_primary_orange`
    - Text: "DOCTOR ACCESS ONLY", Roboto Bold 10sp, `color_primary_orange`, letter-spacing `+0.05em`

  **QR Display Area:**
  - Outer container: corner radius `12dp`, background `color_qr_bg_teal` (`#4DBDAD`), padding `16dp`
  - Width: `220dp`, centred horizontally in card
  - Inner white card: corner radius `10dp`, background white, padding `12dp`, elevation `4dp`
  - Inside white card: `ImageView` of generated ZXing QR bitmap, size `140dp × 140dp`
  - Margin top: `12dp` from header row

  **Caption:**
  - "Scan for Full Medical Profile" — Roboto Regular 13sp, `color_text_secondary`, centred
  - Margin top: `10dp` from QR container

#### Bottom Navigation Bar

- Background: white, top border `1dp` `color_divider`
- Height: `64dp`
- Four evenly spaced tabs:

  | Index | Icon | Label | Active? |
  |---|---|---|---|
  | 0 | Heart outline | Summary | No |
  | 1 | Share/network nodes | Sharing | No |
  | 2 | Person with lines (ID card) | Medical ID | **Yes** |
  | 3 | Gear/cog | Settings | No |

- Active tab: icon + label both `color_primary_orange`
- Inactive tab: icon + label both `color_nav_inactive` (`#B0BAC5`)
- Icon size: `24dp`, label `11sp` below icon, gap `4dp`

---

### 15.4 Screen 3 — Active Emergency Dashboard (`EmergencyActiveActivity`)

> **Triggered by:** Countdown completion in `LockScreenActivity`
> **File:** `activities/EmergencyActiveActivity.java` + `res/layout/activity_emergency_active.xml`
> **New activity** — not in original spec, add to `AndroidManifest.xml`

#### Emergency Banner (top of screen, full width, no card)

- Background: `color_emergency_banner_bg` (`#FDEAEA`)
- Height: `60dp`, corner radius `0dp` (flush to top edges)
- Left: pulsing red dot indicator, diameter `10dp`, colour `#E8574A`
  - Animate with `AlphaAnimation` 1.0 → 0.2 → 1.0, duration 1000ms, repeat infinite
- Text: "Emergency Activated — Notifying Family", Roboto SemiBold 16sp, `color_emergency_red`
  - Left margin from dot: `10dp`
- Right: `×` close/dismiss icon, `20dp`, `color_emergency_red`
  - Tapping `×` does **not** cancel the SOS — it only collapses the banner. Show a `Snackbar`: "SOS still active. Tap CANCEL SOS to stop."
- Padding: `16dp` horizontal, vertical centred

#### Two-Column Card Row

Horizontal `LinearLayout`, equal weight (0.5 / 0.5), `16dp` horizontal margin, `12dp` gap between cards.

**Left Card — "SHOW QR CODE"**
- Background: white, corner radius `16dp`, elevation `2dp`
- Padding: `16dp`
- Header: "SHOW QR CODE" — Roboto Bold 12sp, `color_text_primary`, letter-spacing `+0.08em`
- QR `ImageView`: white background card (corner radius `8dp`, padding `8dp`), size `100dp × 100dp` centred
  - Render same ZXing QR as Medical ID screen
- Badge: "🔒 Doctor Access Only" — same badge style as Medical ID screen, centred below QR, margin top `8dp`
- Caption below badge: "For Medical Staff Only" — Roboto Regular 11sp, `color_text_secondary`, centred, margin top `4dp`

**Right Card — "CALL RELATIVES"**
- Background: white, corner radius `16dp`, elevation `2dp`
- Padding: `16dp`
- Header: "CALL RELATIVES" — Roboto Bold 12sp, `color_text_primary`, letter-spacing `+0.08em`

  **Contact row (repeat for each emergency contact):**
  - Name: Roboto SemiBold 15sp, `color_text_primary`
  - Relationship tag: small pill, background `#EDF3FF`, text `color_primary_orange`, Roboto Regular 11sp, padding `3dp` vertical `8dp` horizontal, corner radius `6dp`
  - Right: green call button, circle `48dp`, background `color_green_call` (`#2DB55D`), white phone icon `20dp`
  - Row height: `52dp`
  - Divider: `1dp` `color_divider` between rows

  **"CALL ALL →" button:**
  - Full width of card, height `44dp`, corner radius `8dp`
  - Background: `color_green_call` (`#2DB55D`)
  - Text: "CALL ALL →" Roboto Bold 14sp, white
  - Margin top: `10dp`
  - On tap: iterate contacts list and invoke `Intent.ACTION_CALL` for each (requires `CALL_PHONE` permission — add to manifest and `PermissionHelper`)

#### Live Status Feed Card

- Background: white, corner radius `16dp`, elevation `2dp`
- Margin: `16dp` horizontal, `12dp` top from card row
- Padding: `16dp`

  **Header row:**
  - Left: "Live Status Feed" — Roboto Bold 16sp, `color_text_primary`
  - Right: green dot `10dp` with same pulse animation as banner red dot (green version)

  **Status item — COMPLETED state:**
  - Left icon: green filled circle `24dp`, white checkmark `14dp` inside
  - Text: event description, Roboto Regular 14sp, `color_text_primary`
  - Timestamp: Roboto Regular 12sp, `color_text_secondary`, on new line below description
  - Row padding: `8dp` vertical

  **Status item — IN PROGRESS state:**
  - Left icon: `ProgressBar` (circular indeterminate) `24dp`, `color_primary_orange` tint
  - Text: Roboto Regular 14sp, `color_text_primary`, shown at full opacity

  **Status item — QUEUED state:**
  - Left icon: empty circle outline `24dp`, `color_divider`
  - Text: Roboto Regular 14sp, `color_text_secondary` (greyed out), displayed at 50% alpha

  **Feed item order (from top):**
  1. ✅ "Location captured (area name)" + timestamp — COMPLETED
  2. ✅ "Medical ID pushed to lock screen" + timestamp — COMPLETED
  3. ⏳ "Alerting nearby hospitals" — IN PROGRESS (spinner)
  4. ◯ next queued steps — QUEUED

  Implement feed as `RecyclerView` with `LinearLayoutManager`. Items added dynamically as each dispatch step resolves.

#### Bottom Action Bar (fixed to bottom)

- Background: transparent
- Padding: `16dp` all sides
- Two side-by-side buttons, each ~45% width:

  **Left — "CANCEL SOS" button:**
  - Height: `52dp`, corner radius `26dp` (full pill)
  - Background: `#1A2B3C` (very dark navy, near-black)
  - Text: "CANCEL SOS" Roboto Bold 15sp, white, letter-spacing `+0.05em`
  - On tap: show `AlertDialog` confirmation "Are you sure you want to cancel the emergency?" with "Yes, Cancel" and "Keep Active" options. On confirm → emit `cancel_emergency` WebSocket event + pop activity.

  **Right — "102 POLICE" button:**
  - Height: `52dp`, corner radius `26dp`
  - Background: white
  - Border: `1.5dp` stroke, `color_emergency_red`
  - Text: "102 POLICE" Roboto Bold 15sp, `color_emergency_red`
  - On tap: `Intent(Intent.ACTION_DIAL, Uri.parse("tel:102"))` — pre-dials, does not auto-call

---

### 15.5 New File Additions from UI Spec

Add these to the file structure:

```
app/src/main/java/com/emergency/patient/
│
├── activities/
│   └── EmergencyActiveActivity.java   # NEW — Active emergency dashboard (Screen 3)
│
res/
├── layout/
│   ├── activity_lock_screen.xml       # Screen 1
│   ├── fragment_medical_id.xml        # Screen 2
│   ├── activity_emergency_active.xml  # Screen 3 (NEW)
│   ├── item_status_feed.xml           # RecyclerView row for Live Status Feed
│   └── item_contact_row.xml           # RecyclerView row for emergency contacts
│
├── drawable/
│   ├── ic_sos_cross.xml               # Vector cross drawable (coral-red)
│   ├── bg_sos_glow.xml                # Circle ShapeDrawable for glow halo
│   ├── bg_condition_chip.xml          # Pill stroke shape for condition tags
│   ├── bg_call_button.xml             # Green circle drawable
│   ├── bg_pill_dark.xml               # Dark navy pill for CANCEL SOS
│   └── bg_pill_outline_red.xml        # Red-outline pill for 102 POLICE
│
└── values/
    └── colors.xml                     # All tokens from Section 15.1
```

### 15.6 Updated AndroidManifest.xml Additions

```xml
<!-- New activity for active emergency screen -->
<activity
    android:name=".activities.EmergencyActiveActivity"
    android:showOnLockScreen="true"
    android:turnScreenOn="true"
    android:screenOrientation="portrait"
    android:exported="false"/>

<!-- New permission for CALL ALL button -->
<uses-permission android:name="android.permission.CALL_PHONE"/>
```

Add `CALL_PHONE` to `PermissionHelper.java` in the dangerous permissions list.

### 15.7 Updated Testing Checklist (UI)

- [ ] Lock screen shows patient's first name (not full name)
- [ ] SOS cross pulse animation loops correctly
- [ ] Time updates every second on lock screen
- [ ] Tap on cross/label launches countdown (replaces swipe from original spec)
- [ ] Medical ID screen shows all three card sections (profile, QR)
- [ ] "Doctor Access Only" badge renders on both Medical ID and Emergency screens
- [ ] Condition chip renders with orange border and light background
- [ ] Emergency banner red dot pulses continuously
- [ ] Tapping `×` on banner shows Snackbar (does not cancel SOS)
- [ ] Live Feed items render correctly in all three states (completed, in-progress, queued)
- [ ] Feed updates dynamically as dispatch steps complete
- [ ] "CALL ALL" button dials each contact in sequence
- [ ] "CANCEL SOS" shows confirmation dialog before cancelling
- [ ] "102 POLICE" opens dialler pre-filled with 102 (does not auto-call)
- [ ] Bottom nav active state highlights "Medical ID" tab in orange

---

> ⚠️ **Deployment Warning:** This application handles life-critical emergencies. Perform thorough end-to-end testing in both online and offline scenarios before any production release. Coordinate with the backend team to validate the SMS format, WebSocket event contract, and push notification schema before beginning Phase 3 and Phase 4 implementation.

---

*Document prepared for Antigravity development team. Questions? Refer to `/docs/api` on the backend repository.*
