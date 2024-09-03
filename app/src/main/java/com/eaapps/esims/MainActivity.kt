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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.eaapps.esims.ui.theme.EsimsTheme


private const val TEST_SIM_PROFILE = "LPA:1\$smdp.io\$57-262E95-176EZGS"

private const val START_RESOLUTION_ACTION = "start_resolution_action"
private const val BROADCAST_PERMISSION = "com.eaapps.esims.lpa.permission.BROADCAST"


class MainActivity : ComponentActivity() {
    private var manager: EuiccManager? = null

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

        manager = getSystemService(EUICC_SERVICE) as EuiccManager

        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            EsimsTheme {

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    EsimView(
                        context = context,
                        modifier = Modifier.padding(innerPadding)
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
            null,
            null,
            ContextCompat.RECEIVER_EXPORTED
        )
        ContextCompat.registerReceiver(
            this@MainActivity,
            resolutionReceiver,
            IntentFilter(START_RESOLUTION_ACTION),
            null,
            null,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(eSimBroadcastReceiver)
        unregisterReceiver(resolutionReceiver)
    }

}

@SuppressLint("ServiceCast")
@Composable
fun EsimView(context: Context, modifier: Modifier) {
    val manager = EsimManager(context)
    var esimList by remember {
        mutableStateOf("")
    }

    Column(verticalArrangement = Arrangement.Center, horizontalAlignment = CenterHorizontally, modifier = modifier.fillMaxSize()) {

        Text(text = "Globee Test Direct Installation", modifier = Modifier.padding(vertical = 24.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        
        Button(onClick = {
            Toast.makeText(
                context,
                "isAvailable: ${if (manager.isAvailable()) "Your device supports E-SIM" else "your device doesn't support E-SIM"}",
                Toast.LENGTH_SHORT
            ).show()
        }, modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
            Text(text = "Check E-SIM support")
        }
        Button(onClick = {
            runCatching {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(context as Activity, arrayOf(Manifest.permission.READ_PHONE_STATE), 200)
                } else {
                    if (manager.isAvailable()) {
                        manager.downloadEsim(activationCode = TEST_SIM_PROFILE)
                    }
                }
            }.getOrElse {
                Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }, modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
            Text(text = "Direct activation E-SIM", textAlign = TextAlign.Center)
        }

        Button(onClick = {
            runCatching {
                val sb = StringBuilder()
                manager.listOfEsimProfiles().map {
                    "SubscriptionId : ${it?.subscriptionId}\n" +
                            "mcc : ${it?.mcc}\n" +
                            "mnc : ${it?.mnc}\n" +
                            "numeric: ${it?.numeric}\n"
                    "--------------------\n"
                }
                    .forEach {
                        sb.append(it).append("\n")
                    }
                esimList = sb.toString()

            }.getOrElse {
                Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }) {
            Text(text = "Display List E-SIM")
        }

        Text(text = esimList, modifier = Modifier.padding(vertical = 16.dp))

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