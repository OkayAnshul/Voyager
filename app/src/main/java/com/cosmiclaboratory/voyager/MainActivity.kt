package com.cosmiclaboratory.voyager

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cosmiclaboratory.voyager.domain.model.enums.PermissionState
import com.cosmiclaboratory.voyager.platform.coordinator.PermissionMonitor
import com.cosmiclaboratory.voyager.platform.coordinator.PermissionSnapshot
import com.cosmiclaboratory.voyager.presentation.navigation.VoyagerDestination
import com.cosmiclaboratory.voyager.presentation.navigation.VoyagerDestination.Companion.navigateToTab
import com.cosmiclaboratory.voyager.presentation.screen.analytics.StatisticsScreen
import com.cosmiclaboratory.voyager.presentation.screen.dashboard.DashboardScreen
import com.cosmiclaboratory.voyager.presentation.screen.map.MapScreen
import com.cosmiclaboratory.voyager.presentation.screen.onboarding.FeatureWalkthroughScreen
import com.cosmiclaboratory.voyager.presentation.screen.onboarding.PermissionOnboardingScreen
import com.cosmiclaboratory.voyager.presentation.screen.onboarding.PersonaPickScreen
import com.cosmiclaboratory.voyager.presentation.screen.onboarding.WalkthroughPreferences
import com.cosmiclaboratory.voyager.presentation.screen.onboarding.PermissionReminderBanner
import com.cosmiclaboratory.voyager.presentation.screen.place.PlaceDetailScreen
import com.cosmiclaboratory.voyager.presentation.screen.search.SearchScreen
import com.cosmiclaboratory.voyager.presentation.screen.settings.SettingsScreen
import com.cosmiclaboratory.voyager.presentation.screen.splash.AnimatedSplashContent
import com.cosmiclaboratory.voyager.presentation.screen.timeline.TimelineScreen
import com.cosmiclaboratory.voyager.presentation.screen.export.ExportScreen
import com.cosmiclaboratory.voyager.presentation.screen.feedback.FeedbackScreen
import com.cosmiclaboratory.voyager.presentation.screen.review.PlaceReviewScreen
import com.cosmiclaboratory.voyager.presentation.screen.debug.DebugDataInsertionScreen
import com.cosmiclaboratory.voyager.presentation.screen.debug.PipelineDebugScreen
import com.cosmiclaboratory.voyager.presentation.screen.developer.DeveloperProfileScreen
import com.cosmiclaboratory.voyager.presentation.screen.developer.OpenSourceLicensesScreen
import com.cosmiclaboratory.voyager.presentation.screen.segment.SegmentDetailSheet
import com.cosmiclaboratory.voyager.presentation.screen.visit.VisitDetailSheet
import com.cosmiclaboratory.voyager.presentation.state.SharedUiState
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerGradients
import com.cosmiclaboratory.voyager.ui.theme.VoyagerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private enum class AppPhase { SPLASH, RESTORE, ONBOARDING, PERSONA, WALKTHROUGH, MAIN }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var permissionMonitor: PermissionMonitor
    @Inject lateinit var sharedUiState: SharedUiState
    @Inject lateinit var walkthroughPreferences: WalkthroughPreferences
    @Inject lateinit var restorePreferences: com.cosmiclaboratory.voyager.presentation.screen.onboarding.RestorePreferences
    @Inject lateinit var settingsRepository: com.cosmiclaboratory.voyager.domain.repository.SettingsRepository

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionMonitor.refresh() }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionMonitor.refresh() }

    private val activityRecognitionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionMonitor.refresh() }

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionMonitor.refresh() }

    override fun onResume() {
        super.onResume()
        permissionMonitor.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        permissionMonitor.refresh()

        setContent {
            VoyagerTheme {
                val permissionState by permissionMonitor.permissionState.collectAsState()
                val needsOnboarding = permissionState == PermissionState.NOTHING ||
                    permissionState == PermissionState.NO_LOCATION_WITH_AR
                val hasSeenWalkthrough by walkthroughPreferences.hasSeen
                    .collectAsState(initial = true)
                val hasSeenRestore by restorePreferences.hasSeen
                    .collectAsState(initial = true)
                val settings by settingsRepository.observeSettings().collectAsState()
                val hasChosenPersona = settings.activeJob.isNotBlank()

                // FLAG_SECURE — hides app content in the recents switcher and blocks
                // screenshots when the user enables it in Privacy settings.
                LaunchedEffect(settings.flagSecureEnabled) {
                    if (settings.flagSecureEnabled) {
                        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }

                val coroutineScope = rememberCoroutineScope()
                var phase by remember { mutableStateOf(AppPhase.SPLASH) }

                fun nextAfterPermissions(): AppPhase = when {
                    !hasChosenPersona -> AppPhase.PERSONA
                    !hasSeenWalkthrough -> AppPhase.WALKTHROUGH
                    else -> AppPhase.MAIN
                }

                when (phase) {
                    AppPhase.SPLASH -> {
                        AnimatedSplashContent(onComplete = {
                            phase = when {
                                // Fresh install: offer a one-time restore before onboarding.
                                needsOnboarding && !hasSeenRestore -> AppPhase.RESTORE
                                needsOnboarding -> AppPhase.ONBOARDING
                                else -> nextAfterPermissions()
                            }
                        })
                    }
                    AppPhase.RESTORE -> {
                        com.cosmiclaboratory.voyager.presentation.screen.onboarding.RestoreScreen(
                            onComplete = {
                                phase = if (needsOnboarding) {
                                    AppPhase.ONBOARDING
                                } else {
                                    nextAfterPermissions()
                                }
                            }
                        )
                    }
                    AppPhase.ONBOARDING -> {
                        PermissionOnboardingScreen(onComplete = {
                            permissionMonitor.refresh()
                            phase = nextAfterPermissions()
                        })
                    }
                    AppPhase.PERSONA -> {
                        PersonaPickScreen(onComplete = {
                            phase = if (!hasSeenWalkthrough) AppPhase.WALKTHROUGH else AppPhase.MAIN
                        })
                    }
                    AppPhase.WALKTHROUGH -> {
                        FeatureWalkthroughScreen(onComplete = {
                            coroutineScope.launch { walkthroughPreferences.markSeen() }
                            phase = AppPhase.MAIN
                        })
                    }
                    AppPhase.MAIN -> {
                        val snap by permissionMonitor.snapshot.collectAsState()
                        VoyagerApp(
                            sharedUiState = sharedUiState,
                            permissionSnapshot = snap,
                            onRequestLocationPermissions = {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            onRequestBackgroundLocation = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    backgroundLocationLauncher.launch(
                                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                    )
                                }
                            },
                            onRequestNotifications = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onRequestActivityRecognition = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                                }
                            },
                            onRequestBatteryOptimization = {
                                @Suppress("BatteryLife")
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    android.net.Uri.parse("package:${packageName}")
                                )
                                startActivity(intent)
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Permission action callbacks — provided via CompositionLocal so any screen can trigger them. */
data class PermissionActions(
    val requestLocationPermissions: () -> Unit = {},
    val requestBackgroundLocation: () -> Unit = {},
    val requestNotifications: () -> Unit = {},
    val requestActivityRecognition: () -> Unit = {},
    val requestBatteryOptimization: () -> Unit = {}
)

val LocalPermissionActions = staticCompositionLocalOf { PermissionActions() }
val LocalPermissionSnapshot = staticCompositionLocalOf { PermissionSnapshot() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoyagerApp(
    sharedUiState: SharedUiState,
    permissionSnapshot: PermissionSnapshot = PermissionSnapshot(),
    onRequestLocationPermissions: () -> Unit = {},
    onRequestBackgroundLocation: () -> Unit = {},
    onRequestNotifications: () -> Unit = {},
    onRequestActivityRecognition: () -> Unit = {},
    onRequestBatteryOptimization: () -> Unit = {}
) {
    val permissionActions = remember {
        PermissionActions(
            requestLocationPermissions = onRequestLocationPermissions,
            requestBackgroundLocation = onRequestBackgroundLocation,
            requestNotifications = onRequestNotifications,
            requestActivityRecognition = onRequestActivityRecognition,
            requestBatteryOptimization = onRequestBatteryOptimization
        )
    }
    val pendingReviewCount by sharedUiState.pendingReviewCount.collectAsState()
    val navController = rememberNavController()
    val bottomNavItems = VoyagerDestination.bottomNavItems
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    // Derive selected tab from actual navigation state — no separate mutable state
    val selectedTab = if (currentRoute in bottomNavItems.map { it.route }) currentRoute else bottomNavItems.first().route

    // Determine if bottom nav and top bar should be visible (hide on push-nav screens)
    val showBottomNav = currentRoute in bottomNavItems.map { it.route }
    val showTopBar = showBottomNav

    val snackbarHostState = remember { SnackbarHostState() }
    val appScope = rememberCoroutineScope()

    CompositionLocalProvider(
        LocalPermissionActions provides permissionActions,
        LocalPermissionSnapshot provides permissionSnapshot
    ) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (showTopBar) Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VoyagerGradients.topBar)
            ) {
            TopAppBar(
                title = {
                    var showVoyagerStory by remember { mutableStateOf(false) }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) {
                            showVoyagerStory = true
                        }
                    ) {
                        // Orion's Belt: 3 tiny dots offset like the real asterism
                        Canvas(modifier = Modifier.size(18.dp, 18.dp)) {
                            val cx = size.width / 2
                            val cy = size.height / 2
                            val r = 2.2.dp.toPx()
                            val pts = listOf(
                                Offset(cx - 5.dp.toPx(), cy + 2.dp.toPx()),
                                Offset(cx, cy - 1.dp.toPx()),
                                Offset(cx + 5.dp.toPx(), cy + 3.dp.toPx())
                            )
                            pts.forEach { drawCircle(VoyagerColors.PrimaryDim, r, it) }
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Voyager",
                            fontFamily = com.cosmiclaboratory.voyager.ui.theme.GreatVibesFontFamily,
                            fontSize = 26.sp,
                            letterSpacing = 0.5.sp,
                            color = VoyagerColors.Primary
                        )
                    }

                    if (showVoyagerStory) {
                        ModalBottomSheet(
                            onDismissRequest = { showVoyagerStory = false },
                            containerColor = VoyagerColors.SurfaceOverlay
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Why \"Voyager\"?",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = VoyagerColors.Primary
                                )
                                Text(
                                    text = "Named after the Voyager 1 and Voyager 2 space probes — the farthest human-made objects from Earth. They left in 1977 carrying the Golden Record: a snapshot of humanity, sent into the unknown.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = VoyagerColors.OnSurface
                                )
                                Text(
                                    text = "This app is the inverse. Instead of broadcasting, it listens — quietly recording your own small voyages and keeping them right here, on your device. Your Golden Record stays with you.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = VoyagerColors.OnSurface
                                )
                                Text(
                                    text = "Every journey is an experiment in the Cosmic Laboratory.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = VoyagerColors.Primary,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                },
                actions = {
                    // Search icon → Search screen
                    IconButton(onClick = {
                        navController.navigate(VoyagerDestination.Search.route)
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = VoyagerColors.OnSurfaceVariant
                        )
                    }
                    // Bell icon — pending review count badge → PlaceReview (guard when empty)
                    IconButton(onClick = {
                        if (pendingReviewCount > 0) {
                            navController.navigate(VoyagerDestination.PlaceReview.route)
                        } else {
                            appScope.launch {
                                snackbarHostState.showSnackbar("No places need review")
                            }
                        }
                    }) {
                        BadgedBox(
                            badge = {
                                if (pendingReviewCount > 0) {
                                    Badge(
                                        containerColor = VoyagerColors.Error,
                                        contentColor = VoyagerColors.OnPrimary
                                    ) {
                                        Text(
                                            text = if (pendingReviewCount > 99) "99+" else pendingReviewCount.toString(),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = "Reviews",
                                tint = VoyagerColors.OnSurfaceVariant
                            )
                        }
                    }
                    // Gear icon → Settings (push nav)
                    IconButton(onClick = {
                        navController.navigate(VoyagerDestination.Settings.route)
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = VoyagerColors.OnSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
            } // Box
        },
        bottomBar = {
            if (showBottomNav) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VoyagerGradients.navBar)
                ) {
                NavigationBar(
                    containerColor = Color.Transparent
                ) {
                    bottomNavItems.forEachIndexed { _, destination ->
                        NavigationBarItem(
                            selected = selectedTab == destination.route,
                            onClick = {
                                navController.navigateToTab(destination.route)
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.title) },
                            label = { Text(destination.title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = VoyagerColors.Primary,
                                selectedTextColor = VoyagerColors.Primary,
                                unselectedIconColor = VoyagerColors.OnSurfaceVariant,
                                unselectedTextColor = VoyagerColors.OnSurfaceVariant,
                                indicatorColor = VoyagerColors.PrimaryContainer
                            )
                        )
                    }
                }
                } // Box
            }
        }
    ) { paddingValues ->
        val context = androidx.compose.ui.platform.LocalContext.current
        Column(modifier = Modifier.padding(paddingValues)) {
        // Permission reminder banner — shown when any permission is missing
        PermissionReminderBanner(snapshot = permissionSnapshot)

        NavHost(
            navController = navController,
            startDestination = VoyagerDestination.Home.route,
            modifier = Modifier.weight(1f)
        ) {
            composable(VoyagerDestination.Home.route) {
                DashboardScreen(
                    onNavigateToInsights = {
                        navController.navigateToTab(VoyagerDestination.Insights.route)
                    },
                    onNavigateToExport = {
                        navController.navigate(VoyagerDestination.Export.route)
                    },
                    onNavigateToSearch = {
                        navController.navigate(VoyagerDestination.Search.route)
                    },
                    onRunPlaceDetection = {
                        val wm = androidx.work.WorkManager.getInstance(context.applicationContext)
                        val request = androidx.work.OneTimeWorkRequestBuilder<com.cosmiclaboratory.voyager.platform.worker.DiscoverPlacesWorker>()
                            .build()
                        wm.enqueueUniqueWork(
                            "detect_places_manual",
                            androidx.work.ExistingWorkPolicy.REPLACE,
                            request
                        )
                    }
                )
            }
            composable(VoyagerDestination.Map.route) {
                MapScreen()
            }
            composable(VoyagerDestination.Timeline.route) {
                TimelineScreen(
                    onSegmentClick = { segmentId ->
                        navController.navigate(VoyagerDestination.SegmentDetail.createRoute(segmentId))
                    },
                    onPlaceClick = { placeId ->
                        navController.navigate(VoyagerDestination.PlaceDetail.createRoute(placeId))
                    },
                    onShowOnMap = { segmentId ->
                        navController.navigateToTab(VoyagerDestination.Map.route)
                    }
                )
            }
            // UI-1.05: Insights routes to StatisticsScreen (NOT DashboardScreen)
            composable(VoyagerDestination.Insights.route) {
                StatisticsScreen(
                    onNavigateToPaywall = {
                        navController.navigate(VoyagerDestination.Paywall.route)
                    }
                )
            }
            // Settings — push-nav from top bar gear icon
            composable(VoyagerDestination.Settings.route) {
                SettingsScreen(
                    onNavigateToDebugDataInsertion = {
                        navController.navigate(VoyagerDestination.DebugDataInsertion.route)
                    },
                    onNavigateToDeveloperProfile = {
                        navController.navigate(VoyagerDestination.DeveloperProfile.route)
                    },
                    onNavigateToPipelineDebug = {
                        navController.navigate(VoyagerDestination.PipelineDebug.route)
                    },
                    onNavigateToExport = {
                        navController.navigate(VoyagerDestination.Export.route)
                    },
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToFeedback = {
                        navController.navigate(VoyagerDestination.Feedback.route)
                    },
                    onNavigateToOpenSourceLicenses = {
                        navController.navigate(VoyagerDestination.OpenSourceLicenses.route)
                    },
                    onNavigateToReliability = {
                        navController.navigate(VoyagerDestination.Reliability.route)
                    },
                    onNavigateToMileage = {
                        navController.navigate(VoyagerDestination.Mileage.route)
                    },
                    onNavigateToPaywall = {
                        navController.navigate(VoyagerDestination.Paywall.route)
                    }
                )
            }
            composable(VoyagerDestination.Reliability.route) {
                com.cosmiclaboratory.voyager.presentation.screen.reliability.ReliabilityScreen()
            }
            composable(VoyagerDestination.Mileage.route) {
                com.cosmiclaboratory.voyager.presentation.screen.mileage.MileageScreen(
                    onNavigateToPaywall = {
                        navController.navigate(VoyagerDestination.Paywall.route)
                    }
                )
            }
            composable(VoyagerDestination.Paywall.route) {
                com.cosmiclaboratory.voyager.presentation.billing.PaywallScreen(
                    onClose = { navController.popBackStack() }
                )
            }
            // PlaceReview — push-nav from top bar bell icon
            composable(VoyagerDestination.PlaceReview.route) {
                PlaceReviewScreen()
            }
            composable(VoyagerDestination.Export.route) {
                ExportScreen(onBack = { navController.popBackStack() })
            }
            composable(VoyagerDestination.Search.route) {
                SearchScreen(
                    onPlaceClick = { placeId ->
                        navController.navigate(VoyagerDestination.PlaceDetail.createRoute(placeId))
                    }
                )
            }
            composable(
                route = VoyagerDestination.PlaceDetail.route,
                arguments = listOf(navArgument("placeId") { type = NavType.LongType })
            ) {
                PlaceDetailScreen(onNavigateBack = { navController.popBackStack() })
            }
            // Debug screens (debug builds only — guarded from production APKs)
            if (com.cosmiclaboratory.voyager.BuildConfig.DEBUG) {
                composable(VoyagerDestination.DebugDataInsertion.route) {
                    DebugDataInsertionScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(VoyagerDestination.PipelineDebug.route) {
                    PipelineDebugScreen()
                }
            }
            composable(VoyagerDestination.DeveloperProfile.route) {
                DeveloperProfileScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToLicenses = {
                        navController.navigate(VoyagerDestination.OpenSourceLicenses.route)
                    }
                )
            }
            composable(VoyagerDestination.OpenSourceLicenses.route) {
                OpenSourceLicensesScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(VoyagerDestination.Feedback.route) {
                FeedbackScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(
                route = VoyagerDestination.SegmentDetail.route,
                arguments = listOf(navArgument("segmentId") { type = NavType.LongType })
            ) {
                SegmentDetailSheet(
                    onDismiss = { navController.popBackStack() },
                    onNavigateToPaywall = {
                        navController.popBackStack()
                        navController.navigate(VoyagerDestination.Paywall.route)
                    }
                )
            }
            composable(
                route = VoyagerDestination.VisitDetail.route,
                arguments = listOf(navArgument("visitId") { type = NavType.LongType })
            ) {
                VisitDetailSheet(
                    onDismiss = { navController.popBackStack() },
                    onNavigateToPlace = { placeId ->
                        navController.navigate(VoyagerDestination.PlaceDetail.createRoute(placeId))
                    },
                    onNavigateToPaywall = {
                        navController.popBackStack()
                        navController.navigate(VoyagerDestination.Paywall.route)
                    }
                )
            }
        }
        } // Column
    }
    } // CompositionLocalProvider
}
