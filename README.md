# MSP SDK (Android) integration guide
## Privisioning
A *Prebid API Key* needs to be provided offline by Particles. Please search `"af7ce3f9-462d-4df1-815f-09314bb87ca3"` in the demo app and replace it with your own. 

Publisher App developers need to pass an *placement id* provisioned by Particles to `LoadAd` API to load an Ad. Please search `"demo-android-native-banner-multiformat"` in the demo app and replace it with your own.

## Dependencies
```
    implementation ("ai.themsp:facebook-adapter:{msp_sdk_version}")
    implementation ("ai.themsp:nova-adapter:{msp_sdk_version}")
    implementation ("ai.themsp:google-adapter:{msp_sdk_version}")
    implementation ("ai.themsp:prebid-adapter:{msp_sdk_version}")
    implementation ("ai.themsp:msp-core:{msp_sdk_version}")
```
Please refer to demo app gradle file for the latest msp sdk version: [build.gradle.kts](https://github.com/ParticleMedia/msp-sdk-demo-android/blob/main/app/build.gradle.kts#L57)

## API usage 
1. Init SDK using `MSP.init`
2. Load an Ad using `AdLoader`
3. Got notified via `AdListener.onAdLoaded(placementId: String)` when Ad finished loading.
4. Fetch the loaded Ad from cache using `AdLoader().getAd` API   
Please checkout the demo app for [sample code](https://github.com/ParticleMedia/msp-sdk-demo/blob/main/app/src/main/java/com/particlemedia/ad/MainActivity.kt)

### notifyLoss API
Call `MSP.notifyLoss` API when: 
1. MSP Ad loses the auction, or
2. MSP SDK does not fill while other bidder wins

`fun notifyLoss(winnerBidder: String, winnerPrice: Float, requestId: String, ad: MSPAd?)`

- `winnerBidder`: name of the winning bidder other than MSP
- `winnderPrice`: Ad price of the winning bid other than MSP
- `requestId`: Provided by `onAdLoaded` and `onError` callback parameter `loadInfo[MSPConstants.LOAD_INFO_KEY_REQUEST_ID]`
- `ad`: MSP Ad that loses the auction.(pass `null` for the No fill case)

### About AdRequest.Builder.setAdaptiveBannerSize
This is to support Google Adaptive [Banner Ads](https://developers.google.com/ad-manager/mobile-ads-sdk/android/banner)

To support **inline** adaptive banner Ad: 
```
     setAdaptiveBannerSize(AdSize(DisplayUtils.getScreenWidth(), 0, true, false))
```

To support **anchored** adaptive banner Ad:
```
    com.google.android.gms.ads.AdSize size = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, DisplayUtils.getScreenWidth());
    ......
    setAdaptiveBannerSize(new com.particles.msp.api.AdSize(size.getWidth(), size.getHeight(), false, true))
    
```

Please checkout the demo app for [sample code](https://github.com/ParticleMedia/msp-sdk-demo/blob/main/app/src/main/java/com/particlemedia/ad/MainActivity.kt)
## Proguard rules
Add below rules to your app's proguard-rules.pro file: 
```
## Google Ads
-keep class com.google.android.gms.ads.** { *; }

## MSP SDK
-keep class com.particles.** { *; }
```
## Privacy & CCPA
Please follow Prebid's documentation to set user's IAB US Privacy signal: https://docs.prebid.org/prebid-mobile/prebid-mobile-privacy-regulation.html#notice-and-opt-out-signal 

## Verify your integration
If everything goes well, you should be able to get below sample Ad from Prebid.

<img width="300" src="https://github.com/user-attachments/assets/9135888c-f4d9-468c-b026-e989d1acbbad">
