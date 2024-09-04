package com.eaapps.esims

import android.app.Service
import android.content.Intent
import android.os.IBinder

import android.service.euicc.ICarrierEuiccProvisioningService
import android.service.euicc.IGetActivationCodeCallback

class CarrierEuiccProvisioningService : Service() {
    val activeCode = "LPA:1\$smdp.io\$57-262E95-176EZGS"

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    private val binder = object : ICarrierEuiccProvisioningService.Stub () {
        override fun getActivationCode(callback: IGetActivationCodeCallback?) {
            // you can write your own logic to fetch activation code from somewhere.
            val activationCode : String = activeCode
            callback?.onSuccess(activationCode)
        }

        override fun getActivationCodeForEid(eid: String?, callback: IGetActivationCodeCallback?) {
            val activationCode : String = activeCode
            callback?.onSuccess(activationCode)
        }

    }



}