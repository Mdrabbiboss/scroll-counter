package com.rabbi.scrollcounter.ads

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Google's consent flow (required by AdMob for users in the EEA/UK).
 * Ads are only requested after consent info says it's allowed.
 */
object AdsConsent {
    private val adsStarted = AtomicBoolean(false)

    /** Compose-observable: show the banner only when true. */
    var canRequestAds by mutableStateOf(false)
        private set

    /** Compose-observable: show the "Ad privacy choices" button in Settings when true. */
    var privacyOptionsRequired by mutableStateOf(false)
        private set

    fun gather(activity: Activity) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()
        info.requestConsentInfoUpdate(
            activity, params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                    refresh(activity, info)
                }
            },
            { _ -> refresh(activity, info) }
        )
        // Consent from a previous session can be used right away.
        refresh(activity, info)
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { _ ->
            refresh(activity, UserMessagingPlatform.getConsentInformation(activity))
        }
    }

    private fun refresh(context: Context, info: ConsentInformation) {
        privacyOptionsRequired = info.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (info.canRequestAds()) {
            if (!adsStarted.getAndSet(true)) {
                val app = context.applicationContext
                Thread { MobileAds.initialize(app) {} }.start()
            }
            canRequestAds = true
        }
    }
}
