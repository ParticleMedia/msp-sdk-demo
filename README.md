# MSP SDK — iOS Integration Guide

## Overview

This guide covers how to add the MSP iOS SDK to your app, including how to initialize SDK, load Ad and display ads. 

The guide also provides some best pratices which helps you avoid common pitfalls and achieve best performance.

**System requirements**

| Requirement | Version |
|---|---|
| iOS deployment target | 15.0+ |
| Xcode | 15.0+ |
| Swift | 5.0+ |
| CocoaPods | 1.12+ |

---

## Installation

The MSP SDK is distributed via CocoaPods. Add the core pod and any adapter pods for the ad networks you want to monetize with.

**Podfile**

```ruby
platform :ios, '15.0'

target 'YourApp' do
  use_frameworks!

  # Core SDK (required)
  pod 'MSPiOSCore'

  # Ad network adapters (add only those you need)
  pod 'MSPGoogleAdapter'
  pod 'MSPFacebookAdapter'
  pod 'MSPNovaAdapter'
  pod 'MSPMolocoAdapter'
end
```

Run the install command:

```bash
pod install
```

Open the generated `.xcworkspace` file — do not use the `.xcodeproj` directly.

---

## Info.plist Configuration

### App Tracking Transparency (iOS 14+)

Add the usage description key. The system displays this string in the ATT permission prompt:

```xml
<key>NSUserTrackingUsageDescription</key>
<string>This identifier will be used to deliver personalized ads to you.</string>
```

Network-specific `Info.plist` entries (GAD identifier, SKAdNetwork IDs) are covered per-network in the [Mediation Networks Guide](#mediation-networks-guide).

---

## Initialize the SDK

Initialize the MSP SDK as early as possible — ideally in `application(_:didFinishLaunchingWithOptions:)`. The SDK uses the initialization window to prefetch ad configurations and warm up adapter networks.

**AppDelegate.swift**

```swift
import AppTrackingTransparency
import MSPCore
import MSPiOSCore

// Import adapter modules — see Mediation Networks Guide for each network's import name

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    // Declare adapter managers for the networks you want to activate
    private let adNetworkManagers: [AdNetworkManager] = [
        GoogleManager(),
        FacebookManager(),
        NovaManager(),
        MolocoManager(),
    ]

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        initializeMSP()

        // Request ATT permission after a brief delay so your UI is visible
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            if #available(iOS 14, *) {
                ATTrackingManager.requestTrackingAuthorization { _ in }
            }
        }

        return true
    }

    private func initializeMSP() {
        // Optional: pass a publisher-provided user identifier for frequency capping
        MSP.shared.ppid = "your-user-id"

        // Build initialization parameters
        let initParams = InitializationParametersImp(
            prebidAPIKey: "YOUR_PREBID_API_KEY",
            sourceApp: "YOUR_SOURCE_APP",
            orgId: 12345,   // Your org ID from the MSP dashboard
            appId: 67890    // Your app ID from the MSP dashboard
        )

        // Initialize
        MSP.shared.initMSP(
            initParams: initParams,
            sdkInitListener: self,          // optional — set to nil if not needed
            adNetworkManagers: adNetworkManagers
        )
    }
}

// MARK: - MSPInitListener (optional)
extension AppDelegate: MSPInitListener {
    func onComplete(status: MSPInitStatus, message: String) {
        print("[MSP] Initialization complete. Status: \(status), message: \(message)")
    }
}
```

**Key initialization parameters**

| Parameter | Type | Description |
|---|---|---|
| `prebidAPIKey` | `String` | API key issued by Particle inc. for your app |
| `sourceApp` | `String` | App bundle identifier or source tag |
| `orgId` | `Int64` | Organization ID provisioned from Particle inc. |
| `appId` | `Int64` | App ID provisioned from Particle inc. |

**Optional MSP.shared properties**

| Property | Default | Description |
|---|---|---|
| `ppid` | `nil` | Publisher-provided user ID |

---

## Load and Display Ads

The pattern for every ad format is the same:

1. Create an `MSPAdLoader` and an `AdRequest`.
2. Call `loadAd(placementId:adListener:adRequest:)`.
3. In `onAdLoaded`, call `loader.getAd(placementId:)` to retrieve the ad object.
4. Present the Ad(for interstitial Ad) or attach the ad to the App view hierarchy.

---

## Call NotifyLoss API
Call `MSP.shared.notifyLoss` API when: 
1. MSP Ad loses the auction, or
2. MSP SDK does not fill while other bidder wins

`public func notifyLoss(winnerBidderName: String, winnerPrice: Float, ad: MSPAd?, requestId: String?)`

- `winnerBidderName`: name of the winning bidder other than MSP
- `winnderPrice`: Ad price of the winning bid other than MSP
- `ad`: MSP Ad that loses the auction.(pass `nil` for the No fill case)
- `requestId`: Provided by `onSuccess` and `onError` callback parameter `loadInfo["request_id"]`

```swift
extension YourViewController: AdListener {

    // Case 1: MSP ad was loaded but lost the in-app auction to another bidder.
    // Pass the MSP ad object; requestId is not needed here.
    func onAdLoaded(placementId: String, loadInfo: [String: Any]) {
        let mspAd = loader.getAd(placementId: placementId)

        // If another bidder wins the in-app auction:
        MSP.shared.notifyLoss(
            winnerBidderName: "other_bidder_name",
            winnerPrice: 1.5,       // winning bid price in USD
            ad: mspAd,
            requestId: nil
        )
    }

    // Case 2: MSP SDK returned no fill and another bidder wins.
    // Pass nil for ad and supply the requestId from loadInfo.
    func onError(msg: String, loadInfo: [String: Any]) {
        let requestId = loadInfo["request_id"] as? String

        MSP.shared.notifyLoss(
            winnerBidderName: "other_bidder_name",
            winnerPrice: 1.5,       // winning bid price in USD
            ad: nil,
            requestId: requestId
        )
    }
}
```
---

## Ad Formats

### Banner Ads

```swift
import MSPCore
import MSPiOSCore

class BannerViewController: UIViewController {

    private let loader = MSPAdLoader()
    private var bannerContainerView: UIView?

    private func loadBanner() {
        let size = AdSize(width: 320, height: 50)
        let request = AdRequest(
            customParams: [:],
            geo: nil,
            context: nil,
            adaptiveBannerSize: nil,
            adSize: size,
            placementId: "YOUR_BANNER_PLACEMENT_ID",
            adFormat: .banner
        )
        loader.loadAd(placementId: "YOUR_BANNER_PLACEMENT_ID", adListener: self, adRequest: request)
    }
}

extension BannerViewController: AdListener {

    func onAdLoaded(placementId: String, loadInfo: [String: Any]) {
        guard let bannerAd = loader.getAd(placementId: placementId) as? BannerAd else { return }

        let adView = bannerAd.adView
        adView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(adView)

        NSLayoutConstraint.activate([
            adView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            adView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),
            adView.widthAnchor.constraint(equalToConstant: 320),
            adView.heightAnchor.constraint(equalToConstant: 50),
        ])
    }

    func onError(msg: String, loadInfo: [String: Any]) {
        print("[MSP] Banner error: \(msg)")
    }

    func onAdImpression(ad: MSPAd) {}
    func onAdClick(ad: MSPAd) {}
    func onAdDismissed(ad: MSPAd) {}

    func getRootViewController() -> UIViewController? { self }
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

```swift
import MSPCore
import MSPiOSCore

class InterstitialViewController: UIViewController {

    private let loader = MSPAdLoader()

    private func loadInterstitial() {
        let request = AdRequest(
            customParams: [:],
            geo: nil,
            context: nil,
            adaptiveBannerSize: nil,
            adSize: nil,
            placementId: "YOUR_INTERSTITIAL_PLACEMENT_ID",
            adFormat: .interstitial
        )
        loader.loadAd(placementId: "YOUR_INTERSTITIAL_PLACEMENT_ID", adListener: self, adRequest: request)
    }

    private func showInterstitialIfReady() {
        guard let interstitialAd = loader.getAd(placementId: "YOUR_INTERSTITIAL_PLACEMENT_ID") as? InterstitialAd else {
            loadInterstitial() // not ready yet — start loading
            return
        }
        interstitialAd.show(rootViewController: self)
    }
}

extension InterstitialViewController: AdListener {

    func onAdLoaded(placementId: String, loadInfo: [String: Any]) {
        print("[MSP] Interstitial ready")
        showInterstitialIfReady()
    }

    func onError(msg: String, loadInfo: [String: Any]) {
        print("[MSP] Interstitial error: \(msg)")
    }

    func onAdDismissed(ad: MSPAd) {
        print("[MSP] Interstitial Ads is dismissed: \(msg)")
    }

    func onAdImpression(ad: MSPAd) {}
    func onAdClick(ad: MSPAd) {}

    func getRootViewController() -> UIViewController? { self }
}
```

### Rewarded Ads

Rewarded ads are full-screen placements that grant a reward — coins, lives, premium content access — after the user completes the ad experience. The reward is delivered via `onAdRewardReceived`.

```swift
import MSPCore
import MSPiOSCore

class RewardedViewController: UIViewController {

    private let loader = MSPAdLoader()

    private func loadRewardedAd() {
        let request = AdRequest(
            customParams: [:],
            geo: nil,
            context: nil,
            adaptiveBannerSize: nil,
            adSize: nil,
            placementId: "YOUR_REWARDED_PLACEMENT_ID",
            adFormat: .rewarded
        )
        loader.loadAd(placementId: "YOUR_REWARDED_PLACEMENT_ID", adListener: self, adRequest: request)
    }

    @IBAction func userTappedWatchAd(_ sender: UIButton) {
        guard let rewardedAd = loader.getAd(placementId: "YOUR_REWARDED_PLACEMENT_ID") as? RewardedAd else {
            print("[MSP] Rewarded ad not ready yet")
            return
        }
        rewardedAd.show(rootViewController: self)
    }
}

extension RewardedViewController: AdListener {

    // Override this to grant the reward to the user
    func onAdRewardReceived(ad: MSPAd) {
        guard let rewardedAd = ad as? RewardedAd else { return }
        let reward = rewardedAd.reward
        print("[MSP] Reward: \(reward.amount) \(reward.type)")
        // Grant the reward to the user here
    }

    func onAdLoaded(placementId: String, loadInfo: [String: Any]) {}
    func onError(msg: String, loadInfo: [String: Any]) {}
    func onAdDismissed(ad: MSPAd) {}
    func onAdImpression(ad: MSPAd) {}
    func onAdClick(ad: MSPAd) {}

    func getRootViewController() -> UIViewController? { self }
}
```

### Native Ads

Native ads return a `NativeAd` object you render with your own layout. Implement `MSPNativeAdContainer` to wire up your UI elements, then pass it to `NativeAdView`.

```swift
import MSPCore
import MSPiOSCore

class NativeViewController: UIViewController {

    private let loader = MSPAdLoader()

    private func loadNativeAd() {
        let request = AdRequest(
            customParams: [:],
            geo: nil,
            context: nil,
            adaptiveBannerSize: nil,
            adSize: nil,
            placementId: "YOUR_NATIVE_PLACEMENT_ID",
            adFormat: .native
        )
        loader.loadAd(placementId: "YOUR_NATIVE_PLACEMENT_ID", adListener: self, adRequest: request)
    }
}

extension NativeViewController: AdListener {

    func onAdLoaded(placementId: String, loadInfo: [String: Any]) {
        guard let nativeAd = loader.getAd(placementId: placementId) as? NativeAd else { return }
        DispatchQueue.main.async {
            let container = MyNativeAdContainer(frame: CGRect(x: 0, y: 0, width: 300, height: 250))
            let nativeAdView = NativeAdView(nativeAd: nativeAd, nativeAdContainer: container)
            nativeAdView.translatesAutoresizingMaskIntoConstraints = false
            self.view.addSubview(nativeAdView)
            NSLayoutConstraint.activate([
                nativeAdView.centerXAnchor.constraint(equalTo: self.view.centerXAnchor),
                nativeAdView.centerYAnchor.constraint(equalTo: self.view.centerYAnchor),
                nativeAdView.widthAnchor.constraint(equalToConstant: 300),
            ])
        }
    }

    func onError(msg: String, loadInfo: [String: Any]) {}
    func onAdImpression(ad: MSPAd) {}
    func onAdClick(ad: MSPAd) {}
    func onAdDismissed(ad: MSPAd) {}

    func getRootViewController() -> UIViewController? { self }
}
```

Implement `MSPNativeAdContainer` to provide the UI elements the SDK will populate:

```swift
class MyNativeAdContainer: UIView, MSPNativeAdContainer {
    private let titleLabel = UILabel()
    private let bodyLabel = UILabel()
    private let advertiserLabel = UILabel()
    private let ctaButton = UIButton()
    private let mediaView = UIView()
    private let iconView = UIImageView()

    func getTitle() -> UILabel? { titleLabel }
    func getbody() -> UILabel? { bodyLabel }
    func getAdvertiser() -> UILabel? { advertiserLabel }
    func getCallToAction() -> UIButton? { ctaButton }
    func getMedia() -> UIView? { mediaView }
    func getIcon() -> UIImageView? { iconView }
    func getCustomClickableViews() -> [UIView]? { nil }
}
```

### Native-Banner Multi-format

`.multi_format` allows both native and banner ads to fill the same placement. In `onAdLoaded`, check which type was returned and render accordingly.

```swift
import MSPCore
import MSPiOSCore

class MultiFormatViewController: UIViewController {

    private let loader = MSPAdLoader()

    private func loadAd() {
        let request = AdRequest(
            customParams: [:],
            geo: nil,
            context: nil,
            adaptiveBannerSize: nil,
            adSize: nil,
            placementId: "YOUR_MULTI_FORMAT_PLACEMENT_ID",
            adFormat: .multi_format
        )
        loader.loadAd(placementId: "YOUR_MULTI_FORMAT_PLACEMENT_ID", adListener: self, adRequest: request)
    }
}

extension MultiFormatViewController: AdListener {

    func onAdLoaded(placementId: String, loadInfo: [String: Any]) {
        let ad = loader.getAd(placementId: placementId)
        DispatchQueue.main.async {
            if let nativeAd = ad as? NativeAd {
                let container = MyNativeAdContainer(frame: CGRect(x: 0, y: 0, width: 300, height: 250))
                let nativeAdView = NativeAdView(nativeAd: nativeAd, nativeAdContainer: container)
                nativeAdView.translatesAutoresizingMaskIntoConstraints = false
                self.view.addSubview(nativeAdView)
                NSLayoutConstraint.activate([
                    nativeAdView.centerXAnchor.constraint(equalTo: self.view.centerXAnchor),
                    nativeAdView.centerYAnchor.constraint(equalTo: self.view.centerYAnchor),
                    nativeAdView.widthAnchor.constraint(equalToConstant: 300),
                ])
            } else if let bannerAd = ad as? BannerAd {
                let adView = bannerAd.adView
                adView.translatesAutoresizingMaskIntoConstraints = false
                self.view.addSubview(adView)
                NSLayoutConstraint.activate([
                    adView.centerXAnchor.constraint(equalTo: self.view.centerXAnchor),
                    adView.centerYAnchor.constraint(equalTo: self.view.centerYAnchor),
                    adView.widthAnchor.constraint(equalToConstant: 320),
                    adView.heightAnchor.constraint(equalToConstant: 50),
                ])
            }
        }
    }

    func onError(msg: String, loadInfo: [String: Any]) {}
    func onAdImpression(ad: MSPAd) {}
    func onAdClick(ad: MSPAd) {}
    func onAdDismissed(ad: MSPAd) {}

    func getRootViewController() -> UIViewController? { self }
}
```

## Mediation Networks Guide

Network-specific `Info.plist` entries required for each adapter are listed below.

| Pod | Ad Network | Formats |
|---|---|---|
| `MSPGoogleAdapter` | Google Ad Manager / AdMob | Banner, Interstitial, Rewarded, Native |
| `MSPFacebookAdapter` | Meta Audience Network | Banner, Interstitial, Rewarded, Native |
| `MSPNovaAdapter` | Nova | Banner, Interstitial, Native |
| `MSPMolocoAdapter` | Moloco | Banner, Interstitial, Rewarded |

---

### Google

**Info.plist — GAD identifier**

Add your app's GAD identifier from the Google AdMob dashboard:

```xml
<key>GADApplicationIdentifier</key>
<string>ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX</string>
```

**Info.plist — SKAdNetwork**

```xml
<dict>
  <key>SKAdNetworkIdentifier</key>
  <string>cstr6suwn9.skadnetwork</string>
</dict>
```

---

### Meta (Facebook)

**Info.plist — SKAdNetwork**

```xml
<dict>
  <key>SKAdNetworkIdentifier</key>
  <string>v9wttpbfk9.skadnetwork</string>
</dict>
<dict>
  <key>SKAdNetworkIdentifier</key>
  <string>n38lu8286q.skadnetwork</string>
</dict>
```

---

## Best Practices

### Preloading

The auction takes time. Suggest Call `loadAd` before the moment you need to show the ad:

- **Banners**: Load when the view controller loads (`viewDidLoad`).
- **Interstitials**: Load at app launch or immediately after the previous interstitial is dismissed.
- **Rewarded**: Load at app launch and again in `onAdDismissed`.

### Destroying Ads

When an ad is no longer needed, call `destroy()` to release adapter resources and prevent memory leaks:

```swift
override func viewDidDisappear(_ animated: Bool) {
    super.viewDidDisappear(animated)
    currentAd?.destroy()
    currentAd = nil
}
```

### Memory Management

Keep a strong reference to your `MSPAdLoader` instance for the lifetime of the placement. The `AdListener` is held weakly by the loader — your view controller or delegate object must remain alive until `onAdLoaded` or `onError` fires.

---

## Troubleshooting

**No ads filling**

- Verify the placement IDs match what is Provisioned by Particles inc.
- Confirm `initMSP` completes before `loadAd` is called.
- Enable verbose logging during development: `MSPLogger.shared.setLogLevel(level: MSPLogger.DEBUG)`.

**`onAdLoaded` fires but `getAd` returns nil**

`getAd` removes the ad from cache. Call it exactly once per load cycle and hold the returned ad strongly until you are done with it.

**ATT prompt not appearing**

`ATTrackingManager.requestTrackingAuthorization` must be called from the main thread after your root view controller is visible. The 1-second delay in the sample above handles the most common case; adjust as needed for your presentation flow.

**Rewarded ad shows but reward is never granted**

`onAdRewardReceived` fires only if the user watches the ad to completion. Do not grant rewards based on `onAdDismissed` alone.

## Privacy & CCPA
Please follow Prebid's documentation to set user's IAB US Privacy signal: https://docs.prebid.org/prebid-mobile/prebid-mobile-privacy-regulation.html#notice-and-opt-out-signal 
