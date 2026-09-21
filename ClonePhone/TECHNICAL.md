# rebaritclone 7.8 test

Native Android application (Java, minSdk 26, compileSdk 35, targetSdk 34). Original interface with separate role, category, pairing, transfer and result screens. Local TCP on port 39841. Existing app ID and development signing key retained for in-place upgrade; versionCode 19.

## Pairing and transport

Receiver enumerates IPv4 Wi-Fi link addresses and hotspot interfaces, excluding cellular/VPN interfaces. A local-only QR contains a version tag, up to eight RFC1918 addresses and a fresh 128-bit secret. The camera scanner and image picker decode QR using embedded ZXing core 3.5.3 (Apache-2.0; license in third-party). No external scanner, Play Services or online QR generation is used. Clipboard pairing is a fallback; mark the clipboard payload sensitive on Android 13+.

HotspotLink requests a LocalOnlyHotspot on the receiver. Its v3 QR carries SSID and WPA2 credentials plus private addresses and the transfer secret. Android 10+ senders use WifiNetworkSpecifier without NET_CAPABILITY_INTERNET, with system user consent, then bind transfer sockets to the returned Network. No default cellular socket fallback is used. Older senders and OEM failures use a manual hotspot/Wi-Fi flow. The app never fetches an online service. Nearby Wi-Fi permission is requested on Android 13+; fine/coarse location permissions on older systems are required by Wi-Fi APIs, not used to read geographic location. QR credentials are secrets: share only between owned phones.

The local hotspot's band is chosen by Android. The normal local-only-hotspot path does not force 5 GHz. Performance mode first tries a 5 GHz Wi-Fi Direct group where Android reports support, showing an explicit failure and user-selected fallback when creation fails. Its manual flow suggests 5 GHz only where both devices support it. Network request and hotspot reservation are released on completion/cancellation/destruction. Sender confirms receipt of the final batch ACK before receiver tears down its hotspot. OEM interface names, hotspot availability and physical network behavior remain untested on devices. Wi-Fi Direct group-owner creation and an experimental AOA USB transport are now implemented, as detailed below.

Wire v4 uses CP04 magic, a random 256-bit receiver session challenge, HMAC-SHA256-derived independent AES-256-GCM directional keys and monotonic 96-bit nonces. Each file has an authenticated EOF, SHA-256 digest and acknowledgement. Metadata is sent as separate bounded records, avoiding the original single-frame manifest limit. Count limit: 10000 files; frame limit: 1 MiB; file chunks: 256 KiB. The receiver rejects old wire versions and unauthenticated attempts and waits for another client until timeout. Both phones must upgrade. No forward secrecy or third-party security audit is claimed.

## Selection and storage

Category requests use just-in-time READ_MEDIA_* / legacy READ_EXTERNAL_STORAGE permissions. Android 14 selected-visual-access is supported and explicitly reported as partial access. MediaStore lists only accessible indexed media. Manual file selection uses SAF without broad storage permission. Duplicate source URIs are removed before transfer. Contacts require READ_CONTACTS and are exported by the platform vCard provider to an app-private cache file; receiver offers ACTION_VIEW for the user to import through Contacts, not silent database restoration.

Android 10+: received images, videos and audio use the corresponding MediaStore collection and rebaritclone/batch directory, with IS_PENDING until verification and output close. Other files use MediaStore.Downloads. No WRITE_EXTERNAL_STORAGE permission is needed. API 26-28 uses a user-selected SAF directory with .partial then rename. All copying is streamed, source files are never deleted. Each batch uses a new directory. Failed transfers attempt to delete the incomplete destination; process death may leave pending/partial entries. A lost acknowledgement after commit may leave a completed file despite sender reporting failure.

MediaStore metadata/permissions can redact location data; original EXIF, timestamps, albums and filesystem layout are not guaranteed preserved. There is no automatic private app data, WhatsApp, settings or account migration. Selected SMS and call records can be exported as JSON backups, but are not restored to the native Phone/Messages databases. Contacts must be explicitly imported and may duplicate existing entries. Version 6 supports in-Activity session resume; there is no deduplication across independent batches or recovery after process death.

## Lifecycle and build

The Activity keeps the screen awake during a session; a foreground transfer service is not implemented. Keep both apps open. Stop closes sockets and releases pending approval. Process death and OEM battery/network behavior remain device-test requirements. QR scanner releases the camera on pause. Read timeout is 180 seconds, user acceptance 150 seconds, listener lifetime ten minutes. No application-level socket write deadline exists; Stop closes the socket.

APK built using the included SDK-only build-local.sh (javac, aapt2, d8, zipalign, apksigner); Gradle project also supplied. Bundled ZXing core is included in both build paths. Public development keystore password: android, alias: androiddebugkey. Use a separate private release signing setup for production. No Play Store publication performed.

## Verification and limits

- Full Java compilation, resources, DEX and APK signature verification.
- Encrypted 2 MiB duplex payload, empty/end records, wrong key, invalid code and tampering rejection.
- Pairing local-address validation and invalid payload rejection.
- QR encode/decode through both bitmap and NV21 luminance paths, at 280/660 pixels and four rotations.
- 2000-file Unicode metadata stream exceeding old 128 KiB manifest cap; disconnect handling.
- NOT RUN: physical-device installation, native UI screenshots, camera focus/preview, MediaStore/SAF behavior, Contacts import, two-device Wi-Fi/hotspot transfer and OEM lifecycle tests. Camera QR tests use synthetic frames and do not establish physical camera performance.

Primary references:
https://developer.android.com/training/data-storage/shared/media
https://developer.android.com/training/data-storage/shared/documents-files
https://github.com/zxing/zxing

## Buffered transport update
Network input/output use 512 KiB buffers, file I/O uses 256 KiB buffers, socket buffers request 1 MiB (actual size controlled by OS). Data frames use a writeBuffered method to avoid per-chunk flush and Arrays.copyOf; EOF/control writes flush explicitly. AES-GCM Cipher instances are reused but initialized with unique monotonic nonces. Peak buffers stay bounded independent of file size. SHA-256 remains enabled. Wire v4 is intentionally incompatible with earlier APKs; upgrade both phones.

FastStreamTest verifies 64 MiB plus a short 37-byte tail, digest, EOF flush and acknowledgement. Host loopback timing is a diagnostic, not evidence of real-phone throughput or an Ultra Fast guarantee. Actual two-phone hotspot/system-consent testing has not been performed.

## Version 4 changes

Version 5 defaults the new fast5 preference to true, including upgrades from v4. New-phone role immediately starts permission → Wi-Fi settings if disabled → location settings on API <33 if disabled → host creation → QR. System permissions remain user-granted. Settings return checks prerequisites instead of repeatedly opening a cancelled panel; API26–28 folder selection resumes setup. USB/manual setup remains accessible from home.

Performance mode requests only a 5 GHz P2P group (API29+), verifies WifiP2pGroup.getFrequency before showing QR and does not silently fall back to 2.4 GHz. Unsupported/failed/unknown-band cases show explicit retry, normal-hotspot, manual or USB choices. Normal mode retains LocalOnlyHotspot → auto-band P2P fallback. A 45-second setup deadline reports failure instead of indefinite waiting. Receiver diagnostics use only the created group frequency; unknown hotspot band is explicitly unknown. Sender diagnostics use only WifiInfo from its bound Network, never unrelated infrastructure getConnectionInfo. These changes do not establish measured throughput improvement.

High performance uses 1 MiB file chunks and file buffers instead of 256 KiB, within the same 1 MiB wire frame bound, and requests WIFI_MODE_FULL_HIGH_PERF only during Wi-Fi transfer. This is a tuning option, not a measured improvement or Ultra Fast guarantee; Android/OEMs may ignore/deprecate the lock. The option may increase memory/power usage. The user supplied a v3 screenshot showing approximately 7.7 MB/s on actual phones; that confirms a v3 transfer was in progress, not its completion or the bottleneck. No controlled before/after speed test on their devices was possible.

## USB AOA prototype

New phone is USB host; old phone is Android Open Accessory. UsbLink obtains explicit Android USB permissions, probes AOA version with vendor request 51, supplies all identifying strings including version with request 52, starts accessory mode with request 53, then handles re-enumeration and fresh permission. Accessory attach metadata matches rebaritclone / Phone Transfer / 1.0. Host selects only AOA bulk interfaces, excluding ADB. Old phone opens its accessory ParcelFileDescriptor. USB debugging is not used. Data cable, host/accessory capabilities and correct USB roles are required; not all Android phone pairs support this.

UsbSession bootstraps a fresh key over the physically connected cable, then uses SecureChannel and exactly the same shared sendFiles/receiveFiles routines as Wi-Fi (manifest, acceptance, digests and acknowledgements). Physical cable and user USB permissions are the trust boundary: the key bootstrap is not authenticated against a malicious physical USB interposer. No claim of protection from such an interposer is made. USB input preserves byte-stream semantics across bulk reads, and host bulk requests are capped at 16 KiB for compatibility with older Android APIs. AOA is not MTP and cannot concurrently expose the same Android device as MTP.

USB setup polls for up to two minutes; host bulk I/O has 180-second timeouts. The accessory FileDescriptor side has no application-level read deadline; Cancel closes descriptors, and unplug normally surfaces an I/O failure. Activity/foreground-only lifecycle limitations remain. AOA and physical USB-C throughput have not been measured. A USB-C connector does not establish a guaranteed data rate.

UsbSessionTest uses fragmented simulated streams, full 1 MiB encrypted records, a short tail, SHA-256, final acknowledgement and invalid-header rejection. It verifies only the transport-independent session/record layer, not UsbManager, AOA switching, permissions, USB roles, host bulk I/O or physical performance. Real two-phone hotspot/P2P/USB/UI tests remain outstanding.


Version 5 verification: Android build and signing checked. Physical permissions/settings return, 5 GHz P2P creation and before/after throughput remain untested. Official band API: https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pConfig.Builder#setGroupOperatingBand(int)


## Version 6: interrupted-transfer resume and QR speed control

High-performance control is removed from home/receiver setup and placed below the QR. Switching it cancels the waiting listener, releases the current host, and starts a new host/QR with the selected preference. Both phones must use v6: CP04 and v4 key-derivation context reject earlier wire formats.

Sender retains a random batch UUID while a transfer is incomplete, tied to ordered source URI/name/MIME/size metadata. Receiver retains UUID, manifest, publication directory and file entries in Activity memory. Unknown/changed batches require acceptance and replace the previous partial session. Socket-only errors get two automatic sender retries while the network is still available; receiver keeps its listener for reconnection for up to ten minutes. Lost Wi-Fi network/hotspot requires new pairing via Resume; USB requires reconnecting and selecting USB again. Original file selections must remain unchanged. Reconnecting an already accepted UUID+manifest does not prompt again.

ResumableFile stages only fully authenticated decrypted records in an app-cache file. Each retry negotiates byte offset, completion flag and SHA-256 prefix digest. Sender reads/hash-checks the retained prefix before transmitting remaining bytes. Mismatched partial prefix is cleared and the session stops with SOURCE_CHANGED; completed entries are not overwritten. Full digest and declared size are verified before publication to MediaStore/SAF. Entry is marked done before ACK, allowing a lost per-file ACK to skip re-publication. Completed prefix checks require local reads but no retransmission of file contents. Resumed bytes are excluded from reported speed. Empty files and unknown sizes are supported.

A staged file plus its final destination temporarily require up to twice that file's space. Publication copies add I/O and are not a throughput optimization. Partial cache files may be evicted by Android; a missing partial starts at zero. UUID/commit ledger is not durable: Activity destruction, process death or device restart loses resume state. No crash-safe exactly-once publication is claimed. Orphan cache files are subject to Android cache cleanup. User cancellation preserves this Activity's checkpoint for an explicit retry. Source changes are rejected, and source files are never modified.

ResumeTest exercises the production ResumableFile and SecureChannel over local sockets: interruption after 1 MiB, retained byte offset, reconnect/tail/digest, lost publication ACK without duplicate publish, corrupt prefix rejection/reset, subsequent retry, and empty-file deduplication. Android UI lifecycle, settings, Wi-Fi/USB reconnect and performance still require physical device testing. User screenshot confirms v5 created a 5765 MHz Wi-Fi Direct group on their phone; it does not establish throughput.


## Version 7: selected application transfer and consent-based installation

Apps category queries visible MAIN/LAUNCHER activities through an explicit manifest <queries> intent, deduplicates packages, and excludes system apps and rebaritclone itself. No QUERY_ALL_PACKAGES permission is used. A multiple-choice list exports only selected apps. Each .rbapp is a bounded ZIP containing byte-identical ApplicationInfo.sourceDir (base.apk) and splitSourceDirs (split-N.apk). Export checks lastUpdateTime/version before/after to reject concurrent package updates. No private app data, OBB, asset packs, accounts or credentials are read. Export snapshots consume sender cache space.

Verified publication of .rbapp/.apk received files adds their URI to the current batch's install queue. After successful batch transfer, AppInstallActivity opens automatically. On partial-transfer failure the result page allows installation of already verified received apps. Installation uses PackageInstaller MODE_FULL_INSTALL sessions, streams base and all splits together, fsyncs and closes all outputs before commit, and requests USER_ACTION_NOT_REQUIRED on API31+ subject to Android enforcing eligibility. REQUEST_INSTALL_PACKAGES is declared; canRequestPackageInstalls gates the user-controlled unknown-sources settings screen. No silent install, root, accessibility automation or device-owner bypass is used.

Installer callbacks use a mutable app-scoped PendingIntent with a per-Activity random action and a non-exported dynamic receiver on API33+. Session IDs are checked. Pending user action opens Android's provided confirmation intent only while this Activity is resumed. Success proceeds to the next app; terminal install failures/denial and staging failures are recorded as skipped and advance automatically. Unknown-source permission denial pauses the queue. Existing apps are never uninstalled to resolve a conflict. Installer Activity is not exported. Cancel/destruction abandons its active session. A restored Activity does not automatically replay the queue; the user must resume explicitly. Process-death recovery of the transfer/install queue is not guaranteed.

AppBundle accepts only base.apk and numbered split APK entries, rejects duplicates/path traversal/extra entries/missing base, bounds entry count to 256 and cumulative decompressed bytes to 16 GiB, and never extracts ZIP paths to the filesystem. Validation must complete before a PackageInstaller session commits; Android validates actual APK structure, package/split consistency, signatures, ABI and compatibility. Standalone APKs selected through Files use a single session entry. Copied device-specific splits can be incompatible with a different device; no installability guarantee is made.

AppBundleTest: actual signed test APK + synthetic split byte-for-byte roundtrip, base-only archive, malformed/truncated container, duplicate/path traversal/extra entry/missing base/count overflow and incomplete sink staging rejection. Full Android compile/signature verification passes. Physical app list, unknown-source settings, Android install/update confirmation, split compatibility, cancel/status delivery, and low-storage/OEM lifecycle behavior have NOT been device-tested.

Official references:
https://developer.android.com/reference/android/content/pm/PackageInstaller.Session
https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setRequireUserAction(int)
https://developer.android.com/training/package-visibility/declaring


## Version 7.1 installation queue

Terminal success/skip outcomes and counts are visible in the Activity and saved in its instance state. Rejected/incompatible/staging-failed apps do not block subsequent entries. UPDATE_PACKAGES_WITHOUT_USER_ACTION plus USER_ACTION_NOT_REQUIRED requests the documented Android12+ automatic-update path, but Android determines eligibility (installer/update ownership and installed app target SDK conditions). It does not enable unattended first-time app migration. Pending user-action callbacks are still handled normally. No Play Store approval status is inferred from an APK or app listing, and no installer identity is forged. System install restrictions, user denial and signature checks are honored. Build/signing verified; real system approval/skip/automatic update behavior is not device-tested.


## Version 7.2 selection and live statistics

TransferStats computes manifest byte totals, unknown-size state and completion-prefix counts for photos/videos/apps/other. MIME classification includes common media-extension fallback. Category cards and deduplicated selection/connection summaries show decimal GB. During transfer, both roles show total/moved/remaining bytes, acknowledged file totals, remaining per-type counts, average decimal MB/s excluding resumed bytes, and estimated time remaining. An in-flight file remains pending until final completion; percentage is capped below 100% before all files finish. Unknown total size disables a fabricated remaining byte count or ETA; tiny positive sizes display <0.001 GB. ETA has a three-second warmup and indicates verification/publication when all known bytes have arrived but files are not completed. This does not change wire format or copying semantics.

TransferStatsTest passes mixed-type totals, completion boundaries, partial/resumed byte remaining, unknown sizes, empty input and decimal unit formatting. Full Android compilation and APK signing pass. Native layout on physical devices has not been visually tested.


## Version 7.3: Kurdish, English and Arabic

Home has a permanently multilingual language selector (native language names). UiText stores the selection in private language/selected SharedPreferences, defaults to Kurdish, and updates the home screen without recreating MainActivity or dropping file selection. MainActivity, scanner and installer initialize their UI language from that preference. Root layout direction and in-app dialog configuration follow the choice: English LTR, Kurdish/Arabic RTL. Each device selects independently.

229 unique app-owned strings across MainActivity, AppInstallActivity, ScanActivity, UsbLink and HotspotLink are localized via stable IDs. Dynamic text fragments are translated before concatenation; file/app names, credentials, protocol fields and diagnostic codes are not passed through string replacement. Translations.java is generated by localization/generate.py from ckb.json and translations.tsv; both are included in source. The existing SDK-only builder does not need generated Android R.java for these catalogs. Android-owned permission/install/settings UI keeps its system-selected language.

TranslationsTest verifies all 229 entries in all three languages, no Kurdish/Arabic script in English strings, fallback, direction, line breaks and spacing of dynamic fragments. Source audit confirms no unlocalized Kurdish UI literals except intentional native language labels. Full APK build/signature verification passes. No physical UI screenshots or runtime language persistence test on an Android device was available; these remain device-test items. Transfer protocol and app-install policy are unchanged.


## Version 7.4: selected personal records

Contacts, recent calls and SMS each have a separate multi-choice picker with search, select-all-visible and clear. Selection persists during the current Activity session. Contacts export selected provider-generated VCF records; SMS/calls export version-1 rebaritclone-records UTF-8 JSON with IDs, timestamps and original fields. One immutable cache snapshot per selected category enters the existing transfer pipeline. The category shows record count; transfer counts snapshot files. Failed export preserves the previous selection and discards the partial snapshot. Receiver shows backup-only guidance; existing explicit VCF import remains available.

READ_CONTACTS, READ_CALL_LOG and READ_SMS are requested on demand. READ_SMS/READ_CALL_LOG are hard-restricted Android permissions and may be blocked by the OS/installer; denial is explained. No default-SMS role, native write, SMS send or permission bypass is implemented. JSON is not claimed compatible with third-party restoration tools. MMS/RCS/WhatsApp excluded. Pickers cap at 5000 contacts / newest 5000 calls or SMS and show a limit notice. Search operates on displayed previews, not the full SMS body. Deleted/unreadable rows fail export rather than silently omit chosen data.

246 translated strings now cover Kurdish, English and Arabic. Synthetic RecordJson output independently parsed with Python verifies Unicode, control escaping, string phone numbers, nulls, long timestamps, selected fixture exclusion, counts and empty backups. This tests serialization, not Android provider or picker behavior. Full APK builds and signs. Physical permission, provider compatibility, picker and two-device tests remain unrun.


## Version 7.4.1: Activity-backed localized dialogs

UiText.context previously returned Activity.createConfigurationContext, a standalone resource context without the Activity WindowManager/window token. Every localized AlertDialog used it, including language selection and both the calls picker and permission-denial alert. This is a concrete BadTokenException risk consistent with the reported exits, but no device stack trace was available to confirm the observed exception. It now requires an Activity and returns ContextThemeWrapper with that Activity as its base, its existing theme and an override containing only locale/layout direction. This preserves Activity window services while localizing dialog resources. All callers compile against the Activity-only signature. No broad catch hides dialog failures; record permission/export behavior is unchanged.

Build/signing, certificate continuity and translation checks pass. No emulator/system image or attached Android device is available. Required device regression: open/dismiss language picker, switch all three languages and restart; open recent calls with permission allowed/denied; open SMS/contacts pickers, search/select/cancel/apply; confirm denial alerts and other localized dialogs remain visible without app exit.


## Version 7.5: role-entry permission queue and transfer notifications

Role cards start a sequential, SDK-aware runtime permission queue. Granted groups are skipped. Denial/swipe advances once, with a final optional app-settings/continue screen; it never loops until consent. Receiver requests notification and Wi-Fi discovery permissions only. Sender also requests media, camera and read-only contacts/calls/SMS. Android 12 and earlier request coarse+fine location together; 13+ requests Nearby Wi-Fi, media types and POST_NOTIFICATIONS; 14+ includes selected-visual-media support. No broad system-file, overlay or SMS-send permission. SAF remains the documents route, legacy receiver folder selection stays in hotspot setup, and package-install access stays at installation. Restricted call/SMS permissions may remain unavailable.

Notification channel `transfer` has LOW importance and PRIVATE lockscreen visibility. Transfer updates are throttled to 1.5 seconds, with immutable content PendingIntent and no filenames/message bodies. Final notification reports success/failure; progress notice is removed when an active Activity is destroyed. No foreground service or process-death resume was added; keeping the app open remains required. Permission prompts and notification display are ultimately controlled by Android, including suppressed repeated denials/channel blocking.

PermissionPlanTest covers role/SDK boundaries 26,28,29,31,32,33,34,35,36, duplicates and forbidden unrelated grants. All 252 translations compile and validate. APK signing and upgrade certificate continuity pass. Runtime permission callbacks, denied/restricted/selected-photo branches, settings return, notification delivery and physical hotspot flow remain device-test requirements.


## Version 7.5.1: transient Wi-Fi Direct BUSY recovery

Screenshot on Samsung SM-A566B Android 16 shows P2P_ERROR_2. Android defines 2 as BUSY, not lack of 5 GHz support. createGroup now retries BUSY with 750/1500/3000 ms backoff; each delayed attempt checks the session generation. Other errors are not retried. After forced-5GHz BUSY exhaustion, one automatic local-only-hotspot attempt is made with require5 cleared. If local hotspot fails, the existing AUTO-band P2P fallback may run with the same bounded retries, then reports failure; no loop back to local hotspot. Successful fallback is explicitly labeled on QR/receiver diagnostics and never asserts a 5 GHz local hotspot. Existing fast-mode preference remains selected for future attempts. Stale createGroup success no longer removes a potentially newer group. Cancelling remains generation-guarded; late success cleanup and actual OEM lifecycle behavior require physical tests.

P2pRetryTest verifies bounded BUSY budget and immediate permanent-error termination. Full APK build, signing, upgrade identity, and 253 localized strings pass. No device or emulator was available for asynchronous Wi-Fi manager callbacks, successful local-hotspot fallback, cancellation races or QR pairing. Existing 45s outer timeout remains. Reference: https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager#BUSY


## Version 7.6: concise transfer screen and recognizable app selection

Transfer details now show only total GB, moved GB, average MB/s and estimated remaining time; retain percentage/progress, hide current filename/count status and remove transfer-screen band and remaining/category detail rows. QR band diagnostics are retained. Result page uses acknowledged prefix counts from TransferStats for completed photos/videos and a separate monotonic transferStarted timestamp set at first resetTotals per job. Automatic retries preserve that timestamp; manual restarted jobs reset it. Preparation, pairing and installation time are excluded. Stats/timer are cleared at job start to avoid old-batch result data.

SMS/calls first picker opening defaults all loaded records selected; existing saved selections are retained. The 5000-record cap remains disclosed. App picker replaces package-code strings with sorted human labels, provider icons and checkboxes. Icons load off UI thread with default-icon fallback. Only existing visible non-system launchable apps remain eligible; bundle format and install semantics are unchanged.

Build/signature and upgrade certificate pass. Existing TransferStatsTest covers acknowledged boundaries, mixed categories, empty and partial/resumed data. All 256 translations pass. Actual list rendering/taps, default check marks and final duration presentation still require device validation.


## Version 7.6.1: received file browser and direct SMS category toggle

Track successfully published receiving URIs with original display names/MIME in insertion order. Show files opens a session-local list on the receiver result screen, including committed files from interrupted transfers; new incoming batch clears it and resumed acknowledgements do not add duplicates. ACTION_VIEW grants read-only URI access via flags/ClipData; rbapp/APK entries go through the existing installer with just the selected package. Missing viewer/denied access shows an explanation. No broad storage access added. Activity state is not persisted; external Files/Gallery remain the access path after process death.

SMS category now directly exports loaded rows and selects its snapshot without a picker. Second tap clears selection. Existing Android permission gating, background export and failure preservation remain. A toast discloses the 5000-row cap when reached. Contacts/calls selection is unchanged. Build/signatures, upgrade certificate and 258 translations pass; no real-device picker/open-file/one-tap-SMS test was available.


## Version 7.7: reference-video landing and role screens

Inspected the 30.5s attached EasyShare demonstration via frames. Implemented a dark landing page with original Canvas purple ribbon, teal Mobile bo Mobile card (replaces Device clone wording) and purple File transfer card. Both lead to dark role screens with explicit old/new phone choices connected to existing permission queue and sender/receiver paths. Overflow offers actual language/help/alternate receiving functions; no dummy history or PC/iPhone migration options were added. Header retains rebaritclone. Only landing/role screens adopt the dark theme; existing functional screens retain their established layout and reset system-bar colors. Scrolling accommodates smaller displays and wrapping. No proprietary artwork or source was copied.

Full Android compile/signatures, upgrade certificate and 264 translations pass. No Android emulator or device was available for pixel comparison, navigation taps or native rendering; the reference video was visually inspected, not used as proof of generated app appearance.


## Version 7.8: dark file selection gallery

Localized phone-to-phone title. Purple file card enters sender permission setup then FileGallery; green card retains old/new device flow. Native ListView virtualizes date headers and four-column thumbnail rows, with per-item, per-day and select-visible controls, filename search, photo/video filter, duration overlays and long-press ACTION_VIEW. Audio tab loads audio rows. Files opens SAF; Apps/Contacts route to existing selectors and return to gallery. MainActivity keeps selected gallery URIs in its existing groups, deduplicating at transfer preparation. Footer reports all groups and continues to existing pairing.

MediaStore reads on a dedicated single worker; thumbnail pool has two workers and a bounded 64-task queue, 12MiB bitmap LRU and generation/closed guards. Every main page navigation shuts gallery workers down; provider results check generation before touching UI. Queries respect platform grants and show errors/empty/cap notices; max 10,000 loaded entries per current media/audio view. No extra storage grants, database mutations, or persistent transfer-history feature added. Date grouping uses date_added and local calendar date. Media query cap may omit additional records; this is disclosed.

APK build/signatures, upgrade identity and 268 strings verified. No real/emulated Android UI, thumbnails, per-day taps, partial-media grants, SAF returns, large-gallery performance or video duration overlay rendering was tested; these remain physical validation tasks. Tabs invoking existing app/contact dialogs and system document UI are functional routes rather than copies of the reference app's internal implementations.

7.9: decimal MB below 1 GB across selection, transfer and approval sizes; GB retained for larger amounts. Calls now directly export all loaded rows as SMS does, with the same 5000-row cap and permission/error handling. Second tap clears selection.

7.9.1: reverted MB display change to prior GB formatter; kept call select-all toggle. User requested APK size over 1 MB: included deterministic inert 1 MiB package-padding.bin asset, with no runtime use or performance benefit. Manual and Gradle builds include assets. Signed APK build passed; not device tested.

8.0: refreshed native home illustration and contrast, three-step active/completed indicators, category selection borders/ripples, responsive-height buttons, transfer/result information cards, gallery search field. Corrected one-tap call/SMS guidance in all locales. Signed build and translation/TransferStats tests passed. No emulator or physical-device visual verification; system pickers/install dialogs retain OS UI. Transport and media byte counts unchanged.

8.1: dedicated USB receiving entry from home menu, cable illustration and localized steps. USB wait page has retry; back returns USB setup. Android 8/9 folder success resumes USB and cancellation returns USB setup. Signed build, 278 translations and static USB-only route checks passed; physical USB transport/UI not tested.

8.2: Connect > Select > Transfer. Sender permissions route to connection page. Wi-Fi verifies encrypted peer handshake then closes preflight TCP while retaining network; receiver accepts subsequent transfer connection (10-minute accept window). USB retains authenticated channel while sender selects. Receiver selection waiting screen. Connection page clears stale sender state. Signed build, 281 translations and simulated UsbSessionTest passed. Physical-device connection-first flow not tested.

8.3: replaced incoming-transfer AlertDialog message with a custom RTL/LTR-aware confirmation card. It shows receive icon, file count, known byte total and unknown-size marker, up to three ellipsized file names, a remainder label, and two 58dp action buttons. Confirmation latch/timeout/cancel behavior is preserved. Signed build and 284 translation test passed; no device UI test.

8.4: selection page has grouped headings and visual cards for photos, videos, files, APK apps, audio, contacts, calls and SMS. Hotspot status includes an Android capability message for 6/6E, 5 GHz or fallback. Wi-Fi Direct configuration can request only 5 GHz or AUTO in Android’s public API, so the app reports/detects support but cannot force 6/6E. Signed build and 290 translation test passed; device radio behavior remains untested.
