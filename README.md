# MSP SDK — Android Integration Guide

## Overview

This guide covers how to add the MSP Android SDK to your app, including how to initialize the SDK, load ads, and display ads.

The guide also provides some best practices which help you avoid common pitfalls and achieve best performance.

**System requirements**

| Requirement | Version |
|---|---|
| Android minimum SDK | 24 (Android 7.0) |
| Android target SDK | 36 |
| Kotlin | 1.9+ |
| Gradle | 7.0+ |

---

## Installation

The MSP SDK is distributed via Maven Central. Add the core dependency and any adapter dependencies for the ad networks you want to monetize with.

**settings.gradle**

Ensure Maven Central is listed in your repository configuration:

```gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

**build.gradle (app module)**

```gradle
dependencies {
    // Core SDK (required)
    implementation 'ai.themsp:prebid-adapter:$LATEST_VERSION'
    implementation 'ai.themsp:msp-core:$LATEST_VERSION'

    // Ad network adapters (add only those you need)
    implementation 'ai.themsp:google-adapter:$LATEST_VERSION'
    implementation 'ai.themsp:facebook-adapter:$LATEST_VERSION'
    implementation 'ai.themsp:nova-adapter:$LATEST_VERSION'
    implementation 'ai.themsp:moloco-adapter:$LATEST_VERSION'
}
```

---

## AndroidManifest.xml Configuration

Add the required permissions to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Required for ad personalization -->
<uses-permission android:name="com.google.android.gms.permission.AD_ID" />
```

Network-specific manifest entries (Google App ID, etc.) are covered per-network in the [Mediation Networks Guide](#mediation-networks-guide).

---

## Initialize the SDK

Initialize the MSP SDK as early as possible — ideally in your `Application.onCreate()`. The SDK uses the initialization window to prefetch ad configurations and warm up adapter networks.

**MyApplication.kt**

```kotlin
import com.particles.msp.api.MSPInitListener
import com.particles.msp.api.MSPInitStatus
import com.particles.msp.api.MSPInitializationParameters
import com.particles.msp.util.Logger
import com.particles.prebidadapter.MSP

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeMSP()
    }

    private fun initializeMSP() {
        // Optional: enable verbose logging during development
        Logger.setLogLevel(Logger.VERBOSE)

        // Optional: pass a publisher-provided user identifier for frequency capping
        MSP.setPpid("your-user-id")

        // Build initialization parameters
        val initParams = object : MSPInitializationParameters {
            override fun getPrebidAPIKey(): String = "YOUR_PREBID_API_KEY"
            override fun getOrgId(): Int = 12345       // Your org ID from the MSP dashboard
            override fun getAppId(): Int = 67890       // Your app ID from the MSP dashboard

            // Privacy and consent flags — set values appropriate for your app
            override fun hasUserConsent(): Boolean = true
            override fun isAgeRestrictedUser(): Boolean = false
            override fun isDoNotSell(): Boolean = false
            override fun getConsentString(): String = ""
            override fun isInTestMode(): Boolean = false

            // Additional network-specific init parameters
            override fun getParameters(): Map<String, Any> = mapOf(
                 MSPConstants.INIT_PARAM_KEY_GOOGLE_APP_ID to "ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX"
                 MSPConstants.INIT_PARAM_KEY_MOLOCO_APP_KEY to "xxxxx-v9C8uNYV5riG7Vwu"
            )
        }

        // Initialize
        MSP.init(
            context = applicationContext,
            initParams = initParams,
            sdkInitListener = object : MSPInitListener {
                override fun onComplete(status: MSPInitStatus, message: String) {
                    Logger.info("[MSP] Initialization complete. Status: $status, message: $message")
                }
            }
        )
    }
}
```

Register your Application class in `AndroidManifest.xml`:

```xml
<application
    android:name=".MyApplication"
    ...>
```

**Key initialization parameters**

| Parameter | Type | Description |
|---|---|---|
| `getPrebidAPIKey()` | `String` | API key issued by Particle Inc. for your app |
| `getOrgId()` | `Int` | Organization ID provisioned from Particle Inc. |
| `getAppId()` | `Int` | App ID provisioned from Particle Inc. |
| `hasUserConsent()` | `Boolean` | Whether the user has given consent for personalized ads |
| `isDoNotSell()` | `Boolean` | Whether the user has opted out of the sale of personal data (CCPA) |
| `getConsentString()` | `String` | IAB TCF consent string |
| `getParameters()` | `Map<String, Any>` | Additional network-specific initialization parameters |

**Optional MSP properties**

| Method | Description |
|---|---|
| `MSP.setPpid(ppid)` | Publisher-provided user ID for frequency capping |

---

## Load and Display Ads

The pattern for every ad format is the same:

1. Create an `AdLoader` and an `AdRequest`.
2. Call `loader.loadAd(placementId, adListener, adRequest)`.
3. In `onAdLoaded`, call `loader.getAd(placementId)` to retrieve the ad object.
4. Present the ad (for interstitial/rewarded) or attach the ad view to your layout.

---

## Call NotifyLoss API

Call `MSP.notifyLoss` when:
1. The MSP Ad loses the auction, or
2. The MSP SDK does not fill while another bidder wins

`fun notifyLoss(winnerBidder: String, winnerPrice: Float, requestId: String, ad: MSPAd?)`

- `winnerBidder`: Name of the winning bidder other than MSP
- `winnerPrice`: Ad price of the winning bid other than MSP
- `requestId`: Provided by `onAdLoaded` and `onError` callback parameter `loadInfo[MSPConstants.LOAD_INFO_KEY_REQUEST_ID]`
- `ad`: MSP Ad that loses the auction. Pass `null` for the no-fill case.

```kotlin
class YourActivity : AppCompatActivity(), AdListener {

    // Case 1: MSP ad was loaded but lost the in-app auction to another bidder.
    // Pass the MSP ad object; requestId is not needed here.
    override fun onAdLoaded(placementId: String, loadInfo: Map<String, Any>) {
        val mspAd = loader.getAd(placementId)

        // If another bidder wins the in-app auction:
        MSP.notifyLoss(
            winnerBidder = "other_bidder_name",
            winnerPrice = 1.5f,         // winning bid price in USD
            requestId = "",
            ad = mspAd
        )
    }

    // Case 2: MSP SDK returned no fill and another bidder wins.
    // Pass null for ad and supply the requestId from loadInfo.
    override fun onError(msg: String, loadInfo: Map<String, Any>) {
        val requestId = loadInfo[MSPConstants.LOAD_INFO_KEY_REQUEST_ID] as? String ?: ""

        MSP.notifyLoss(
            winnerBidder = "other_bidder_name",
            winnerPrice = 1.5f,         // winning bid price in USD
            requestId = requestId,
            ad = null
        )
    }
}
```

---

## Ad Formats

### Banner Ads

```kotlin
import android.view.View
import com.particles.msp.api.AdFormat
import com.particles.msp.api.AdListener
import com.particles.msp.api.AdLoader
import com.particles.msp.api.AdRequest
import com.particles.msp.api.AdSize
import com.particles.msp.api.BannerAdView
import com.particles.msp.api.MSPAd

class BannerActivity : AppCompatActivity() {

    private val loader = AdLoader()

    private fun loadBanner() {
        val request = AdRequest.Builder(AdFormat.BANNER)
            .setContext(this)
            .setPlacement("YOUR_BANNER_PLACEMENT_ID")
            .setAdSize(AdSize(width = 320, height = 50, isInlineAdaptiveBanner = false, isAnchorAdaptiveBanner = false))
            .build()
        loader.loadAd("YOUR_BANNER_PLACEMENT_ID", adListener, request)
    }

    private val adListener = object : AdListener {

        override fun onAdLoaded(placementId: String, loadInfo: Map<String, Any>) {
            val bannerAd = loader.getAd(placementId) as? BannerAdView ?: return
            val adView = bannerAd.adView as View

            runOnUiThread {
                // Attach the ad view to your layout
                val container = findViewById<FrameLayout>(R.id.banner_container)
                container.removeAllViews()
                container.addView(adView)
            }
        }

        override fun onError(msg: String, loadInfo: Map<String, Any>) {
            Log.e("MSP", "Banner error: $msg")
        }

        override fun onAdImpression(ad: MSPAd) {}
        override fun onAdClicked(ad: MSPAd) {}
        override fun onAdDismissed(ad: MSPAd) {}
    }
}
```

**Standard banner sizes**

| Size | Width | Height |
|---|---|---|
| Banner | 320 | 50 |
| Medium rectangle | 300 | 250 |
| Leaderboard | 728 | 90 |

### Interstitial Ads

Load the interstitial before you need to show it. Display it at a natural transition point — level completion, article end, etc.

```kotlin
import android.app.Activity
import com.particles.msp.api.AdFormat
import com.particles.msp.api.AdListener
import com.particles.msp.api.AdLoader
import com.particles.msp.api.AdRequest
import com.particles.msp.api.InterstitialAd
import com.particles.msp.api.MSPAd

class InterstitialActivity : AppCompatActivity() {

    private val loader = AdLoader()

    private fun loadInterstitial() {
        val request = AdRequest.Builder(AdFormat.INTERSTITIAL)
            .setContext(this)
            .setPlacement("YOUR_INTERSTITIAL_PLACEMENT_ID")
            .build()
        loader.loadAd("YOUR_INTERSTITIAL_PLACEMENT_ID", adListener, request)
    }

    private fun showInterstitialIfReady() {
        val interstitialAd = loader.getAd("YOUR_INTERSTITIAL_PLACEMENT_ID") as? InterstitialAd
        if (interstitialAd == null) {
            loadInterstitial() // not ready yet — start loading
            return
        }
        interstitialAd.show(this)
    }

    private val adListener = object : AdListener {

        override fun onAdLoaded(placementId: String, loadInfo: Map<String, Any>) {
            Log.d("MSP", "Interstitial ready")
            showInterstitialIfReady()
        }

        override fun onError(msg: String, loadInfo: Map<String, Any>) {
            Log.e("MSP", "Interstitial error: $msg")
        }

        override fun onAdDismissed(ad: MSPAd) {
            Log.d("MSP", "Interstitial dismissed")
        }

        override fun onAdImpression(ad: MSPAd) {}
        override fun onAdClicked(ad: MSPAd) {}
    }
}
```

### Rewarded Ads

Rewarded ads are full-screen placements that grant a reward — coins, lives, premium content access — after the user completes the ad experience. The reward is delivered via `onAdRewardReceived`.

```kotlin
import com.particles.msp.api.AdFormat
import com.particles.msp.api.AdListener
import com.particles.msp.api.AdLoader
import com.particles.msp.api.AdRequest
import com.particles.msp.api.MSPAd
import com.particles.msp.api.RewardedAd

class RewardedActivity : AppCompatActivity() {

    private val loader = AdLoader()

    private fun loadRewardedAd() {
        val request = AdRequest.Builder(AdFormat.REWARDED)
            .setContext(this)
            .setPlacement("YOUR_REWARDED_PLACEMENT_ID")
            .build()
        loader.loadAd("YOUR_REWARDED_PLACEMENT_ID", adListener, request)
    }

    fun onUserTappedWatchAd() {
        val rewardedAd = loader.getAd("YOUR_REWARDED_PLACEMENT_ID") as? RewardedAd
        if (rewardedAd == null) {
            Log.d("MSP", "Rewarded ad not ready yet")
            return
        }
        rewardedAd.show(this)
    }

    private val adListener = object : AdListener {

        // Override this to grant the reward to the user
        override fun onAdRewardReceived(ad: MSPAd) {
            val rewardType = ad.adInfo["ad_reward_type"]
            val rewardAmount = ad.adInfo["ad_reward_amount"]
            Log.d("MSP", "Reward: $rewardAmount $rewardType")
            // Grant the reward to the user here
        }

        override fun onAdLoaded(placementId: String, loadInfo: Map<String, Any>) {}
        override fun onError(msg: String, loadInfo: Map<String, Any>) {}
        override fun onAdDismissed(ad: MSPAd) {}
        override fun onAdImpression(ad: MSPAd) {}
        override fun onAdClicked(ad: MSPAd) {}
    }
}
```

### Native Ads

Native ads return a `NativeAd` object you render with your own layout. Use `NativeAdViewBinder` to map your XML layout view IDs to the SDK, then pass it to `NativeAdView`.

**1. Define your native ad layout** (`res/layout/native_ad.xml`):

```xml
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="8dp">

        <ImageView
            android:id="@+id/icon_image_view"
            android:layout_width="40dp"
            android:layout_height="40dp" />

        <TextView
            android:id="@+id/ad_title_text_view"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/advertiser_text_view"
            android:layout_width="match_parent"
            android:layout_height="wrap_content" />

        <TextView
            android:id="@+id/ad_body_text_view"
            android:layout_width="match_parent"
            android:layout_height="wrap_content" />

        <FrameLayout
            android:id="@+id/ad_media_view_container"
            android:layout_width="match_parent"
            android:layout_height="200dp" />

        <FrameLayout
            android:id="@+id/options_view"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" />

        <Button
            android:id="@+id/cta_button"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" />

    </LinearLayout>
</FrameLayout>
```

**2. Load and render the native ad**:

```kotlin
import com.particles.msp.api.AdFormat
import com.particles.msp.api.AdListener
import com.particles.msp.api.AdLoader
import com.particles.msp.api.AdRequest
import com.particles.msp.api.MSPAd
import com.particles.msp.api.NativeAd
import com.particles.msp.api.NativeAdView
import com.particles.msp.api.NativeAdViewBinder

class NativeActivity : AppCompatActivity() {

    private val loader = AdLoader()

    private fun loadNativeAd() {
        val request = AdRequest.Builder(AdFormat.NATIVE)
            .setContext(this)
            .setPlacement("YOUR_NATIVE_PLACEMENT_ID")
            .build()
        loader.loadAd("YOUR_NATIVE_PLACEMENT_ID", adListener, request)
    }

    private val adListener = object : AdListener {

        override fun onAdLoaded(placementId: String, loadInfo: Map<String, Any>) {
            val nativeAd = loader.getAd(placementId) as? NativeAd ?: return

            val binder = NativeAdViewBinder.Builder()
                .layoutResourceId(R.layout.native_ad)
                .titleTextViewId(R.id.ad_title_text_view)
                .advertiserTextViewId(R.id.advertiser_text_view)
                .bodyTextViewId(R.id.ad_body_text_view)
                .callToActionViewId(R.id.cta_button)
                .mediaViewId(R.id.ad_media_view_container)
                .optionsViewId(R.id.options_view)
                .iconViewId(R.id.icon_image_view)
                .build()

            runOnUiThread {
                val nativeAdView = NativeAdView(nativeAd, binder, this@NativeActivity)
                val container = findViewById<FrameLayout>(R.id.ad_container)
                container.removeAllViews()
                container.addView(nativeAdView)
            }
        }

        override fun onError(msg: String, loadInfo: Map<String, Any>) {}
        override fun onAdImpression(ad: MSPAd) {}
        override fun onAdClicked(ad: MSPAd) {}
        override fun onAdDismissed(ad: MSPAd) {}
    }
}
```

### Native-Banner Multi-format

`AdFormat.MULTI_FORMAT` allows both native and banner ads to fill the same placement. In `onAdLoaded`, check which type was returned and render accordingly.

```kotlin
import android.view.View
import com.particles.msp.api.AdFormat
import com.particles.msp.api.AdListener
import com.particles.msp.api.AdLoader
import com.particles.msp.api.AdRequest
import com.particles.msp.api.AdSize
import com.particles.msp.api.BannerAdView
import com.particles.msp.api.MSPAd
import com.particles.msp.api.NativeAd
import com.particles.msp.api.NativeAdView
import com.particles.msp.api.NativeAdViewBinder

class MultiFormatActivity : AppCompatActivity() {

    private val loader = AdLoader()

    private fun loadAd() {
        val request = AdRequest.Builder(AdFormat.MULTI_FORMAT)
            .setContext(this)
            .setPlacement("YOUR_MULTI_FORMAT_PLACEMENT_ID")
            .setAdSize(AdSize(300, 250, false, false))
            .build()
        loader.loadAd("YOUR_MULTI_FORMAT_PLACEMENT_ID", adListener, request)
    }

    private val adListener = object : AdListener {

        override fun onAdLoaded(placementId: String, loadInfo: Map<String, Any>) {
            val ad = loader.getAd(placementId)

            runOnUiThread {
                val container = findViewById<FrameLayout>(R.id.ad_container)
                container.removeAllViews()

                when (ad) {
                    is NativeAd -> {
                        val binder = NativeAdViewBinder.Builder()
                            .layoutResourceId(R.layout.native_ad)
                            .titleTextViewId(R.id.ad_title_text_view)
                            .advertiserTextViewId(R.id.advertiser_text_view)
                            .bodyTextViewId(R.id.ad_body_text_view)
                            .callToActionViewId(R.id.cta_button)
                            .mediaViewId(R.id.ad_media_view_container)
                            .optionsViewId(R.id.options_view)
                            .iconViewId(R.id.icon_image_view)
                            .build()
                        container.addView(NativeAdView(ad, binder, this@MultiFormatActivity))
                    }
                    is BannerAdView -> {
                        container.addView(ad.adView as View)
                    }
                }
            }
        }

        override fun onError(msg: String, loadInfo: Map<String, Any>) {}
        override fun onAdImpression(ad: MSPAd) {}
        override fun onAdClicked(ad: MSPAd) {}
        override fun onAdDismissed(ad: MSPAd) {}
    }
}
```

---

## Mediation Networks Guide

| Dependency | Ad Network | Formats |
|---|---|---|
| `ai.themsp:google-adapter` | Google Ad Manager / AdMob | Banner, Interstitial, Rewarded, Native |
| `ai.themsp:facebook-adapter` | Meta Audience Network | Banner, Interstitial, Rewarded, Native |
| `ai.themsp:nova-adapter` | Nova | Banner, Interstitial, Native |
| `ai.themsp:moloco-adapter` | Moloco | Banner, Interstitial, Rewarded |

---

### Google

Pass your AdMob App ID in the `getParameters()` map during initialization:

```kotlin
override fun getParameters(): Map<String, Any> = mapOf(
    MSPConstants.INIT_PARAM_KEY_GOOGLE_APP_ID to "ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX"
)
```

Add the AdMob App ID to `AndroidManifest.xml` as well (required by the Google Mobile Ads SDK):

```xml
<application>
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX" />
</application>
```

### Moloco

Pass your Moloco App Key in the `getParameters()` map during initialization:

```kotlin
override fun getParameters(): Map<String, Any> = mapOf(
    MSPConstants.INIT_PARAM_KEY_MOLOCO_APP_KEY to "YOUR_MOLOCO_APP_KEY"
)
```

---

## Best Practices

### Preloading

The auction takes time. Call `loadAd` before the moment you need to show the ad:

- **Banners**: Load when the fragment or activity starts (`onCreate` / `onStart`).
- **Interstitials**: Load at app launch or immediately after the previous interstitial is dismissed.
- **Rewarded**: Load at app launch and again in `onAdDismissed`.

### Destroying Ads

When an ad is no longer needed, call `destroy()` to release adapter resources and prevent memory leaks:

```kotlin
override fun onDestroy() {
    super.onDestroy()
    currentAd?.destroy()
    currentAd = null
}
```

---

## Troubleshooting

**No ads filling**

- Verify the placement IDs match what is provisioned by Particle Inc.
- Confirm `MSP.init()` completes before `loadAd` is called.
- Enable verbose logging during development: `Logger.setLogLevel(Logger.VERBOSE)`.

**`onAdLoaded` fires but `getAd` returns null**

`getAd` removes the ad from cache. Call it exactly once per load cycle and hold the returned ad strongly until you are done with it.

**Interstitial or rewarded ad not showing**

`show(activity)` must be called with a non-null, non-finishing `Activity`. Ensure the activity is in the foreground when you call `show`.

**Rewarded ad shows but reward is never granted**

`onAdRewardReceived` fires only if the user watches the ad to completion. Do not grant rewards based on `onAdDismissed` alone.

---

## Privacy & CCPA

Pass user consent and privacy signals through the `MSPInitializationParameters` implementation:

- `hasUserConsent()` — set to `true` if the user has consented to personalized advertising
- `isDoNotSell()` — set to `true` if the user has opted out under CCPA
- `getConsentString()` — provide the IAB TCF consent string if applicable

Please follow Prebid's documentation to set the user's IAB US Privacy signal: https://docs.prebid.org/prebid-mobile/prebid-mobile-privacy-regulation.html#notice-and-opt-out-signal
