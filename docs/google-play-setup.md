# Google Play Store Setup Guide

This guide covers the manual steps required to set up your Google Play Developer account and prepare the Play Console for qMetronome's first release.

## 1. Create a Google Play Developer Account

1.  **Sign Up**: Go to the [Google Play Console](https://play.google.com/console/signup).
2.  **Select Account Type**: Choose "Organization" (recommended for Quaternion Media) or "Personal".
3.  **Identity Verification**: You will need to provide:
    *   A valid ID (Passport/Driver's License).
    *   Organization details (D-U-N-S number, address, phone).
    *   A one-time registration fee ($25 USD).
4.  **Wait for Approval**: Verification can take a few days.

## 2. Create the App in Play Console

Once your account is ready:
1.  Click **Create app**.
2.  **App Name**: `qMetronome`
3.  **Default Language**: `English (United States)`
4.  **App or Game**: `App`
5.  **Free or Paid**: `Free`
6.  Accept the **Declarations** and click **Create app**.

## 3. Set Up Play App Signing

Google Play manages your app's signing key by default, which is highly recommended.
1.  In the app dashboard, go to **Setup > App integrity**.
2.  Ensure **Play App Signing** is enabled.
3.  You will use your **Upload Key** (generated in the "Technical Release Prep" step) to sign the `.aab` (Android App Bundle) before uploading it. Google will replace it with the production key.

## 4. Privacy Policy

Google Play requires a URL for your privacy policy.
1.  Navigate to **Policy and programs > App content**.
2.  Under **Privacy policy**, provide the link to the raw `PRIVACY.md` from the GitHub repository:
    `https://raw.githubusercontent.com/quaternionmedia/qmetronome/main/PRIVACY.md`

## 5. Store Listing Assets

Follow the guidelines in [`app-store-checklist.md`](app-store-checklist.md) to upload:
*   **App Icon**: 512x512 PNG (32-bit with alpha).
*   **Feature Graphic**: 1024x500 PNG.
*   **Screenshots**: At least two (Phone, 7-inch tablet, 10-inch tablet).

## 6. Testing Tracks

Before going to Production, use an **Internal testing** or **Closed testing** track.
1.  Go to **Testing > Internal testing**.
2.  Create a new release and upload the signed `.aab` file.
3.  Add testers (yourself and the Quaternion Media team).
4.  This allows you to verify the app on real devices via the Play Store before it's public.
