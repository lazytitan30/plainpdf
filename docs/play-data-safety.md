# Google Play data safety form: answers for Plain PDF

Filled in from the privacy audit of 4 September 2026. Re-check after any dependency change.

## Section: Data collection and security

**Does your app collect or share any of the required user data types?** No.

Rationale: collection in Play's definition means data transmitted off the device. Plain PDF holds no INTERNET permission in either flavour (verified in the merged release manifests), so nothing is transmitted. Data that stays on the device and is not sent off it does not count as collected.

**Is all of the user data collected by your app encrypted in transit?** Not applicable, nothing is transmitted. If the form insists on an answer, choose "No data collected".

**Do you provide a way for users to request that their data is deleted?** Yes: removing a document or folder from the library deletes what the app stores about it; clearing app storage or uninstalling removes everything. Nothing exists on any server to delete.

## Section: Data types

Mark every category as not collected and not shared. For your own records, the on-device data is:

- App activity: none leaves the device.
- Files and docs: opened only through the system file picker, stored as links plus display names locally, never transmitted.
- Photos: Scan receives a photo from the camera app into private cache, deleted when the scan ends or within a day, never transmitted.
- Personal info, financial info, location, contacts, calendar, health, messages, audio, device IDs: not accessed at all.

## Section: Security practices

- Data is encrypted in transit: not applicable (no transit).
- Users can request data deletion: yes, see above.
- Independent security review: no.

## Third-party SDKs (Play flavour only)

The Play flavour includes Google Play Billing 9.1.0 for the optional Supporter purchase. The billing library bundles a diagnostics transport component. Because the app holds no INTERNET permission, that component cannot send anything. If Google's SDK index ever flags it, the honest statement is: "The billing SDK's diagnostics cannot transmit because the app does not have network access."

The FOSS flavour has no third-party SDK with any network capability.

## Permissions declared

| Permission | Flavour | Reason |
|---|---|---|
| POST_NOTIFICATIONS | both | Progress notification for long tool runs |
| FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC | both | Keeps long tool runs alive under a notification |
| WAKE_LOCK, RECEIVE_BOOT_COMPLETED | both (merged from WorkManager) | Standard WorkManager requirements; the boot receiver is disabled unless work is pending |
| com.android.vending.BILLING | play | Supporter purchase |

No storage, camera, location or network permission.

## Privacy policy URL

Host PRIVACY.md from the repository root and enter its public URL in the store listing. The policy text matches the behaviour verified in the audit.
