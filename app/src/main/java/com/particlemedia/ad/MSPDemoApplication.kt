package com.particlemedia.ad

import android.app.Application
import com.particles.msp.api.MSPConstants
import com.particles.msp.api.MSPInitListener
import com.particles.msp.api.MSPInitStatus
import com.particles.msp.api.MSPInitializationParameters
import com.particles.msp.util.Logger
import com.particles.prebidadapter.MSP
import kotlin.system.measureTimeMillis

class MSPDemoApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        Logger.setLogLevel(Logger.DEBUG) // for debugging only. Please do NOT set for production builds.
        // 1. init MSP SDK
        val initParams = object : MSPInitializationParameters  {
            override fun getAppId(): Int {
                // Please replace with your own provisioned app id
                return 1
            }

            override fun getOrgId(): Int {
                // Please replace with your own provisioned org id
                return 1061
            }

            override fun getParameters(): Map<String, Any> {
                // Please replace with current app user id (ppid)
                return mapOf(MSPConstants.INIT_PARAM_KEY_PPID to "shun-test-ppid", MSPConstants.INIT_PARAM_KEY_EMAIL to "shun.j@shun.com")
            }

            override fun isAgeRestrictedUser(): Boolean {
                // currently returned value is not used
                return false
            }

            override fun isInTestMode(): Boolean {
                // currently returned value is not used
                return true
            }

            override fun getPrebidAPIKey(): String {
                return "af7ce3f9-462d-4df1-815f-09314bb87ca3" // Please replace with your own Prebid API Key
            }

            override fun getPrebidHostUrl(): String {
                return "https://msp.newsbreak.com/openrtb2/auction"
            }
        }
        val initListener = object: MSPInitListener {
            override fun onComplete(status: MSPInitStatus, message: String) {
                Logger.info("MSP SDK is initialized: $status")
            }
        }

        val timeTakenInit = measureTimeMillis { MSP.init(applicationContext, initParams, initListener) }
        Logger.info("MSP.init() DURATION: $timeTakenInit ms")
    }
}