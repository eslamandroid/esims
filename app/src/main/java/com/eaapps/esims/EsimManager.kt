package com.eaapps.esims

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telephony.SubscriptionManager
import android.telephony.euicc.DownloadableSubscription
import android.telephony.euicc.EuiccManager


data class Profile(val id: Int = -1, val label: String = "", val displayName: String = "")

class EsimManager(private val context: Context) {

    fun isAvailable() = (context.getSystemService(Context.EUICC_SERVICE) as EuiccManager?).let { it != null && it.isEnabled }

     fun downloadEsim(activationCode: String) {
        val euiccManager = context.getSystemService(Context.EUICC_SERVICE) as EuiccManager?
        if (euiccManager != null && euiccManager.isEnabled) {
            val subscription = DownloadableSubscription.forActivationCode(activationCode)
            val intent = Intent(Intent.ACTION_MAIN)
            val callbackIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            euiccManager.downloadSubscription(subscription, true, callbackIntent)
        } else {
            throw Exception("eSIM not supported or enabled on this device.")
        }
    }

     @SuppressLint("MissingPermission")
     fun listOfEsimProfiles(): List<Profile> {
        val euiccManager = context.getSystemService(Context.EUICC_SERVICE) as EuiccManager?
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager?
        if (euiccManager != null && subscriptionManager != null) {
            val profiles = subscriptionManager.activeSubscriptionInfoList
            return if (profiles != null && profiles.isNotEmpty()) {
                profiles.mapIndexed { index, subscriptionInfo ->
                    if (subscriptionInfo.isEmbedded) {
                        val esimLabel = "eSIM$index"
                        val displayName = subscriptionInfo.displayName.toString()
                        val subscriptionId = subscriptionInfo.subscriptionId
                        Profile(subscriptionId, esimLabel, displayName)
                    } else {
                        Profile()
                    }
                }.toList()
            } else {
                arrayListOf()
            }
        } else {
            throw Exception("eSIM not supported or enabled on this device.")
        }
    }

     fun activateEsim(profile: Profile) {
        val euiccManager = context.getSystemService(Context.EUICC_SERVICE) as EuiccManager?
        if (euiccManager != null && euiccManager.isEnabled) {
            val intent = Intent(Intent.ACTION_MAIN)
            val callbackIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            euiccManager.switchToSubscription(profile.id, callbackIntent)
        } else {
            throw Exception("eSIM not supported or enabled on this device.")
        }
    }


     fun deactivateEsim(profile: Profile) {
        val euiccManager = context.getSystemService(Context.EUICC_SERVICE) as EuiccManager?
        if (euiccManager != null && euiccManager.isEnabled) {
            val intent = Intent(Intent.ACTION_MAIN)
            val callbackIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            euiccManager.switchToSubscription(-1, callbackIntent);
        } else {
            throw Exception("eSIM not supported or enabled on this device.")
        }
    }

}