package com.eaapps.esims

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.euicc.DownloadableSubscription
import android.telephony.euicc.EuiccManager


data class Profile(val subscriptionId:Int,val name: String, val apn: String, val mcc: String, val mnc: String, val numeric: String)

const val DOWNLOAD_ACTION = "download_subscription"

class EsimManager(private val context: Context) {

    fun isAvailable() = (context.getSystemService(Context.EUICC_SERVICE) as EuiccManager?).let { it != null && it.isEnabled }

    fun downloadEsim(activationCode: String) {
        val euiccManager = context.getSystemService(Context.EUICC_SERVICE) as EuiccManager?
        if (euiccManager != null && euiccManager.isEnabled) {
            val subscription = DownloadableSubscription.forActivationCode(activationCode)
            val intent = Intent(DOWNLOAD_ACTION).apply {
                setPackage(context.packageName)
            }
            val callbackIntent = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_MUTABLE
            )
            euiccManager.downloadSubscription(subscription, true, callbackIntent)
        } else {
            throw Exception("eSIM not supported or enabled on this device.")
        }
    }

    @SuppressLint("MissingPermission")
    fun listOfEsimProfiles(): List<Profile?> {
        val euiccManager = context.getSystemService(Context.EUICC_SERVICE) as EuiccManager?
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager?
        if (euiccManager != null && subscriptionManager != null) {
            val profiles = subscriptionManager.activeSubscriptionInfoList
            return if (profiles != null && profiles.isNotEmpty()) {
                profiles.mapIndexed { index, subscriptionInfo ->
                    if (subscriptionInfo.isEmbedded) {
                        val displayName = subscriptionInfo.carrierName.toString()
                        val subscriptionId = subscriptionInfo.subscriptionId
                        val mcc = subscriptionInfo.mccString
                        val mnc = subscriptionInfo.mncString
                        Profile(subscriptionId = subscriptionId,name = displayName, apn = "", mcc = mcc ?: "-", mnc = mnc ?: "-", numeric = mcc + mnc)
                    } else {
                        null
                    }
                }.toList()
            } else {
                arrayListOf()
            }
        } else {
            throw Exception("eSIM not supported or enabled on this device.")
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun handleResolvableError(activity: Activity, intent: Intent) {
        try {
            val euiccManager = context.getSystemService(Context.EUICC_SERVICE) as EuiccManager?

            val explicitIntent = Intent(DOWNLOAD_ACTION);
            explicitIntent.apply {
                `package` = context.packageName
            }
            val callbackIntent = PendingIntent.getBroadcast(
                context, 999,
                explicitIntent, PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_MUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                euiccManager?.startResolutionActivity(
                    activity,
                    999,
                    intent,
                    callbackIntent
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun activateEsim(profile: Profile) {
        val euiccManager = context.getSystemService(Context.EUICC_SERVICE) as EuiccManager?
        if (euiccManager != null && euiccManager.isEnabled) {
            val intent = Intent(Intent.ACTION_MAIN)
            val callbackIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            euiccManager.switchToSubscription(profile.subscriptionId, callbackIntent)
        } else {
            throw Exception("eSIM not supported or enabled on this device.")
        }
    }

    fun deactivateEsim(profile: Profile) {
        val euiccManager = context.getSystemService(Context.EUICC_SERVICE) as EuiccManager?
        if (euiccManager != null && euiccManager.isEnabled) {
            val intent = Intent(Intent.ACTION_MAIN)
            val callbackIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            euiccManager.switchToSubscription(-1, callbackIntent)
        } else {
            throw Exception("eSIM not supported or enabled on this device.")
        }
    }
}

