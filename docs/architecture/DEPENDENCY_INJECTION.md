# Dependency Injection with Hilt

This guide explains why Voyager uses Hilt for dependency injection and how it's implemented throughout the application.

## 🤔 Why Hilt? (vs Koin, Manual DI, Dagger)

### Comparison Matrix

| Feature | Hilt | Koin | Manual DI | Dagger 2 |
|---------|------|------|-----------|----------|
| **Compile-time Safety** | ✅ Yes | ❌ Runtime | ❌ None | ✅ Yes |
| **Android Integration** | ✅ Excellent | ⚠️ Good | ❌ Manual | ⚠️ Manual |
| **Learning Curve** | ⚠️ Medium | ✅ Easy | ✅ Easy | ❌ Steep |
| **Performance** | ✅ Excellent | ⚠️ Good | ✅ Best | ✅ Excellent |
| **Code Generation** | ✅ Yes | ❌ No | ❌ No | ✅ Yes |
| **Jetpack Support** | ✅ Built-in | ⚠️ Extension | ❌ Manual | ⚠️ Manual |
| **Worker Injection** | ✅ HiltWorkerFactory | ⚠️ Manual | ❌ Complex | ⚠️ Complex |

### Why Hilt for Voyager?

#### 1. **Compile-time Safety**
```kotlin
// ✅ Hilt catches this at compile time
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val nonExistentService: NonExistentService // Compilation error
)

// ❌ Koin discovers this at runtime
val dashboardViewModel: DashboardViewModel by inject() // Runtime crash
```

#### 2. **Android Lifecycle Integration**
```kotlin
// ✅ Hilt automatically scopes to Activity/Fragment lifecycle
@HiltViewModel
class MapViewModel @Inject constructor(
    private val locationRepository: LocationRepository
) : ViewModel() // Survives configuration changes

// ❌ Manual handling required with other frameworks
class MapViewModel(
    private val locationRepository: LocationRepository
) : ViewModel() {
    // Need manual scope management
}
```

#### 3. **WorkManager Integration**
```kotlin
// ✅ Hilt provides seamless Worker injection
@HiltWorker
class PlaceDetectionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val placeDetectionUseCases: PlaceDetectionUseCases // Automatically injected
)

// ❌ Complex factory setup required with other frameworks
```

#### 4. **Performance**
- **Zero reflection**: All dependencies resolved at compile time
- **Optimized code generation**: Minimal runtime overhead
- **Memory efficient**: No runtime container overhead

## 🏗️ Hilt Architecture in Voyager

### Component Hierarchy

```
Application
    ↓
SingletonComponent (App-level singletons)
    ↓
ActivityRetainedComponent (Survives config changes)
    ↓
ViewModelComponent (ViewModel-scoped)
    ↓
ActivityComponent (Activity-scoped)
    ↓
FragmentComponent (Fragment-scoped)
```

### Module Organization

```
📁 di/
├── 📄 DatabaseModule.kt        # Database and DAOs
├── 📄 LocationModule.kt        # Location services
├── 📄 RepositoryModule.kt      # Repository implementations
├── 📄 UseCasesModule.kt        # Business logic use cases
├── 📄 StateModule.kt           # State management
├── 📄 OrchestratorModule.kt    # Data orchestration
├── 📄 UtilsModule.kt           # Utility classes
├── 📄 ValidationModule.kt      # Validation services
└── 📄 StateEntryPoint.kt       # Manual injection points
```

## 📋 Module Implementations

### 1. Application Setup

```kotlin
// VoyagerApplication.kt
@HiltAndroidApp
class VoyagerApplication : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory) // Critical for Worker injection
            .build()
}
```

### 2. Database Module

```kotlin
// di/DatabaseModule.kt
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideVoyagerDatabase(@ApplicationContext context: Context): VoyagerDatabase {
        val passphrase = SecurityUtils.getDatabasePassphrase(context)
        return VoyagerDatabase.create(context, passphrase)
    }
    
    @Provides
    fun provideLocationDao(database: VoyagerDatabase): LocationDao = database.locationDao()
    
    @Provides
    fun providePlaceDao(database: VoyagerDatabase): PlaceDao = database.placeDao()
    
    @Provides
    fun provideVisitDao(database: VoyagerDatabase): VisitDao = database.visitDao()
    
    @Provides
    fun provideGeofenceDao(database: VoyagerDatabase): GeofenceDao = database.geofenceDao()
    
    @Provides
    fun provideCurrentStateDao(database: VoyagerDatabase): CurrentStateDao = database.currentStateDao()
}
```

**Why this pattern?**
- **Single responsibility**: Each module focuses on one domain
- **Easy testing**: Can provide test databases easily
- **Lifecycle management**: Database created once at app startup

### 3. Repository Module

```kotlin
// di/RepositoryModule.kt
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    abstract fun bindLocationRepository(
        locationRepositoryImpl: LocationRepositoryImpl
    ): LocationRepository
    
    @Binds
    abstract fun bindPlaceRepository(
        placeRepositoryImpl: PlaceRepositoryImpl
    ): PlaceRepository
    
    @Binds
    abstract fun bindVisitRepository(
        visitRepositoryImpl: VisitRepositoryImpl
    ): VisitRepository
    
    @Binds
    abstract fun bindGeofenceRepository(
        geofenceRepositoryImpl: GeofenceRepositoryImpl
    ): GeofenceRepository
    
    @Binds
    abstract fun bindCurrentStateRepository(
        currentStateRepositoryImpl: CurrentStateRepositoryImpl
    ): CurrentStateRepository
    
    @Binds
    abstract fun bindPreferencesRepository(
        preferencesRepositoryImpl: PreferencesRepositoryImpl
    ): PreferencesRepository
}
```

**Why `@Binds` vs `@Provides`?**
- **@Binds**: More efficient, no method body, direct interface→implementation mapping
- **@Provides**: When you need custom logic in creation

### 4. Location Services Module

```kotlin
// di/LocationModule.kt
@Module
@InstallIn(SingletonComponent::class)
object LocationModule {
    
    @Provides
    @Singleton
    fun provideFusedLocationProviderClient(
        @ApplicationContext context: Context
    ): FusedLocationProviderClient {
        return LocationServices.getFusedLocationProviderClient(context)
    }
    
    @Provides
    @Singleton
    fun provideGeofencingClient(
        @ApplicationContext context: Context
    ): GeofencingClient {
        return LocationServices.getGeofencingClient(context)
    }
    
    @Provides
    @Singleton
    fun provideLocationServiceManager(
        @ApplicationContext context: Context,
        preferencesRepository: PreferencesRepository
    ): LocationServiceManager {
        return LocationServiceManager(context, preferencesRepository)
    }
}
```

### 5. Use Cases Module

```kotlin
// di/UseCasesModule.kt
@Module
@InstallIn(SingletonComponent::class)
object UseCasesModule {
    
    @Provides
    @Singleton
    fun providePlaceDetectionUseCases(
        locationRepository: LocationRepository,
        placeRepository: PlaceRepository,
        visitRepository: VisitRepository,
        preferencesRepository: PreferencesRepository,
        errorHandler: ErrorHandler,
        validationService: ValidationService
    ): PlaceDetectionUseCases {
        return PlaceDetectionUseCases(
            locationRepository,
            placeRepository,
            visitRepository,
            preferencesRepository,
            errorHandler,
            validationService
        )
    }
    
    @Provides
    @Singleton
    fun provideLocationUseCases(
        locationRepository: LocationRepository,
        preferencesRepository: PreferencesRepository,
        locationServiceManager: LocationServiceManager
    ): LocationUseCases {
        return LocationUseCases(
            locationRepository,
            preferencesRepository,
            locationServiceManager
        )
    }
    
    @Provides
    @Singleton
    fun providePlaceUseCases(
        placeRepository: PlaceRepository,
        geofenceRepository: GeofenceRepository,
        visitRepository: VisitRepository
    ): PlaceUseCases {
        return PlaceUseCases(
            placeRepository,
            geofenceRepository,
            visitRepository
        )
    }
}
```

## 🎯 Scoping Strategies

### Singleton Components

```kotlin
// Services that live for the entire app lifetime
@Singleton
class SmartDataProcessor @Inject constructor(
    private val locationRepository: LocationRepository,
    private val placeRepository: PlaceRepository,
    private val currentStateRepository: CurrentStateRepository
) {
    // Heavy initialization, keep alive
    private val processingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

@Singleton
class AppStateManager @Inject constructor(
    private val currentStateRepository: CurrentStateRepository
) {
    // Global state, single instance needed
}
```

### ViewModelScoped Components

```kotlin
// Automatically scoped to ViewModel lifecycle
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val locationUseCases: LocationUseCases,
    private val analyticsUseCases: AnalyticsUseCases,
    private val workManagerHelper: WorkManagerHelper
) : ViewModel() {
    // Dies when ViewModel is cleared
}
```

### ActivityScoped Components

```kotlin
// For services that need Activity context
@ActivityScoped
class PermissionManager @Inject constructor(
    private val activity: Activity
) {
    // Lives for Activity lifetime
    fun requestLocationPermissions() {
        // Use activity reference safely
    }
}
```

## ⚙️ Advanced Hilt Features

### 1. Assisted Injection for Workers

```kotlin
// For classes that need both injected and runtime parameters
@HiltWorker
class PlaceDetectionWorker @AssistedInject constructor(
    @Assisted context: Context,              // Runtime parameter
    @Assisted workerParams: WorkerParameters, // Runtime parameter
    private val placeDetectionUseCases: PlaceDetectionUseCases, // Injected
    private val placeUseCases: PlaceUseCases  // Injected
) : CoroutineWorker(context, workerParams) {
    
    override suspend fun doWork(): Result {
        val newPlaces = placeDetectionUseCases.detectNewPlaces()
        return Result.success()
    }
}
```

### 2. Entry Points for Manual Injection

```kotlin
// di/StateEntryPoint.kt
@EntryPoint
@InstallIn(SingletonComponent::class)
interface StateEntryPoint {
    fun appStateManager(): AppStateManager
}

// Usage in Service where @Inject doesn't work
class LocationTrackingService : Service() {
    
    private lateinit var appStateManager: AppStateManager
    
    override fun onCreate() {
        super.onCreate()
        
        // Manual injection when @Inject isn't available
        appStateManager = EntryPointAccessors
            .fromApplication(applicationContext, StateEntryPoint::class.java)
            .appStateManager()
    }
}
```

### 3. Qualifiers for Multiple Implementations

```kotlin
// When you need different types of the same interface
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LocalDataSource

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RemoteDataSource

@Module
@InstallIn(SingletonComponent::class)
object DataSourceModule {
    
    @Provides
    @LocalDataSource
    fun provideLocalDataSource(dao: LocationDao): LocationDataSource {
        return LocalLocationDataSource(dao)
    }
    
    @Provides
    @RemoteDataSource
    fun provideRemoteDataSource(api: LocationApi): LocationDataSource {
        return RemoteLocationDataSource(api)
    }
}

// Usage
class LocationRepository @Inject constructor(
    @LocalDataSource private val localDataSource: LocationDataSource,
    @RemoteDataSource private val remoteDataSource: LocationDataSource
)
```

## 🧪 Testing with Hilt

### 1. Test Application

```kotlin
// Create test application
@HiltAndroidApp
class VoyagerTestApplication : Application()
```

### 2. Test Module Replacement

```kotlin
// Replace production modules in tests
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [RepositoryModule::class]
)
abstract class TestRepositoryModule {
    
    @Binds
    abstract fun bindLocationRepository(
        fakeLocationRepository: FakeLocationRepository
    ): LocationRepository
}

// Test implementation
@Singleton
class FakeLocationRepository @Inject constructor() : LocationRepository {
    private val locations = mutableListOf<Location>()
    
    override suspend fun insertLocation(location: Location): Long {
        locations.add(location)
        return locations.size.toLong()
    }
    
    override fun getRecentLocations(limit: Int): Flow<List<Location>> {
        return flowOf(locations.takeLast(limit))
    }
}
```

### 3. ViewModel Testing

```kotlin
@HiltAndroidTest
class DashboardViewModelTest {
    
    @get:Rule
    var hiltRule = HiltAndroidRule(this)
    
    @Inject
    lateinit var locationUseCases: LocationUseCases
    
    @Before
    fun setup() {
        hiltRule.inject()
    }
    
    @Test
    fun `dashboard loads correctly`() = runTest {
        val viewModel = DashboardViewModel(locationUseCases, analyticsUseCases)
        // Test ViewModel behavior
    }
}
```

## 🚀 Performance Optimizations

### 1. Lazy Injection

```kotlin
// Delay expensive object creation
class SmartDataProcessor @Inject constructor(
    private val locationRepository: LocationRepository,
    private val heavyService: Lazy<HeavyProcessingService> // Created only when needed
) {
    
    suspend fun processLocation(location: Location) {
        locationRepository.insertLocation(location)
        
        // Heavy service created only if complex processing needed
        if (needsComplexProcessing) {
            heavyService.get().process(location)
        }
    }
}
```

### 2. Provider Injection

```kotlin
// When you need multiple instances
class LocationProcessor @Inject constructor(
    private val locationValidatorProvider: Provider<LocationValidator>
) {
    
    fun processLocations(locations: List<Location>) {
        locations.chunked(100).forEach { batch ->
            // New validator instance for each batch
            val validator = locationValidatorProvider.get()
            batch.forEach { validator.validate(it) }
        }
    }
}
```

## 🔧 Common Patterns

### 1. Repository with Multiple Data Sources

```kotlin
@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val localDataSource: LocationDao,
    private val remoteDataSource: LocationApi,
    private val preferencesRepository: PreferencesRepository
) : LocationRepository {
    
    override fun getRecentLocations(limit: Int): Flow<List<Location>> {
        return if (shouldSyncWithRemote()) {
            // Merge local and remote data
            combine(
                localDataSource.getRecentLocations(limit),
                remoteDataSource.getRecentLocations(limit)
            ) { local, remote ->
                mergeLocations(local, remote)
            }
        } else {
            localDataSource.getRecentLocations(limit)
        }
    }
}
```

### 2. Conditional Dependencies

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object ConditionalModule {
    
    @Provides
    @Singleton
    fun provideAnalyticsService(
        @ApplicationContext context: Context
    ): AnalyticsService {
        return if (BuildConfig.DEBUG) {
            DebugAnalyticsService()
        } else {
            ProductionAnalyticsService(context)
        }
    }
}
```

## 🐛 Common Pitfalls & Solutions

### 1. Circular Dependencies

```kotlin
// ❌ Problem: Circular dependency
class ServiceA @Inject constructor(private val serviceB: ServiceB)
class ServiceB @Inject constructor(private val serviceA: ServiceA)

// ✅ Solution: Introduce mediator or break cycle
class ServiceA @Inject constructor(private val mediator: ServiceMediator)
class ServiceB @Inject constructor(private val mediator: ServiceMediator)
```

### 2. Missing @Singleton Annotation

```kotlin
// ❌ Problem: New instance every injection
@Provides
fun provideExpensiveService(): ExpensiveService {
    return ExpensiveService() // Created every time!
}

// ✅ Solution: Add @Singleton
@Provides
@Singleton
fun provideExpensiveService(): ExpensiveService {
    return ExpensiveService() // Created once
}
```

### 3. Context Leaks

```kotlin
// ❌ Problem: Activity context in singleton
@Provides
@Singleton
fun provideServiceWithContext(activity: Activity): SomeService {
    return SomeService(activity) // Memory leak!
}

// ✅ Solution: Use @ApplicationContext
@Provides
@Singleton
fun provideServiceWithContext(@ApplicationContext context: Context): SomeService {
    return SomeService(context) // Safe
}
```

## 📊 Hilt Benefits Summary

| Benefit | Impact on Voyager |
|---------|-------------------|
| **Compile-time Safety** | Catches DI errors during build, not runtime |
| **Performance** | Zero reflection, optimized code generation |
| **Android Integration** | Seamless ViewModel, Worker, Service injection |
| **Maintainability** | Clear module organization, easy to refactor |
| **Testing** | Easy mocking and test double injection |
| **Debugging** | Clear error messages, generated code inspection |

---

*Next: [Place Detection Algorithm](../algorithms/PLACE_DETECTION.md)*