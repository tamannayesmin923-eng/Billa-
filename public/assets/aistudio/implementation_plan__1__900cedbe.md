# Tammie — Encrypted Real-Time Messaging & Calling App

Tammie is a bilingual (Bengali and English) real-time Android messaging platform featuring end-to-end encrypted 1:1 and group chats, a Firebase Storage image and document file uploader with inline previews and progress tracking, live Firestore-signaled audio and video calls with camera preview, customizable user profiles with Last Seen presence, light/dark mode, and real-time notifications.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> Please review the confirmed product choices and backend notes below before approving execution.

- **Confirmed — Bilingual Interface**: Instant in-app language switcher between **English** and **বাংলা (Bengali)** persisted locally across sessions, covering navigation, chats, call controls, file sharing, profile settings, and error states.
- **Confirmed — Calm Teal & Slate Theme**: Material 3 Calm Teal & Cool Slate visual identity with full **Light**, **Dark**, and **System** appearance modes.
- **Confirmed — Audio & Video Calling**: Live **Firestore call signaling** (real-time ringing, accept, decline, hang-up, duration timer, mute, speaker, camera flip) paired with a live **CameraX** video preview during video calls.
- **Confirmed — Rich File Uploader**: Dedicated chat attachment component supporting **images** (zero-permission Android Photo Picker) and **documents** (PDF, Office docs, text, archives via Android Storage Access Framework) with **inline previews, live upload progress, cancel/retry, and one-tap download/open**.
- **Important Backend Provisioning Note**: During setup, AI Studio will automatically provision **Firebase Firestore Enterprise** and **Firebase Auth (Google Sign-In)** after you accept Firebase terms. **Firebase Storage** (bucket creation) and **Firebase Cloud Messaging (FCM)** server push credentials are not auto-provisioned by the tool—Tammie will include complete Firebase Storage SDK upload/download logic, storage security rules, and Android local + push notification channels ready to connect as soon as you enable Storage/FCM in your Firebase Console, plus an automatic graceful fallback notice if Storage is not yet activated.

---

## 1. Overview & Core Concept

- **What It Does**: Tammie connects people through real-time encrypted direct messages and group conversations. Users sign in with Google, discover or start chats with other Tammie users, create named group chats, share photos and documents with live upload/download progress, initiate real-time signaled audio or video calls with live camera preview, customize their profile and Last Seen visibility, switch between Bengali and English, and toggle Dark Mode.
- **Target Audience / Persona**: Everyday friends, families, and small teams—especially Bengali and English speakers—who want a calm, trustworthy, privacy-first mobile messenger that feels effortless on handheld phones and adapts cleanly to larger screens.
- **Key Value**: Combines end-to-end message encryption, real-time presence, media/document sharing, and live call signaling in one cohesive, distraction-free Material 3 Android experience.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Google Sign-In & Profile Bootstrap**:
   - First launch presents a calm branded welcome screen featuring custom editorial artwork, the bilingual language toggle right at the top, and a prominent **"Sign in with Google"** button (with silent auto sign-in for returning sessions).
   - On first sign-in, Tammie creates the user's profile document (display name, photo URL, default bio, online presence, and Last Seen privacy setting) and lands on the main workspace.
2. **Real-Time Chats & Group Creation**:
   - The **Chats** tab displays an active-now strip / search bar, filter chips (`All`, `Direct`, `Groups`), and real-time conversation cards showing avatar, online dot, last encrypted message preview (decrypted locally on-device), timestamp, and unread indicator.
   - Tapping the primary **FAB** opens a New Chat / New Group sheet where users pick a contact for an instant 1:1 chat or select multiple contacts + enter a group title and accent color to create a shared group chat.
3. **Encrypted Conversation & File Upload Component**:
   - Inside a chat, the top bar displays the contact or group name, real-time `Online` / `Last seen ...` status (or member count for groups), an **Encryption Verified** lock badge (tap to inspect the conversation's key fingerprint), and quick **Audio Call** and **Video Call** buttons.
   - Messages are encrypted client-side before leaving the device and decrypted upon arrival; each bubble shows status ticks and timestamp.
   - Tapping the **Attach (`+` / paperclip)** button in the composer opens the **File Upload Sheet** with two primary actions:
     - **Send Photo** (Android Photo Picker — zero broad storage permissions required)
     - **Send Document** (Android Document Picker for PDF, DOCX, TXT, CSV, ZIP, etc.)
   - Selected files appear in a **staged upload preview strip** above the composer (thumbnail for images, file icon + name + size badge for documents, optional caption).
   - Pressing Send starts a resumable **Firebase Storage** upload with a **live linear/circular percentage progress bar**, **Cancel**, and **Retry** on failure. Once uploaded, the message renders inline in the chat stream:
     - **Images**: Rounded inline image preview card that opens a full-screen pinch-to-zoom lightbox with a Download button.
     - **Documents**: Rich file attachment card displaying MIME badge (PDF/DOC/TXT), filename, formatted byte size, and a **Download / Open** action with live download progress.
4. **Live Audio & Video Calls**:
   - Tapping Audio or Video Call writes a live call signaling session to Firestore and opens the full-screen **Call Stage** (ringing -> connected -> ended).
   - Recipients receive an in-app floating **Incoming Call Banner** (and system notification alert) with **Accept** and **Decline** controls.
   - Audio calls feature a subtle animated acoustic wave backdrop, contact avatar, elapsed timer, Mute, Speaker, and End Call controls.
   - Video calls render a live **CameraX** preview with front/back camera flip, video toggle, mute, and end call controls (requesting `CAMERA` and `RECORD_AUDIO` runtime permissions only when needed).
5. **Profile, Last Seen, Dark Mode & Notifications**:
   - The **Calls** tab lists recent audio/video call logs with quick callback triggers.
   - The **Profile & Settings** tab lets users edit their Display Name, Status/Bio, and Avatar (upload custom profile photo or pick a curated avatar tone), toggle **Show Last Seen**, choose **Theme (System / Light / Dark)**, switch **Language (English / বাংলা)**, enable/manage **Notification Alerts** (`POST_NOTIFICATIONS` on Android 13+), and Sign Out safely.

### Visual Identity & Theme

- **Aesthetic Direction**: Calm, refined, privacy-forward modern messenger—airy spacing, layered cool-slate surfaces, and crisp teal accents that feel reassuring rather than noisy.
- **Color Palette & Mood (Material 3 Semantic Tokens)**:
  - *Light Mode*:
    - `primary`: Deep Calm Teal (`#0D7C7B`) — outgoing message bubbles, active indicators, primary CTAs
    - `primaryContainer`: Soft Ice Teal (`#D4F1EE`) — active nav pill, FAB container, encryption badges
    - `background`: Cool Mist Slate (`#F5F8FA`) — main canvas
    - `surface` / `surfaceContainerLow`: Crisp White (`#FFFFFF`) & Soft Slate (`#EDF2F5`) — incoming bubbles, conversation cards, composer bar
    - `onSurface`: Deep Ink Slate (`#10212B`) & Muted Slate (`#516673`)
    - `tertiary`: Warm Amber (`#C87A19`) — encryption verification & document accent
    - `error`: Coral Crimson (`#D63B3B`) — decline/end call, upload failure
  - *Dark Mode*:
    - `primary`: Luminous Seafoam Teal (`#4FD1C5`)
    - `primaryContainer`: Deep Teal Slate (`#133D40`)
    - `background`: Midnight Slate (`#0C141A`)
    - `surface` / `surfaceContainerLow`: Dark Slate (`#131F28`) & Elevated Slate (`#1A2A35`)
    - `onSurface`: Crisp Ice White (`#E6F0F4`) & Cool Gray (`#94AAB7`)
- **Typography & Hierarchy**:
  - Bundled locally via `font-util` (zero runtime font download failures): **Plus Jakarta Sans** for headings/display titles and **Hind Siliguri** for crisp bilingual Bengali + Latin body legibility and message bubbles.
  - Clear visual hierarchy: spacious 24sp screen headers, 16sp high-readability chat text with 1.4x line height, and 11sp tabular timestamps/file-size metadata.
- **Component Styling & Adaptive Layout**:
  - Edge-to-edge rendering with proper `WindowInsets` (`safeDrawing`, `imePadding`, `navigationBars`) so the chat composer glides smoothly above the software keyboard.
  - Handheld screens use a Material 3 `NavigationBar` (`Chats`, `Calls`, `Profile`); expanded tablet/foldable screens adapt with a `NavigationRail` and spacious **List-Detail** chat split pane (`widthIn` bounded reading columns).
  - Custom adaptive app launcher icon (calm teal monogram/bubble emblem) and bespoke generated artwork for the sign-in hero and empty chat state.

### Interactive Feedback & Motion

- Spring-driven message arrival transitions (`< 250ms`), smooth `AnimatedVisibility` for the staged attachment strip and incoming call banner, deterministic upload/download progress bars, shimmer/skeleton placeholders while initial snapshots load, friendly bilingual empty states, and non-blocking snackbars with Retry actions on network or storage errors.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1 — Client-Side Message Encryption (AES-256-GCM)**
  - *Chosen Approach*: Encrypt every text message payload client-side using Android's `javax.crypto` **AES-256-GCM** with random 96-bit IVs and a deterministic per-conversation symmetric key derived from the conversation secret/salt, storing only ciphertext (`ciphertext`, `iv`, `encryptionVersion`) in Firestore and displaying a verifiable key fingerprint in chat info.
  - *Why*: Protects message content in Firestore without external key servers, works seamlessly for both 1:1 and multi-member group chats across sign-ins, and keeps real-time snapshot rendering instantaneous.
  - *Alternatives Considered*: Plaintext storage (violates encryption requirement) or multi-device Signal Double Ratchet server (requires dedicated key-distribution backend not available in serverless Firestore).

- **Decision 2 — Dedicated File Upload Component with Firebase Storage + Play-Compliant Pickers**
  - *Chosen Approach*: Use Android's zero-permission **Photo Picker** (`PickVisualMedia`) for images and **Storage Access Framework** (`OpenDocument`) for documents, validate file size (e.g., 15 MB limit for Storage), upload via **Firebase Storage** (`putStream`/`putFile` with `OnProgressListener` emitting real-time percentage into StateFlow), and store the secure download URL + metadata (`fileName`, `mimeType`, `fileSizeBytes`, `storagePath`, `attachmentType`) in the Firestore message document. Provide matching `storage.rules` enforcing participant-scoped authenticated access.
  - *Why*: Meets Google Play privacy policy (no `READ_EXTERNAL_STORAGE` or `READ_MEDIA_IMAGES` permissions), gives users crisp progress feedback and inline Coil image/document previews, and scales to large PDFs and high-res photos once the Storage bucket is enabled in Firebase Console.
  - *Alternatives Considered*: Storing large files directly in Firestore documents (hits Firestore's 1 MiB document limit and degrades real-time sync performance). If Firebase Storage returns a bucket-not-configured error before console activation, the component clearly informs the user (and offers an optional inline compressed preview fallback for small images under 300 KB so testing never dead-ends).

- **Decision 3 — Real-Time Call Signaling with Live CameraX Preview**
  - *Chosen Approach*: Synchronize live call state (`ringing`, `connected`, `declined`, `ended`, `missed`) and participant audio/video mute flags in Firestore with real-time snapshot listeners, combined with **CameraX** `PreviewView` for live local video capture and Android `AudioManager` speakerphone/mute controls.
  - *Why*: Provides an authentic, responsive cross-device ringing, accept/decline, and live video call experience without requiring external TURN/STUN media relay servers.
  - *Alternatives Considered*: Self-hosted WebRTC TURN infrastructure (requires paid external relay servers and credentials outside Firebase).

- **Decision 4 — Notifications Architecture (Android Notification Channels + FCM Readiness)**
  - *Chosen Approach*: Implement Android `NotificationCompat` channels (`tammie_messages`, `tammie_calls`) with Android 13+ `POST_NOTIFICATIONS` runtime permission flow, automatically posting local system notifications when new incoming messages or calls arrive via active Firestore listeners while the chat is unfocused, alongside a clean token registration hook for Firebase Cloud Messaging when enabled in the Firebase Console.
  - *Why*: Ensures users immediately experience working message and incoming-call notifications out of the box while keeping FCM background push ready for console setup.

- **Decision 5 — Data Persistence & Authentication Boundary**
  - *Chosen Approach*: **Firebase Firestore** (using the provisioned named database ID, never `(default)`) for all durable user, conversation, message, and call data; **Firebase Auth with Google Sign-In via Jetpack Credential Manager** (`GetSignInWithGoogleOption` + silent `GetGoogleIdOption`) gating all authenticated UI and listeners; **Jetpack DataStore Preferences** for local-only Theme mode, Language (`en` / `bn`), and Notification preference toggles.
  - *Why*: Matches durable multi-user real-time messaging requirements with zero-trust Firestore security rules tested locally against the Firestore emulator before deployment.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```text
┌───────────────────────────────────────────────────────────────────────────┐
│                         UI Layer (Jetpack Compose M3)                     │
│  ┌─────────────┐   ┌───────────────────────────────────────────────────┐  │
│  │ Auth Screen │──▶│          Authenticated Shell (key = uid)          │  │
│  │ Google Sign │   │  ┌──────────────┐ ┌──────────────┐ ┌───────────┐  │  │
│  │  In + Lang  │   │  │ Chats & Group│ │ Active Chat  │ │   Calls   │  │  │
│  └─────────────┘   │  │   List View  │ │  + Composer  │ │  History  │  │  │
│                    │  └──────────────┘ │  + File      │ └───────────┘  │  │
│  ┌─────────────┐   │                   │    Uploader  │ ┌───────────┐  │  │
│  │  Call Stage │◀──┼──────────────────▶│  + Inline    │ │ Profile & │  │  │
│  │  (CameraX + │   │                   │    Previews  │ │ Settings  │  │  │
│  │  Signaling) │   │                   └──────────────┘ └───────────┘  │  │
│  └─────────────┘   │  [Floating Incoming Call Banner Overlay]          │  │
│                    └───────────────────────────────────────────────────┘  │
└────────────────────────────────────┬──────────────────────────────────────┘
                                     │ StateFlow / Events
┌────────────────────────────────────▼──────────────────────────────────────┐
│                       Presentation Layer (ViewModels)                     │
│  Auth & Session VM │ Conversations VM │ Chat & Upload VM │ Call VM │ Prefs│
└───────────┬──────────────────┬──────────────────┬──────────────┬──────────┘
            │                  │                  │              │
┌───────────▼──────────────────▼──────────────────▼──────────────▼──────────┐
│                     Domain & Services Layer                               │
│  AES-256-GCM Cipher │ Bilingual Strings Engine │ Android Notification Mgr │
└───────────┬──────────────────┬──────────────────┬──────────────┬──────────┘
            │                  │                  │              │
┌───────────▼──────────────────▼──────────────────▼──────────────▼──────────┐
│                       Data & Infrastructure Layer                         │
│  ┌────────────────────┐ ┌────────────────────┐ ┌────────────────────────┐ │
│  │ Firebase Auth      │ │ Cloud Firestore    │ │ Firebase Storage       │ │
│  │ (Credential Mgr +  │ │ (Named DB ID +     │ │ (Resumable Image & Doc │ │
│  │  Google Sign-In)   │ │  Zero-Trust Rules) │ │  Upload / Download)    │ │
│  └────────────────────┘ └────────────────────┘ └────────────────────────┘ │
│  ┌────────────────────┐ ┌────────────────────┐                            │
│  │ DataStore Prefs    │ │ CameraX & Android  │                            │
│  │ (Theme, Lang, Notif│ │ Photo/Doc Pickers  │                            │
│  └────────────────────┘ └────────────────────┘                            │
└───────────────────────────────────────────────────────────────────────────┘
```

### Data Model & State

- **`users/{uid}`**:
  - Profile fields: `uid`, `displayName`, `email`, `photoUrl`, `bio`, `avatarColorHex`, `showLastSeen` (`Boolean`), `online` (`Boolean`), `lastSeenAt` (server timestamp), `updatedAt` (server timestamp).
  - Security: Any authenticated user can read basic directory profiles to start chats; only `request.auth.uid == uid` can create or update their own profile.
- **`conversations/{conversationId}`**:
  - Metadata: `type` (`DIRECT` | `GROUP`), `title` (group name or empty for direct), `memberUids` (`List<String>`), `memberNames` (`Map<String, String>`), `adminUid`, `encryptionSalt`, `lastMessageCiphertext`, `lastMessageIv`, `lastMessageType`, `lastMessageSenderId`, `lastMessageAt` (server timestamp), `createdAt` (server timestamp).
  - Security: Only authenticated users whose `uid` is in `memberUids` can read, update, or post messages in the conversation.
- **`conversations/{conversationId}/messages/{messageId}`**:
  - Message payload: `senderId`, `senderName`, `memberUids` (denormalized for zero-`get()` security rule checks), `messageType` (`TEXT` | `IMAGE` | `DOCUMENT`), `ciphertext`, `iv`, `encryptionVersion`, optional attachment fields (`attachmentUrl`, `attachmentPath`, `attachmentName`, `attachmentMimeType`, `attachmentSizeBytes`, `inlinePreviewBase64` for optional small-image fallback), `createdAt` (server timestamp).
  - Security: Read allowed iff `request.auth.uid in resource.data.memberUids`; create allowed iff `senderId == request.auth.uid` and `request.auth.uid in request.resource.data.memberUids` with strict field/size validation.
- **`calls/{callId}`**:
  - Signaling fields: `conversationId`, `callerUid`, `callerName`, `calleeUids` (`List<String>`), `participantUids` (`List<String>`), `callType` (`AUDIO` | `VIDEO`), `status` (`RINGING` | `CONNECTED` | `DECLINED` | `ENDED` | `MISSED`), `startedAt`, `endedAt`.
  - Security: Read/update allowed iff `request.auth.uid in participantUids`; create allowed iff `callerUid == request.auth.uid` and `callerUid in participantUids`.
- **Firebase Storage Path Strategy**:
  - `chat_attachments/{conversationId}/{senderUid}/{uuid}_{sanitizedFileName}`
  - Accompanied by a ready-to-deploy storage security rules file restricting reads/writes to authenticated users with a 15 MB per-file size cap and allowed image/document content types.
- **Local Preferences (DataStore)**:
  - `themeMode` (`SYSTEM` | `LIGHT` | `DARK`), `languageCode` (`EN` | `BN`), `notificationsEnabled` (`Boolean`).

### Interactive Component & State Mapping

- **Auth & Language Toggle**: Tapping Google Sign-In launches single-option `GetSignInWithGoogleOption` via Credential Manager; cancellation resets loading cleanly with diagnostic logging; toggling `EN / বাংলা` immediately updates DataStore and recomposes all localized UI strings.
- **Presence & Last Seen Lifecycle**: App foreground/background lifecycle observers update `users/{uid}` (`online = true/false`, `lastSeenAt = serverTimestamp()`) whenever signed in; chat headers respect each peer's `showLastSeen` privacy flag.
- **New Chat / Group Builder**: Searches live Firestore `users` directory; selecting 1 contact creates/opens a deterministic `DIRECT` conversation (`minUid_maxUid`); selecting multiple contacts + group name validates input and creates a `GROUP` conversation with a generated encryption salt.
- **File Upload Component (Chat Composer + Progress Bubble)**:
  - Photo button triggers `PickVisualMedia(ImageOnly)`; Document button triggers `OpenDocument(arrayOf("application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.*", "text/*", "application/zip"))`.
  - Staging state resolves filename, MIME type, and byte size from `ContentResolver`, validates max size (15 MB), and displays an editable preview bar with a remove button.
  - Sending enters `UploadState.Uploading(progress = 0f..1f)` linked to Firebase Storage's `UploadTask`, exposing Cancel (`task.cancel()`) and Retry controls; upon completion it retrieves `downloadUrl`, encrypts any caption, writes the Firestore message, and clears staging state.
  - Received image/document bubbles render via Coil (images) or rich document cards; tapping Download streams the file into the app cache / Downloads via Android `FileProvider` or `DownloadManager` with live progress and opens it via `Intent.ACTION_VIEW`.
- **Call Signaling & Camera Stage**:
  - Initiating an audio/video call writes a `RINGING` call document and navigates to the Call Stage; incoming `RINGING` calls for `uid` trigger the floating top banner and notification alert.
  - Accepting updates `status = CONNECTED` and starts the live timer; video calls request `CAMERA` + `RECORD_AUDIO` permissions and bind CameraX `Preview` with front/back lens switching; Mute/Speaker toggles update audio routing; End Call writes `ENDED` and dismisses the call screen.

### Key Sequences & Integrations

1. **Firebase Provisioning & Rules Gate**: Extract debug keystore SHA-1/SHA-256 -> present Firebase setup consent UI for `["firestore"]` -> verify ToS & run `ProvisionFirebase` -> verify certificate hash in `google-services.json` -> author schema blueprint and zero-trust `firestore.rules` -> validate all read/write/cross-user denial paths against the local Firestore emulator (`firestore.test.js`) -> deploy rules via `DeployRules`.
2. **Dependencies & Permissions**: Enable pre-configured Firestore, Auth, Credential Manager, Navigation Compose, DataStore, Coil, and CameraX dependencies plus `firebase-storage` from the pinned Firebase BoM; declare only least-privilege permissions (`INTERNET`, `CAMERA`, `RECORD_AUDIO`, `POST_NOTIFICATIONS`) with Compose runtime permission launchers—zero broad storage permissions.
3. **Visual Polish**: Generate custom adaptive launcher icon for Tammie, install `Plus Jakarta Sans` and `Hind Siliguri` locally via `font-util`, and generate calm teal/slate onboarding and empty-state artwork.
