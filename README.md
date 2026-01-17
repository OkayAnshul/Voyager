# 🧭 Voyager - Location Analytics Android App

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Kotlin](https://img.shields.io/badge/kotlin-2.0.21-blue)]()
[![License](https://img.shields.io/badge/license-MIT-green)]()
[![Min SDK](https://img.shields.io/badge/min%20sdk-24-orange)]()
[![Target SDK](https://img.shields.io/badge/target%20sdk-36-orange)]()

Voyager is a sophisticated Android location analytics application that tracks user movements, detects significant places, and provides intelligent insights using GPS data, (machine) learning algorithms, and privacy-focused design.

## ✨ Features

### 🗺️ **Core Functionality**
- **Real-time Location Tracking**: Background GPS tracking with configurable accuracy modes
- **Intelligent Place Detection**: DBSCAN clustering algorithm to identify meaningful locations
- **Visit Analytics**: Automatic detection of arrival/departure times and visit durations
- **Interactive Maps**: Free OpenStreetMap integration with route visualization
- **Privacy-First**: Encrypted local database storage with SQLCipher

### ⚙️ **User Configuration**
- **Tracking Settings**: Adjustable update intervals, distance thresholds, and battery modes
- **Place Detection**: Configurable clustering distance, minimum points, and session break times
- **Smart Notifications**: Customizable arrival/departure alerts and pattern notifications
- **Data Management**: Flexible retention periods and export options

### 📊 **Analytics & Insights**
- **Place Categorization**: Automatic classification (Home, Work, Gym, Shopping, Restaurant)
- **Movement Patterns**: Timeline view with detailed visit history
- **Statistical Insights**: Time spent analysis and movement trends
- **Export Capabilities**: JSON, CSV data export with privacy controls

## 📚 Documentation

### Primary Documentation
- **[Voyager Complete Guide](docs/VOYAGER_COMPLETE_GUIDE.md)** - Comprehensive technical documentation (15,000+ words)
- **[Architecture Guide](VOYAGER_ARCHITECTURE_GUIDE.md)** - Clean Architecture implementation details
- **[Project Summary](PROJECT_SUMMARY.md)** - ATS-optimized resume summary

### Specialized Guides (Appendices)
- **[API Reference](docs/appendices/API_REFERENCE.md)** - Complete function wiring map
- **[Design Evolution](docs/appendices/DESIGN_EVOLUTION.md)** - Historical timeline and decisions
- **[Unused Functions](docs/appendices/UNUSED_FUNCTIONS.md)** - Dead code analysis
- **[Technology Stack](docs/appendices/TECHNOLOGY_STACK.md)** - Deep dive into tech choices
- **[Flaws & Advances](docs/appendices/FLAWS_AND_ADVANCES.md)** - Honest technical assessment

### Development Guides
- **[UI Enhancement Roadmap](docs/UI_ENHANCEMENT_ROADMAP.md)** - Future UI/UX improvements
- **[Manual Testing Guide](docs/MANUAL_TESTING_GUIDE.md)** - Testing procedures (40+ test cases)
- **[Automated Testing Strategy](docs/AUTOMATED_TESTING_STRATEGY.md)** - Test implementation plan
- **[Comprehensive Gap Analysis](docs/COMPREHENSIVE_GAP_ANALYSIS.md)** - Technical debt analysis

### Historical Documentation
See `/archive/README.md` for archived session logs, implementation plans, and project evolution history.

---

## 🏗️ Architecture

Voyager follows **Clean Architecture** principles with modern Android development practices:

```
📦 Voyager
├── 🎨 Presentation Layer (Jetpack Compose)
│   ├── Screens (Dashboard, Map, Timeline, Settings, Insights)
│   ├── ViewModels (MVVM pattern)
│   └── Components (Reusable UI components)
├── 💼 Domain Layer
│   ├── Use Cases (Business logic)
│   ├── Models (Data entities)
│   └── Repositories (Abstract interfaces)
├── 🗃️ Data Layer
│   ├── Repositories (Concrete implementations)
│   ├── Database (Room + SQLCipher)
│   ├── Services (Location tracking, geofencing)
│   └── Workers (Background tasks)
└── 🔧 Utils & DI (Hilt dependency injection)
```

## 🚀 Getting Started

### Prerequisites

- **Android Studio**: Hedgehog+ (2023.1.1 or later)
- **Java**: JDK 11 or higher
- **Kotlin**: 2.0.21 (included with AS)
- **Min SDK**: Android 7.0 (API 24)
- **Target SDK**: Android 14+ (API 36)

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/your-username/voyager.git
   cd voyager
   ```

2. **Open in Android Studio**
   ```bash
   # Launch Android Studio and open the project folder
   studio .
   ```

3. **Sync Project**
   - Android Studio will automatically detect and sync Gradle files
   - Wait for dependency downloads to complete

4. **Build the project**
   ```bash
   ./gradlew assembleDebug
   ```

### Configuration

#### Required Permissions

The app requires several permissions that are automatically requested at runtime:

```xml
<!-- Core location permissions -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

<!-- Foreground service for location tracking -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />

<!-- Additional permissions -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

#### No API Keys Required 🎉

Voyager uses **free and open-source** services:
- **OpenStreetMap**: Free alternative to Google Maps
- **OSMDroid**: Open-source mapping library
- **No Google API keys needed**

## 📱 Usage

### First Launch

1. **Grant Permissions**: The app will request location permissions
2. **Background Location**: Grant "Allow all the time" for continuous tracking
3. **Start Tracking**: Toggle location tracking in Settings
4. **Place Detection**: Places will be automatically detected after sufficient data

### Key Screens

#### 🏠 **Dashboard**
- Overview of tracking status and recent activity
- Quick stats on places and visits
- Real-time location information

#### 🗺️ **Map**
- Interactive map showing tracked routes
- Place markers with visit counts
- Real-time location indicator

#### 📅 **Timeline**
- Chronological view of daily activities
- Detailed visit information
- Date navigation controls

#### ⚙️ **Settings**
- **Location Tracking**: Configure update intervals and accuracy
- **Place Detection**: Adjust clustering parameters
- **Notifications**: Customize arrival/departure alerts
- **Data Management**: Export data and manage retention

#### 📊 **Insights**
- Statistical analysis of movement patterns
- Place categorization insights
- Time-based analytics

## 🛠️ Development

### Building

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test

# Run on device/emulator
./gradlew installDebug
```

### Dependencies

**Core Android**
- Jetpack Compose (UI framework)
- Hilt (Dependency injection)
- Room + SQLCipher (Encrypted database)
- WorkManager (Background tasks)

**Location & Maps**
- Google Play Services Location
- OSMDroid (OpenStreetMap)
- Geofencing API

**Testing**
- JUnit 4
- MockK
- Turbine (Flow testing)

### Code Style

- **Kotlin**: Official Kotlin coding conventions
- **Architecture**: Clean Architecture + MVVM
- **DI**: Hilt dependency injection
- **Async**: Kotlin Coroutines and Flow
- **UI**: Jetpack Compose with Material Design 3

## 🔒 Privacy & Security

Voyager is designed with **privacy-first** principles:

- ✅ **Local Storage**: All data stored locally using SQLCipher encryption
- ✅ **No Cloud Sync**: No data transmitted to external servers
- ✅ **User Control**: Configurable data retention and export options
- ✅ **Transparent**: Open-source codebase for full transparency
- ✅ **Minimal Permissions**: Only essential permissions requested

## 🆕 Recent Updates (October 2024)

### Critical Bug Fixes ✅

#### **App Startup Crashes Resolved**
- ✅ **WorkManager Initialization**: Fixed app crashes on startup due to WorkManager conflicts
- ✅ **MainActivity Compose State**: Resolved `mutableStateOf` crashes in Activity lifecycle
- ✅ **Permission Handling**: Stable permission request flow implementation

#### **Analytics Pipeline Fixes**
- ✅ **Infinite Map Loading**: Fixed Flow collection bugs causing endless loading states
- ✅ **Zero Analytics Data**: Resolved issue where dashboard showed 0 values despite 814 locations
- ✅ **Place Detection**: Fixed pipeline that prevented location data from being processed into places

#### **Location Tracking Improvements**
- ✅ **GPS Spam Prevention**: Implemented smart filtering to prevent location spam when stationary
- ✅ **Stationary Detection**: Adaptive GPS behavior that reduces frequency when device isn't moving
- ✅ **Movement Validation**: Intelligent algorithms to distinguish real movement from GPS drift

### Major Enhancements 🚀

#### **User-Configurable Settings System**
- 🆕 **Location Quality Settings**: Configurable GPS accuracy thresholds, speed filters, and timing
- 🆕 **Place Detection Automation**: User-controlled detection frequency and battery requirements
- 🆕 **Analytics Configuration**: Customizable activity time ranges and processing batch sizes

#### **Enhanced Place Detection**
- 🆕 **Manual Detection Trigger**: "Detect Places Now" button for immediate processing
- 🆕 **Comprehensive Logging**: Detailed debug information throughout the detection pipeline
- 🆕 **Preference Integration**: All algorithms now respect user-configured parameters

#### **Smart Background Processing**
- 🆕 **Adaptive Worker Scheduling**: WorkManager respects user preferences for frequency and battery
- 🆕 **Immediate Processing**: App startup triggers place detection for existing location data
- 🆕 **Battery-Aware Operations**: User-configurable battery requirements for background tasks

### Technical Improvements

#### **Robust Error Handling**
```kotlin
// Analytics pipeline now includes comprehensive error handling
suspend fun generateDayAnalytics(date: LocalDate): DayAnalytics {
    return try {
        // Individual error handling for each data source
        val locations = try {
            locationRepository.getLocationsSince(startTime)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get locations", e)
            emptyList() // Graceful fallback
        }
        // Process and return analytics
    } catch (e: Exception) {
        // Return safe defaults instead of crashing
        DayAnalytics(/* empty analytics with safe defaults */)
    }
}
```

#### **Smart Location Filtering**
```kotlin
private fun shouldSaveLocation(newLocation: AndroidLocation): Boolean {
    // 1. User-configurable accuracy filtering
    if (newLocation.accuracy > preferences.maxGpsAccuracyMeters) return false
    
    // 2. Adaptive movement thresholds
    val minMovement = if (isInStationaryMode) 25.0 else preferences.getEffectiveMinDistance()
    
    // 3. User-configurable speed validation
    if (speedKmh > preferences.maxSpeedKmh) return false
    
    // 4. Intelligent time-based filtering
    val minTimeBetween = preferences.minTimeBetweenUpdatesSeconds * 1000L
    
    // Decision logic prevents GPS drift while capturing real movement
}
```

#### **Preference-Driven Architecture**
```kotlin
data class UserPreferences(
    // Location Quality Filtering (NEW)
    val maxGpsAccuracyMeters: Float = 100f, // 50-500m range
    val maxSpeedKmh: Double = 200.0, // 100-300 km/h range
    val minTimeBetweenUpdatesSeconds: Int = 10, // 5-60s range
    
    // Place Detection Automation (NEW)
    val placeDetectionFrequencyHours: Int = 6, // 1-24h range
    val autoDetectTriggerCount: Int = 50, // 10-500 locations
    val batteryRequirement: BatteryRequirement = BatteryRequirement.NOT_LOW,
    
    // Analytics Configuration (NEW)
    val activityTimeRangeStart: Int = 6, // 0-23 hours
    val activityTimeRangeEnd: Int = 23, // 0-23 hours
    val dataProcessingBatchSize: Int = 1000, // 100-5000 items
    
    // Existing settings with validation...
)
```

## 📋 Known Issues & Limitations

### Current Limitations
- **Battery Usage**: Continuous GPS tracking impacts battery life (mitigated by smart stationary detection)
- **Indoor Accuracy**: GPS accuracy reduced in buildings/underground
- **Place Detection**: Requires sufficient location data for accurate detection (now user-configurable)
- **Settings UI**: Advanced settings UI for new preferences is in development

### Recently Fixed Issues ✅
- ✅ **App Startup Crashes**: WorkManager initialization conflicts resolved
- ✅ **Infinite Loading States**: Flow collection bugs in analytics pipeline fixed
- ✅ **Location Spam**: Smart filtering prevents GPS drift when stationary
- ✅ **Zero Analytics**: Place detection pipeline now processes existing location data
- ✅ **Permission Crashes**: Compose state management issues resolved

### Migration Notes
- **Breaking Change**: Place detection now requires explicit enabling in preferences
- **Settings Reset**: Some users may need to reconfigure preferences after update
- **Data Reprocessing**: Use "Detect Places Now" to reprocess existing location data with new algorithms

## 🧪 Testing

### Run Tests
```bash
# Unit tests
./gradlew testDebugUnitTest

# Instrumented tests
./gradlew connectedDebugAndroidTest

# All tests
./gradlew check
```

### Test Coverage
- **Unit Tests**: Domain layer logic, ViewModels
- **Integration Tests**: Database operations, repositories
- **UI Tests**: Critical user flows (planned)

## 🚀 Deployment

### Release Build
```bash
# Generate signed APK
./gradlew assembleRelease

# Generate AAB (recommended for Play Store)
./gradlew bundleRelease
```

### Build Variants
- **Debug**: Development builds with logging
- **Release**: Optimized production builds

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🤝 Contributing

Contributions are welcome! Please read our [Contributing Guide](CONTRIBUTING.md) for details on our code of conduct and the process for submitting pull requests.

### Development Setup
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Submit a pull request

## 📞 Support

For questions, issues, or feature requests:

- **Issues**: [GitHub Issues](https://github.com/your-username/voyager/issues)
- **Discussions**: [GitHub Discussions](https://github.com/your-username/voyager/discussions)
- **Documentation**: Check the `/docs` folder for detailed documentation

## 🙏 Acknowledgments

- **OpenStreetMap**: For providing free, open-source mapping data
- **OSMDroid**: For the excellent Android mapping library
- **Android Team**: For the robust development platform and libraries
- **Kotlin Team**: For the amazing programming language

---

**Built with ❤️ for privacy-conscious users who want to understand their movement patterns without compromising their data.**