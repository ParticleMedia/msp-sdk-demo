package com.particlemedia.ad

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.particles.msp.api.AdFormat
import com.particles.msp.api.AdListener
import com.particles.msp.api.AdLoader
import com.particles.msp.api.AdRequest
import com.particles.msp.api.AdSize
import com.particles.msp.api.BannerAdView
import com.particles.msp.api.InterstitialAd
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

class InterstitialFormatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ad)

        // 2. listen and handle loaded Ad
        val adLoadListener = object : AdListener {
            override fun onAdClicked(ad: MSPAd) {
                Logger.info("Ad clicked. info: ${ad.adInfo}")
                ad.sendHideAdEvent("test ad hide reason")
                ad.sendReportAdEvent("test ad report reason", "test ad report description")
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
                } else if (ad is InterstitialAd) {
                    Logger.info("Interstitial Ad loaded.")
                    ad.show(this@InterstitialFormatActivity)
                }
                adView?.let {
                    adViewContainer.addView(it)
                }
                destroyButton.setOnClickListener { ad.destroy() }
            }

            override fun onError(msg: String, loadInfo: Map<String, Any>) {
                Logger.info("MainActivity. Ads loading error: $msg")
            }
        }

        // 3. Load an Interstitial Ad
        val placementId = "demo-android-interstitial" // Please replace with your own placement ID
        val adRequest = AdRequest.Builder(AdFormat.INTERSTITIAL)
            .setContext(this)
            .setPlacement(placementId)
            .setCustomParams(mapOf("user_id" to "177905312")) // Please replace with current app user id (ppid)
            .setTestParams(getTestParams())
            .build()
        val start = System.currentTimeMillis()
        Logger.info("AdLoader.loadAd() start")
        AdLoader().loadAd(placementId, adLoadListener, adRequest)
        Logger.info("AdLoader.loadAd() end. DURATION: ${System.currentTimeMillis() - start} ms")
    }

    fun getTestParams(): Map<String, Any> {
        val creativeType = "video"
        val creativeLayout = "vertical"
        return buildMap {
            put("test_ad", true)
            //put("ad_network", "msp_google")
            //put("ad_network", "msp_fb")
            put("ad_network", "msp_nova")
            // debug_item is what the Nova debug/test ad-set filter actually reads server-side;
            // top-level fields here only land in ext.data.test, which the server ignores.
            put(MSPConstants.CUSTOM_PARAM_KEY_DEBUG_ITEMS, buildMap {
                put("debug", true)
                put("creative_type", creativeType.uppercase())
                put("layout", creativeLayout)
                put("exp_parameter", buildMap {
                    put("h5_template_group", "t1")
                    put("enable_h5_format", "true")
                    put("enable_h5_for_playable", "true")
                    put("preload", "true")
                })
            })
        }
    }
}