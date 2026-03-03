package com.particlemedia.ad

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.particles.android.ads.internal.data.cache.AdCache
import com.particles.msp.api.AdFormat
import com.particles.msp.api.AdListener
import com.particles.msp.api.AdLoader
import com.particles.msp.api.AdRequest
import com.particles.msp.api.AdSize
import com.particles.msp.api.BannerAdView
import com.particles.msp.api.MSPAd
import com.particles.msp.api.MSPConstants
import com.particles.msp.api.MSPInitListener
import com.particles.msp.api.MSPInitStatus
import com.particles.msp.api.MSPInitializationParameters
import com.particles.msp.api.NativeAd
import com.particles.msp.api.NativeAdView
import com.particles.msp.api.NativeAdViewBinder
import com.particles.msp.util.Logger
import com.particles.prebidadapter.MSP
import kotlin.system.measureTimeMillis

class BannerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ad)



        // 2. listen and handle loaded Ad
        val adLoadListener = object : AdListener {
            override fun onAdClicked(ad: MSPAd) {
                Logger.info("Ad clicked. info: ${ad.adInfo}")
            }

            override fun onAdDismissed(ad: MSPAd) {
               Logger.info("Ad dismissed. info: ${ad.adInfo}")
            }

            override fun onAdImpression(ad: MSPAd) {
                Logger.info("Ad is displayed. info: ${ad.adInfo}")
            }

            override fun onAdLoaded(placementId: String, loadInfo: Map<String, Any>) {
                Logger.info("Ad load event received. placementId: $placementId. Fetching ads from cache...")
                val ad:MSPAd? = AdLoader().getAd(placementId)
                if (ad == null) {
                    Logger.info("Got null ad from cache. placementId: $placementId")
                    return
                }

                Logger.info("Got ad from cache. placementId: $placementId. adInfo: ${ad.adInfo}")

                val adViewContainer = findViewById<FrameLayout>(R.id.ad_container)
                val destroyButton = findViewById<Button>(R.id.destroy_button)
                var adView: View? = null
                if (ad is BannerAdView) {
                    adView = (ad.adView) as View
                    Logger.info("Banner Ad loaded.")
                } else if (ad is NativeAd) {
                    Logger.info("Native Ad loaded. Title: ${ad.title}")
                    val nativeAdViewBinder: NativeAdViewBinder = NativeAdViewBinder.Builder()
                        .layoutResourceId(R.layout.native_ad)
                        .titleTextViewId(R.id.ad_title_text_view)
                        .advertiserTextViewId(R.id.advertiser_text_view)
                        .bodyTextViewId(R.id.ad_body_text_view)
                        .callToActionViewId(R.id.cta_button)
                        .mediaViewId(R.id.ad_media_view_container)
                        .optionsViewId(R.id.options_view)
                        .build()
                    adView = NativeAdView(ad, nativeAdViewBinder, applicationContext)
                }
                adView?.let {
                    adViewContainer.addView(it)
                    destroyButton.setOnClickListener { ad.destroy() }
                }
            }

            override fun onError(msg: String, loadInfo: Map<String, Any>) {
                Logger.info("MainActivity. Ads loading error: $msg")
            }
        }

        // 3. Load an Banner Ad
        val placementId = "demo-android-article-top"  // Please replace with your own placement ID
        val adRequest = AdRequest.Builder(AdFormat.BANNER)
            .setContext(applicationContext)
            .setPlacement(placementId)
            .setAdSize(AdSize(320, 50, false, false))
            .setCustomParams(mapOf("user_id" to "177905312")) // Please replace with current app user id (ppid) (optional)
            .setAdaptiveBannerSize(AdSize(384, 0, false, true))
            .setTestParams(getTestParams()) // for testing ONLY. Please do NOT set for production builds. Otherwise no impression will be counted.
            .build()

        val start = System.currentTimeMillis()
        Logger.info("AdLoader.loadAd() start")
        AdLoader().loadAd(placementId, adLoadListener, adRequest)
        Logger.info("AdLoader.loadAd() end. DURATION: ${System.currentTimeMillis() - start} ms")
    }


    fun getTestParams(): Map<String, Any> {
        val testParams: MutableMap<String, Any> = HashMap()
        testParams["test_ad"] = true
        testParams["ad_network"] = "Prebid"
        return testParams
    }
}