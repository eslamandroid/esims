package com.eaapps.esims

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.telephony.euicc.DownloadableSubscription
import android.telephony.euicc.EuiccManager
import android.telephony.euicc.EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_DETAILED_CODE
import android.telephony.euicc.EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_ERROR_CODE
import android.telephony.euicc.EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_OPERATION_CODE
import android.telephony.euicc.EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_SMDX_REASON_CODE
import android.telephony.euicc.EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_SMDX_SUBJECT_CODE
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.eaapps.esims.ui.theme.EsimsTheme


private const val TEST_SIM_PROFILE = "LPA:1\$smdp.io\$57-25TL52-3YI05T"

private const val DOWNLOAD_ACTION = "download_subscription"
private const val START_RESOLUTION_ACTION = "start_resolution_action"
private const val BROADCAST_PERMISSION = "com.eaapps.esims.lpa.permission.BROADCAST"


class MainActivity : ComponentActivity() {
    private var manager: EuiccManager? = null
    private lateinit var lPAActivityLauncher: ActivityResultLauncher<Intent>

    private val eSimBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when {
                /**
                 * Check if same action is triggered again after the allow permission dialog.
                 * if not will throw IntentSender.SendIntentException again fails to be mentioned clearly on android docs
                 */
                DOWNLOAD_ACTION != intent?.action -> return

                resultCode == EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK -> Toast.makeText(
                    context,
                    "Successfully installed eSIM",
                    Toast.LENGTH_SHORT
                ).show()

                resultCode == EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR -> {
                    // Recoverable might be permission issue system will show dialog to allow or deny
                    val startIntent = Intent(START_RESOLUTION_ACTION)
                    val callbackIntent = PendingIntent.getBroadcast(
                        context,
                        0 /* requestCode */,
                        startIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    try {
                        manager?.startResolutionActivity(
                            this@MainActivity,
                            0 /* requestCode */,
                            intent,
                            callbackIntent
                        )
                    } catch (e: IntentSender.SendIntentException) {
                        Toast.makeText(
                            context,
                            e.message ?: "error",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private val resolutionReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.R)
        override fun onReceive(context: Context?, intent: Intent?) {
            if (START_RESOLUTION_ACTION != intent?.action) {
                return
            }

            val errorCode = intent.getIntExtra(
                EXTRA_EMBEDDED_SUBSCRIPTION_ERROR_CODE,
                0 /* defaultValue*/
            )

            val operationCode =
                intent.getIntExtra(
                    EXTRA_EMBEDDED_SUBSCRIPTION_OPERATION_CODE,
                    0 /* defaultValue*/
                )

            val detailedCode =
                intent.getIntExtra(
                    EXTRA_EMBEDDED_SUBSCRIPTION_DETAILED_CODE,
                    0 /* defaultValue*/
                )

            val subjectCode =
                intent.getIntExtra(
                    EXTRA_EMBEDDED_SUBSCRIPTION_SMDX_SUBJECT_CODE,
                    0 /* defaultValue*/
                )

            val reasonCode =
                intent.getIntExtra(
                    EXTRA_EMBEDDED_SUBSCRIPTION_SMDX_REASON_CODE,
                    0 /* defaultValue*/
                )

            // Helps for debugging if there is an issue with the e-sim
            Toast.makeText(
                context,
                "Result Code: $resultCode \nError Code: $errorCode\n Operation Code: $operationCode \n Detailed Code: $detailedCode \n Subject Code: $subjectCode \n Reason Code: $reasonCode",
                Toast.LENGTH_SHORT
            ).show()
            when (resultCode) {
                EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK -> Toast.makeText(context, "Successfully installed eSIM", Toast.LENGTH_SHORT).show()
                else -> Toast.makeText(context, "Failed to install eSIM", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lPAActivityLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // Handle the result from the LPA app, such as confirming eSIM activation
                val data: Intent? = result.data
                // Process the data if needed
            } else {
                // Handle any errors or cancellations
                Toast.makeText(this, "eSIM activation was not successful", Toast.LENGTH_SHORT).show()
            }
        }
        manager = getSystemService(EUICC_SERVICE) as EuiccManager

        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            EsimsTheme {

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    EsimView(
                        context = context,
                        modifier = Modifier.padding(innerPadding),
                        downloadSim = {
                            downloadTestProfile(it)
                        },
                        openQr = {
                            openQR()
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this@MainActivity,
            eSimBroadcastReceiver,
            IntentFilter(DOWNLOAD_ACTION),
            BROADCAST_PERMISSION,
            null,
            ContextCompat.RECEIVER_EXPORTED
        )
        ContextCompat.registerReceiver(
            this@MainActivity,
            resolutionReceiver,
            IntentFilter(START_RESOLUTION_ACTION),
            BROADCAST_PERMISSION,
            null,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(eSimBroadcastReceiver)
        unregisterReceiver(resolutionReceiver)
    }

    private fun downloadTestProfile(sub: String) {
        // Check if e-sim is supported by the device note that emulators are not supported.
        kotlin.runCatching {
            manager?.let {
                if (it.isEnabled) {
                    val info = it.euiccInfo
                    val osVer = info?.osVersion
//                   showToastMessage("osVer $osVer")

                    val subscription =
                        DownloadableSubscription.forActivationCode(sub)
                    val callbackIntent = PendingIntent.getBroadcast(
                        baseContext,
                        0 /* requestCode */,
                        Intent(DOWNLOAD_ACTION).apply {
                            setPackage(packageName)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    it.downloadSubscription(subscription, true, callbackIntent)
                } else {
                    showToastMessage("eSIM is not supported on this device")
                }
            } ?: showToastMessage("eSIM is not supported on this device")
        }.getOrElse {
            showToastMessage("error:${it.message ?: "error"}")
        }
    }

    private fun openQR() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent =
                    Intent(EuiccManager.ACTION_START_EUICC_ACTIVATION).apply {
                        putExtra(EuiccManager.EXTRA_USE_QR_SCANNER, false)
                    }
                lPAActivityLauncher.launch(intent)
            } else {
                Toast.makeText(this, "not support for current android version", Toast.LENGTH_SHORT).show()

            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showToastMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

}

@SuppressLint("ServiceCast")
@Composable
fun EsimView(context: Context, modifier: Modifier, downloadSim: (String) -> Unit = {}, openQr: () -> Unit = {}) {

    val manager = EsimManager(context)
    Column(verticalArrangement = Arrangement.Center, horizontalAlignment = CenterHorizontally, modifier = modifier.fillMaxSize()) {

        Button(onClick = {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(context as Activity, arrayOf(Manifest.permission.READ_PHONE_STATE), 200)
            }
        }) {
            Text(text = "Check Permission available")
        }

        Button(onClick = {
            Toast.makeText(context, "isAvailable: ${manager.isAvailable()}", Toast.LENGTH_SHORT).show()
        }) {
            Text(text = "Check esim available")
        }

        Button(onClick = {
            openQr()
        }) {
            Text(text = "open qr")
        }

        Button(onClick = {
            runCatching {
                downloadSim(TEST_SIM_PROFILE)
            }.getOrElse {
                Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }) {
            Text(text = "Download ESIM1")
        }


        Button(onClick = {
            runCatching {
                val sb = StringBuilder()
                manager.listOfEsimProfiles().map { "${it.label} - ${it.displayName} - ${it.id}" }
                    .forEach {
                        sb.append(it).append("\n")
                    }
                Toast.makeText(context, sb.toString(), Toast.LENGTH_LONG).show()

            }.getOrElse {
                Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }) {
            Text(text = "Display List Of Esim")
        }


        Button(onClick = {
            runCatching {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager?
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(context as Activity, arrayOf(Manifest.permission.READ_PHONE_STATE), 11)

                }
                val subscriptions = subscriptionManager?.activeSubscriptionInfoList
                println(subscriptions?.map { it.displayName })


                val telephone = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?
                telephone?.apply {
                    Toast.makeText(context, telephone.networkOperatorName, Toast.LENGTH_SHORT).show()
                }

                val intent = Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS)
                context.startActivity(intent)


            }.getOrElse {
                Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }) {
            Text(text = "Open sim manager")
        }

        Button(onClick = {
            runCatching {
                val intent = Intent(Intent.ACTION_MAIN)
                intent.setClassName("com.android.phone", "com.android.phone.settings.eSimManager")
                context.startActivity(intent)

            }.getOrElse {
                Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }) {
            Text(text = "open eSim Manager")
        }
    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    val context = LocalContext.current
    EsimView(
        context = context, modifier = Modifier
    )
}