package com.example.papasabay

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

// Central data model for routes containing fare, geofence, and dynamic barangay quotas
data class RouteInfo(
    val name: String,
    val distance: Int,
    val fare: Int,
    val geofenceMeters: Int,
    val currentQuota: Int,
    val maxQuota: Int
)

// Helper function to format numbers with correct suffixes (1st, 2nd, 3rd, 4th...)
fun getOrdinalSuffix(number: Int): String {
    if (number in 11..13) return "${number}th"
    return when (number % 10) {
        1 -> "${number}st"
        2 -> "${number}nd"
        3 -> "${number}rd"
        else -> "${number}th"
    }
}

// [ADDED FOR LAB ACT 6] Helper function to trigger real Android system pull-down & heads-up notification
fun showEnRouteNotification(context: Context, routeName: String) {
    val channelId = "papasabay_trip_channel_v2"
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "PapaSabay Live Trip Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Heads-up alerts when driver starts the trip"
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("PapaSabay: Driver is En Route!")
        .setContentText("Driver is En Route! ETA: 11 mins ($routeName)")
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .setAutoCancel(true)
        .build()

    notificationManager.notify(System.currentTimeMillis().toInt(), notification)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PapaSabayApp()
                }
            }
        }
    }
}

@Composable
fun PapaSabayApp() {
    var currentRole by remember { mutableStateOf("commuter") }

    // Centralized route database state
    val routesDb = mapOf(
        "Loakan -> City Center" to RouteInfo("Loakan -> City Center", 8, 80, 16, 6, 10),
        "Bakakeng -> City Center" to RouteInfo("Bakakeng -> City Center", 5, 50, 2500, 3, 7),
        "Irisan -> City Center" to RouteInfo("Irisan -> City Center", 7, 70, 5200, 8, 8)
    )

    // Shared state for demo synchronization between commuter & driver views
    var selectedRouteKey by remember { mutableStateOf("Loakan -> City Center") }
    var hasJoinedLine by remember { mutableStateOf(false) }
    var tripStarted by remember { mutableStateOf(false) }
    var driverArrived by remember { mutableStateOf(false) }
    var driverSeats by remember { mutableIntStateOf(4) }
    var scannedPassengerCount by remember { mutableIntStateOf(0) }

    // [ADDED FOR LAB ACT 6] Context and permission launcher for Android 13+ notifications
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted && tripStarted) {
                showEnRouteNotification(context, selectedRouteKey)
            }
        }
    )

    // [ADDED FOR LAB ACT 6] Request notification permission immediately when app launches
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9))
    ) {
        // App Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "PapaSabay MVP",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Baguio Community Carpooling",
                    color = Color(0xFF4ADE80),
                    fontSize = 12.sp
                )
            }
        }

        // Role Switcher Toggle Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { currentRole = "commuter" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentRole == "commuter") Color(0xFF2563EB) else Color.White
                ),
                modifier = Modifier.weight(1f).height(45.dp)
            ) {
                Text(
                    "Commuter View",
                    color = if (currentRole == "commuter") Color.White else Color.DarkGray,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { currentRole = "driver" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentRole == "driver") Color(0xFF16A34A) else Color.White
                ),
                modifier = Modifier.weight(1f).height(45.dp)
            ) {
                Text(
                    "Driver View",
                    color = if (currentRole == "driver") Color.White else Color.DarkGray,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Dynamic Screen Content based on Active Role
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            if (currentRole == "commuter") {
                CommuterScreen(
                    routesMap = routesDb,
                    selectedRouteKey = selectedRouteKey,
                    onRouteChanged = { selectedRouteKey = it },
                    hasJoinedLine = hasJoinedLine,
                    onJoinLine = { hasJoinedLine = true },
                    tripStarted = tripStarted,
                    driverArrived = driverArrived,
                    scannedPassengerCount = scannedPassengerCount
                )
            } else {
                DriverScreen(
                    routesMap = routesDb,
                    selectedRouteKey = selectedRouteKey,
                    onRouteChanged = { selectedRouteKey = it },
                    driverSeats = driverSeats,
                    onSeatsChanged = { driverSeats = it },
                    tripStarted = tripStarted,
                    onStartTrip = {
                        tripStarted = true
                        // [ADDED FOR LAB ACT 6] Fire real system notification when driver starts trip
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                                showEnRouteNotification(context, selectedRouteKey)
                            } else {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else {
                            showEnRouteNotification(context, selectedRouteKey)
                        }
                    },
                    driverArrived = driverArrived,
                    onArrived = { driverArrived = true },
                    scannedPassengerCount = scannedPassengerCount,
                    onScanNextPassenger = {
                        if (scannedPassengerCount < driverSeats) {
                            scannedPassengerCount += 1
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun CommuterScreen(
    routesMap: Map<String, RouteInfo>,
    selectedRouteKey: String,
    onRouteChanged: (String) -> Unit,
    hasJoinedLine: Boolean,
    onJoinLine: () -> Unit,
    tripStarted: Boolean,
    driverArrived: Boolean,
    scannedPassengerCount: Int
) {
    val currentRoute = routesMap[selectedRouteKey] ?: routesMap.values.first()
    val isWithinGeofence = currentRoute.geofenceMeters <= 20
    val isUserScanned = scannedPassengerCount >= 3

    // [ADDED FOR LAB ACT 6] Get Activity reference so the Exit button can close the app
    val context = LocalContext.current
    val activity = context as? Activity

    // State to control visibility of the 3-action confirmation Dialog Box
    var showConfirmQueueDialog by remember { mutableStateOf(false) }

    // [ADDED FOR LAB ACT 6] 3-Action Confirmation Dialog Box
    if (showConfirmQueueDialog) {
        AlertDialog(
            onDismissRequest = { /* Intentionally empty so commuter must pick one of the 3 buttons */ },
            title = {
                Text(text = "Confirm Virtual Line Entry", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Once you join the line for $selectedRouteKey (₱${currentRoute.fare}), you will be locked into the queue so drivers are guaranteed passengers upon arrival. Do you wish to proceed?")
            },
            confirmButton = {
                // Action 1: Continue (Locks commuter into the virtual line)
                Button(
                    onClick = {
                        showConfirmQueueDialog = false
                        onJoinLine()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Action 2: Cancel (Dismisses dialog only, lets commuter think longer)
                    OutlinedButton(onClick = { showConfirmQueueDialog = false }) {
                        Text("Cancel")
                    }
                    // Action 3: Exit (Closes the entire application)
                    TextButton(onClick = {
                        showConfirmQueueDialog = false
                        activity?.finish()
                    }) {
                        Text("Exit", color = Color.Red)
                    }
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Route Selector Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Station Route", fontWeight = FontWeight.Bold, color = Color.DarkGray)
                Spacer(modifier = Modifier.height(8.dp))

                routesMap.keys.forEach { routeKey ->
                    Button(
                        onClick = {
                            if (!hasJoinedLine) {
                                onRouteChanged(routeKey)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedRouteKey == routeKey) Color(0xFF2563EB) else Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(routeKey, color = if (selectedRouteKey == routeKey) Color.White else Color.DarkGray)
                    }
                }
            }
        }

        // Status Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Cash Fare (₱10/km):")
                    Text("₱${currentRoute.fare}", fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Distance to Station:")
                    Text("${currentRoute.geofenceMeters} meters", fontWeight = FontWeight.Bold, color = if (isWithinGeofence) Color(0xFF16A34A) else Color.Red)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isWithinGeofence) Color(0xFFDCFCE7) else Color(0xFFFEE2E2), shape = RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isWithinGeofence) "Within 20m Geofence (Unlocked)" else "Outside Geofence (Too far)",
                        color = if (isWithinGeofence) Color(0xFF16A34A) else Color(0xFFDC2626),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Action / State Output
        if (!hasJoinedLine) {
            Button(
                onClick = { showConfirmQueueDialog = true },
                enabled = isWithinGeofence,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Join Virtual Line", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Queue Status (Locked In)", fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A), modifier = Modifier.align(Alignment.Start))

                    if (!isUserScanned) {
                        val queuePosition = maxOf(1, 3 - scannedPassengerCount)
                        val ordinalPosition = getOrdinalSuffix(queuePosition)
                        Text("You are $ordinalPosition in line at $selectedRouteKey.", modifier = Modifier.align(Alignment.Start))
                    } else {
                        Text("✔ Boarded successfully! Paid ₱${currentRoute.fare} cash.", color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
                    }

                    if (tripStarted && !driverArrived && !isUserScanned) {
                        Text("Driver is En Route! ETA: 11 mins", color = Color(0xFFD97706), fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                    } else if (driverArrived && !isUserScanned) {
                        Text("Driver Arrived! Show QR code pass to board.", color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)

                        // VISUAL MOCK QR CODE GRAPHIC
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .border(2.dp, Color(0xFF2563EB), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Box(modifier = Modifier.size(28.dp).background(Color.Black, RoundedCornerShape(4.dp)))
                                    Box(modifier = Modifier.size(14.dp).background(Color.DarkGray))
                                    Box(modifier = Modifier.size(28.dp).background(Color.Black, RoundedCornerShape(4.dp)))
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                    Box(modifier = Modifier.size(16.dp).background(Color.DarkGray))
                                    Box(modifier = Modifier.size(16.dp).background(Color.Black))
                                    Box(modifier = Modifier.size(16.dp).background(Color.DarkGray))
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Box(modifier = Modifier.size(28.dp).background(Color.Black, RoundedCornerShape(4.dp)))
                                    Box(modifier = Modifier.size(20.dp).background(Color.LightGray))
                                    Box(modifier = Modifier.size(14.dp).background(Color.Black))
                                }
                            }
                            Surface(
                                color = Color.Black.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "PASS #1043",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DriverScreen(
    routesMap: Map<String, RouteInfo>,
    selectedRouteKey: String,
    onRouteChanged: (String) -> Unit,
    driverSeats: Int,
    onSeatsChanged: (Int) -> Unit,
    tripStarted: Boolean,
    onStartTrip: () -> Unit,
    driverArrived: Boolean,
    onArrived: () -> Unit,
    scannedPassengerCount: Int,
    onScanNextPassenger: () -> Unit
) {
    val currentRoute = routesMap[selectedRouteKey] ?: routesMap.values.first()
    val isQuotaFull = currentRoute.currentQuota >= currentRoute.maxQuota
    val isFullyBoarded = scannedPassengerCount >= driverSeats

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Route Selector Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Target Route", fontWeight = FontWeight.Bold, color = Color.DarkGray)
                Spacer(modifier = Modifier.height(8.dp))
                routesMap.keys.forEach { routeKey ->
                    Button(
                        onClick = { onRouteChanged(routeKey) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedRouteKey == routeKey) Color(0xFF16A34A) else Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(routeKey, color = if (selectedRouteKey == routeKey) Color.White else Color.DarkGray)
                    }
                }
            }
        }

        // Driver Configuration Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Available Spare Seats:")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { if (driverSeats > 1 && !tripStarted) onSeatsChanged(driverSeats - 1) }) { Text("-") }
                        Text("$driverSeats", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Button(onClick = { if (driverSeats < 6 && !tripStarted) onSeatsChanged(driverSeats + 1) }) { Text("+") }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Barangay Route Quota:")
                    Text("${currentRoute.currentQuota} / ${currentRoute.maxQuota} Active Drivers", fontWeight = FontWeight.Bold, color = if (isQuotaFull) Color.Red else Color(0xFF2563EB))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isQuotaFull) Color(0xFFFEE2E2) else Color(0xFFDCFCE7), shape = RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isQuotaFull) "Route Quota Full. Cannot start trip." else "Quota Permitted. You may start trip.",
                        color = if (isQuotaFull) Color(0xFFDC2626) else Color(0xFF16A34A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Action flow (Disabled if quota is full)
        if (!tripStarted) {
            Button(
                onClick = onStartTrip,
                enabled = !isQuotaFull,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF16A34A),
                    disabledContainerColor = Color.Gray
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Start Trip (Notify Commuters)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        } else if (!driverArrived) {
            Button(
                onClick = onArrived,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("I'm Here (Arrived at Station)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Station Verification Scanner Mode", fontWeight = FontWeight.Bold, color = Color(0xFF065F46))

                    Text(
                        text = "Boarding Progress: $scannedPassengerCount / $driverSeats Passengers",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF1E3A8A)
                    )

                    val totalCashCollected = scannedPassengerCount * currentRoute.fare
                    Text(
                        text = "Total Cash Collected: ₱$totalCashCollected",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF16A34A)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (!isFullyBoarded) {
                        Button(
                            onClick = onScanNextPassenger,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Scan Next Passenger QR Pass (#104${scannedPassengerCount + 1})")
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFDCFCE7), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🚀 All $driverSeats Seats Full & Verified! Ready to Depart.",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}