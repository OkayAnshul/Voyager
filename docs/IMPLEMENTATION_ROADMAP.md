# VOYAGER - IMPLEMENTATION ROADMAP

**Last Updated**: 2025-11-12
**Target**: Production-Ready Application
**Estimated Timeline**: 12-18 hours development time

---

## OVERVIEW

This roadmap outlines the step-by-step implementation plan to transform Voyager from a working prototype to a production-ready, daily-use location analytics application. Priority is given to implementing **free geocoding** to provide real place names, followed by critical fixes and user experience enhancements.

---

## PHASE 1: FREE GEOCODING IMPLEMENTATION (PRIORITY)
**Goal**: Enable real place names and addresses
**Timeline**: 4-6 hours
**Status**: 🔴 Not Started

### Why This Phase First
- **User Impact**: Highest - solves the main complaint (generic place names)
- **User Value**: Essential for practical daily use
- **Complexity**: Medium - no API keys needed for free options
- **Dependencies**: None - can implement immediately

### 1.1 Create Geocoding Service Interface
**File to Create**: `app/src/main/java/com/cosmiclaboratory/voyager/data/api/GeocodingService.kt`

```kotlin
interface GeocodingService {
    /**
     * Reverse geocode coordinates to address
     * @return Address object or null if unavailable
     */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): AddressResult?

    /**
     * Get place details from coordinates
     * @return PlaceDetails with name, type, etc. or null
     */
    suspend fun getPlaceDetails(latitude: Double, longitude: Double): PlaceDetails?

    /**
     * Check if service is available
     */
    suspend fun isAvailable(): Boolean
}

data class AddressResult(
    val formattedAddress: String,
    val streetName: String?,
    val locality: String?,        // City/town
    val subLocality: String?,     // Neighborhood/area
    val postalCode: String?,
    val countryCode: String?
)

data class PlaceDetails(
    val name: String?,            // Business name (e.g., "Starbucks")
    val type: String?,            // Place type (e.g., "cafe", "gym")
    val formattedAddress: String?
)
```

**Purpose**: Abstraction layer supporting multiple geocoding providers

---

### 1.2 Implement Android Geocoder Service (FREE)
**File to Create**: `app/src/main/java/com/cosmiclaboratory/voyager/data/api/AndroidGeocoderService.kt`

**Advantages**:
- ✅ FREE - no API key required
- ✅ Built-in Android API
- ✅ No rate limiting
- ✅ Works offline (cached results)

**Limitations**:
- ⚠️ Basic addresses only (no business names)
- ⚠️ Quality varies by region
- ⚠️ May return null in some areas

**Implementation**:
```kotlin
@Singleton
class AndroidGeocoderService @Inject constructor(
    @ApplicationContext private val context: Context
) : GeocodingService {

    private val geocoder = Geocoder(context, Locale.getDefault())

    override suspend fun reverseGeocode(
        latitude: Double,
        longitude: Double
    ): AddressResult? = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) return@withContext null

            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            val address = addresses?.firstOrNull() ?: return@withContext null

            AddressResult(
                formattedAddress = address.getAddressLine(0) ?: "",
                streetName = address.thoroughfare,
                locality = address.locality,
                subLocality = address.subLocality,
                postalCode = address.postalCode,
                countryCode = address.countryCode
            )
        } catch (e: Exception) {
            Log.e("AndroidGeocoder", "Reverse geocode failed", e)
            null
        }
    }

    override suspend fun getPlaceDetails(
        latitude: Double,
        longitude: Double
    ): PlaceDetails? {
        // Android Geocoder doesn't provide business names
        return null
    }

    override suspend fun isAvailable(): Boolean {
        return Geocoder.isPresent()
    }
}
```

**Testing Strategy**:
- Test with various coordinates (urban, rural, international)
- Handle null responses gracefully
- Validate address formatting

---

### 1.3 Implement OpenStreetMap Nominatim Service (FREE)
**File to Create**: `app/src/main/java/com/cosmiclaboratory/voyager/data/api/NominatimGeocodingService.kt`

**Advantages**:
- ✅ FREE - no API key required
- ✅ Better quality than Android Geocoder
- ✅ Sometimes includes business names
- ✅ Open-source, privacy-friendly

**Limitations**:
- ⚠️ Rate limited (1 request per second)
- ⚠️ Requires internet connection
- ⚠️ Usage policy requires User-Agent header

**API Endpoint**: `https://nominatim.openstreetmap.org/reverse`

**Implementation**:
```kotlin
@Singleton
class NominatimGeocodingService @Inject constructor(
    private val okHttpClient: OkHttpClient
) : GeocodingService {

    private val baseUrl = "https://nominatim.openstreetmap.org"
    private val rateLimiter = RateLimiter(1000) // 1 request per second

    override suspend fun reverseGeocode(
        latitude: Double,
        longitude: Double
    ): AddressResult? = withContext(Dispatchers.IO) {
        try {
            rateLimiter.acquire()

            val url = "$baseUrl/reverse?format=json&lat=$latitude&lon=$longitude"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Voyager-Android/1.0 (Location Analytics App)")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val json = JSONObject(response.body?.string() ?: "")
            val address = json.optJSONObject("address") ?: return@withContext null

            AddressResult(
                formattedAddress = json.optString("display_name", ""),
                streetName = address.optString("road", null),
                locality = address.optString("city", address.optString("town", null)),
                subLocality = address.optString("suburb", address.optString("neighbourhood", null)),
                postalCode = address.optString("postcode", null),
                countryCode = address.optString("country_code", null)
            )
        } catch (e: Exception) {
            Log.e("NominatimGeocoder", "Reverse geocode failed", e)
            null
        }
    }

    override suspend fun getPlaceDetails(
        latitude: Double,
        longitude: Double
    ): PlaceDetails? = withContext(Dispatchers.IO) {
        try {
            rateLimiter.acquire()

            val url = "$baseUrl/reverse?format=json&lat=$latitude&lon=$longitude&zoom=18"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Voyager-Android/1.0")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val json = JSONObject(response.body?.string() ?: "")

            PlaceDetails(
                name = json.optString("name", null),
                type = json.optString("type", null),
                formattedAddress = json.optString("display_name", null)
            )
        } catch (e: Exception) {
            Log.e("NominatimGeocoder", "Place details failed", e)
            null
        }
    }

    override suspend fun isAvailable(): Boolean {
        // Check internet connectivity
        return true
    }
}

/**
 * Simple rate limiter to comply with Nominatim usage policy
 */
class RateLimiter(private val minIntervalMs: Long) {
    private var lastRequestTime = 0L

    fun acquire() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastRequestTime
        if (elapsed < minIntervalMs) {
            Thread.sleep(minIntervalMs - elapsed)
        }
        lastRequestTime = System.currentTimeMillis()
    }
}
```

**Usage Policy Compliance**:
- ✅ User-Agent header identifies app
- ✅ Rate limiting (1 req/sec maximum)
- ✅ Caching to minimize requests

---

### 1.4 Create Geocoding Repository with Caching
**File to Create**: `app/src/main/java/com/cosmiclaboratory/voyager/data/repository/GeocodingRepository.kt`

**Purpose**: Coordinate multiple geocoding services with intelligent caching

**Strategy**:
1. Check cache first (avoid redundant API calls)
2. Try Android Geocoder (fastest, free)
3. Fallback to Nominatim if Geocoder returns null
4. Cache successful results for 30 days
5. Return generic name if all fail

**Implementation**:
```kotlin
interface GeocodingRepository {
    suspend fun getAddressForCoordinates(latitude: Double, longitude: Double): AddressResult?
    suspend fun getPlaceDetailsForCoordinates(latitude: Double, longitude: Double): PlaceDetails?
    suspend fun clearCache()
}

@Singleton
class GeocodingRepositoryImpl @Inject constructor(
    private val androidGeocoder: AndroidGeocoderService,
    private val nominatimGeocoder: NominatimGeocodingService,
    private val geocodingCacheDao: GeocodingCacheDao
) : GeocodingRepository {

    private val cacheDurationDays = 30

    override suspend fun getAddressForCoordinates(
        latitude: Double,
        longitude: Double
    ): AddressResult? {
        // Round coordinates to ~100m precision for cache key
        val lat = roundToDecimalPlaces(latitude, 3)
        val lng = roundToDecimalPlaces(longitude, 3)

        // 1. Check cache first
        val cached = geocodingCacheDao.getAddress(lat, lng)
        if (cached != null && !cached.isExpired(cacheDurationDays)) {
            return cached.toAddressResult()
        }

        // 2. Try Android Geocoder (fastest, free, offline capable)
        androidGeocoder.getAddressForCoordinates(latitude, longitude)?.let { result ->
            geocodingCacheDao.insertAddress(
                GeocodingCacheEntity.fromAddressResult(lat, lng, result)
            )
            return result
        }

        // 3. Fallback to Nominatim (better quality, requires internet)
        nominatimGeocoder.reverseGeocode(latitude, longitude)?.let { result ->
            geocodingCacheDao.insertAddress(
                GeocodingCacheEntity.fromAddressResult(lat, lng, result)
            )
            return result
        }

        // 4. All geocoding failed
        return null
    }

    override suspend fun getPlaceDetailsForCoordinates(
        latitude: Double,
        longitude: Double
    ): PlaceDetails? {
        // Similar caching strategy for place details
        // Nominatim sometimes provides business names
        return nominatimGeocoder.getPlaceDetails(latitude, longitude)
    }

    override suspend fun clearCache() {
        geocodingCacheDao.clearOldCache(cacheDurationDays)
    }

    private fun roundToDecimalPlaces(value: Double, places: Int): Double {
        val multiplier = Math.pow(10.0, places.toDouble())
        return Math.round(value * multiplier) / multiplier
    }
}
```

**Cache Database Entity**:
**File to Create**: `app/src/main/java/com/cosmiclaboratory/voyager/data/database/entity/GeocodingCacheEntity.kt`

```kotlin
@Entity(
    tableName = "geocoding_cache",
    indices = [Index(value = ["latitude", "longitude"], unique = true)]
)
data class GeocodingCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val formattedAddress: String,
    val streetName: String?,
    val locality: String?,
    val subLocality: String?,
    val postalCode: String?,
    val countryCode: String?,
    val cachedAt: Instant
) {
    fun isExpired(durationDays: Int): Boolean {
        return cachedAt.plusDays(durationDays.toLong()) < Instant.now()
    }

    fun toAddressResult(): AddressResult {
        return AddressResult(
            formattedAddress = formattedAddress,
            streetName = streetName,
            locality = locality,
            subLocality = subLocality,
            postalCode = postalCode,
            countryCode = countryCode
        )
    }

    companion object {
        fun fromAddressResult(
            lat: Double,
            lng: Double,
            address: AddressResult
        ): GeocodingCacheEntity {
            return GeocodingCacheEntity(
                latitude = lat,
                longitude = lng,
                formattedAddress = address.formattedAddress,
                streetName = address.streetName,
                locality = address.locality,
                subLocality = address.subLocality,
                postalCode = address.postalCode,
                countryCode = address.countryCode,
                cachedAt = Instant.now()
            )
        }
    }
}
```

**DAO**:
**File to Create**: `app/src/main/java/com/cosmiclaboratory/voyager/data/database/dao/GeocodingCacheDao.kt`

```kotlin
@Dao
interface GeocodingCacheDao {
    @Query("SELECT * FROM geocoding_cache WHERE latitude = :lat AND longitude = :lng LIMIT 1")
    suspend fun getAddress(lat: Double, lng: Double): GeocodingCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAddress(cache: GeocodingCacheEntity)

    @Query("DELETE FROM geocoding_cache WHERE cachedAt < :expiryDate")
    suspend fun clearOldCache(expiryDate: Instant)

    fun clearOldCache(durationDays: Int) {
        val expiryDate = Instant.now().minusDays(durationDays.toLong())
        clearOldCache(expiryDate)
    }
}
```

**Benefits of Caching**:
- ✅ Reduces API calls by ~90% (same places visited repeatedly)
- ✅ Faster response times (cache lookup <1ms vs API 100-500ms)
- ✅ Works offline for previously geocoded locations
- ✅ Respects Nominatim rate limits

---

### 1.5 Create Geocoding Use Cases
**File to Create**: `app/src/main/java/com/cosmiclaboratory/voyager/domain/usecase/GeocodingUseCases.kt`

```kotlin
@Singleton
class EnrichPlaceWithDetailsUseCase @Inject constructor(
    private val geocodingRepository: GeocodingRepository
) {
    /**
     * Enrich a place with real address and name
     * @param place Place to enrich
     * @return Updated place with address details
     */
    suspend operator fun invoke(place: Place): Place {
        val address = geocodingRepository.getAddressForCoordinates(
            place.latitude,
            place.longitude
        )

        val placeDetails = geocodingRepository.getPlaceDetailsForCoordinates(
            place.latitude,
            place.longitude
        )

        return place.copy(
            name = generateSmartName(place, address, placeDetails),
            address = address?.formattedAddress,
            locality = address?.locality,
            subLocality = address?.subLocality,
            postalCode = address?.postalCode,
            streetName = address?.streetName
        )
    }

    /**
     * Generate smart place name combining:
     * 1. Business name from geocoding (if available)
     * 2. ML category (Home, Work, Gym)
     * 3. Address components (street, neighborhood)
     */
    private fun generateSmartName(
        place: Place,
        address: AddressResult?,
        details: PlaceDetails?
    ): String {
        // Priority 1: Business name from Nominatim
        details?.name?.let { name ->
            if (name.isNotBlank() && name != "null") {
                return name
            }
        }

        // Priority 2: ML category with location context
        val categoryName = place.category.displayName
        val locationContext = address?.subLocality ?: address?.locality

        return if (locationContext != null) {
            when (place.category) {
                PlaceCategory.HOME, PlaceCategory.WORK -> categoryName
                else -> "$categoryName in $locationContext"
            }
        } else {
            categoryName
        }
    }
}
```

---

### 1.6 Update Place Model
**File to Modify**: `app/src/main/java/com/cosmiclaboratory/voyager/domain/model/Place.kt`

**Add Fields**:
```kotlin
data class Place(
    val id: Long = 0L,
    val name: String,
    val category: PlaceCategory,
    val latitude: Double,
    val longitude: Double,

    // NEW: Geocoding fields
    val address: String? = null,
    val streetName: String? = null,
    val locality: String? = null,       // City/town
    val subLocality: String? = null,    // Neighborhood/area
    val postalCode: String? = null,
    val countryCode: String? = null,
    val isUserRenamed: Boolean = false, // Track if user manually renamed

    val visitCount: Int = 0,
    val totalTimeSpent: Long = 0L,
    val radius: Double = 100.0,
    val createdAt: Instant = Instant.now(),
    val lastVisitedAt: Instant? = null
)
```

---

### 1.7 Update Place Entity
**File to Modify**: `app/src/main/java/com/cosmiclaboratory/voyager/data/database/entity/PlaceEntity.kt`

**Add Columns**:
```kotlin
@Entity(
    tableName = "places",
    indices = [
        Index(value = ["latitude", "longitude"]),
        Index(value = ["category"]),
        Index(value = ["lastVisitedAt"])
    ]
)
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,

    // NEW: Geocoding columns
    val address: String? = null,
    val streetName: String? = null,
    val locality: String? = null,
    val subLocality: String? = null,
    val postalCode: String? = null,
    val countryCode: String? = null,
    val isUserRenamed: Boolean = false,

    val visitCount: Int = 0,
    val totalTimeSpent: Long = 0,
    val radius: Double = 100.0,
    val createdAt: Instant,
    val lastVisitedAt: Instant? = null
)
```

**Database Migration**:
**File to Modify**: `app/src/main/java/com/cosmiclaboratory/voyager/data/database/VoyagerDatabase.kt`

```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add geocoding columns to places table
        database.execSQL("ALTER TABLE places ADD COLUMN address TEXT")
        database.execSQL("ALTER TABLE places ADD COLUMN streetName TEXT")
        database.execSQL("ALTER TABLE places ADD COLUMN locality TEXT")
        database.execSQL("ALTER TABLE places ADD COLUMN subLocality TEXT")
        database.execSQL("ALTER TABLE places ADD COLUMN postalCode TEXT")
        database.execSQL("ALTER TABLE places ADD COLUMN countryCode TEXT")
        database.execSQL("ALTER TABLE places ADD COLUMN isUserRenamed INTEGER NOT NULL DEFAULT 0")

        // Create geocoding cache table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS geocoding_cache (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                formattedAddress TEXT NOT NULL,
                streetName TEXT,
                locality TEXT,
                subLocality TEXT,
                postalCode TEXT,
                countryCode TEXT,
                cachedAt INTEGER NOT NULL
            )
        """)

        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_geocoding_cache_lat_lng ON geocoding_cache(latitude, longitude)")
    }
}

@Database(
    entities = [
        LocationEntity::class,
        PlaceEntity::class,
        VisitEntity::class,
        GeofenceEntity::class,
        CurrentStateEntity::class,
        GeocodingCacheEntity::class  // NEW
    ],
    version = 4,  // INCREMENT
    exportSchema = true
)
abstract class VoyagerDatabase : RoomDatabase() {
    // ... existing DAOs
    abstract fun geocodingCacheDao(): GeocodingCacheDao  // NEW
}
```

---

### 1.8 Integrate Geocoding into Place Detection
**File to Modify**: `app/src/main/java/com/cosmiclaboratory/voyager/domain/usecase/PlaceDetectionUseCases.kt`

**Update `detectNewPlaces` Method**:
```kotlin
@Inject
class PlaceDetectionUseCases @Inject constructor(
    // ... existing dependencies
    private val enrichPlaceWithDetailsUseCase: EnrichPlaceWithDetailsUseCase  // NEW
) {
    suspend fun detectNewPlaces(): List<Place> {
        // ... existing clustering logic

        val newPlaces = mutableListOf<Place>()

        for (cluster in clusters) {
            val center = calculateClusterCenter(cluster)
            val category = categorizePlace(cluster, preferences)

            // Create base place
            var place = Place(
                name = category.displayName,  // Temporary name
                category = category,
                latitude = center.latitude,
                longitude = center.longitude,
                radius = preferences.placeDetectionRadiusMeters,
                createdAt = Instant.now()
            )

            // NEW: Enrich with geocoding
            try {
                place = enrichPlaceWithDetailsUseCase(place)
                Log.d("PlaceDetection", "Enriched place: ${place.name} at ${place.address}")
            } catch (e: Exception) {
                Log.w("PlaceDetection", "Geocoding failed, using category name", e)
                // Keep generic name if geocoding fails
            }

            // Check for duplicates
            if (!isDuplicatePlace(place, existingPlaces)) {
                val savedPlace = placeRepository.insertPlace(place)
                newPlaces.add(savedPlace)
            }
        }

        return newPlaces
    }
}
```

---

### 1.9 Add User-Editable Place Names
**File to Create**: `app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/place/PlaceDetailsScreen.kt`

**Features**:
- View place details (name, address, category, stats)
- Edit place name (custom name overrides geocoding)
- Edit place category
- View visit history
- Delete place

**Implementation** (abbreviated):
```kotlin
@Composable
fun PlaceDetailsScreen(
    placeId: Long,
    viewModel: PlaceDetailsViewModel = hiltViewModel()
) {
    val place by viewModel.place.collectAsState()
    var showEditDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(place?.name ?: "Place Details") },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, "Edit")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Place name
            Text(
                text = place?.name ?: "",
                style = MaterialTheme.typography.headlineMedium
            )

            // Address
            place?.address?.let { address ->
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Category chip
            CategoryChip(category = place?.category)

            // Statistics
            StatisticsSection(place = place)

            // Visit history
            VisitHistoryList(placeId = placeId)
        }
    }

    if (showEditDialog) {
        EditPlaceDialog(
            place = place,
            onDismiss = { showEditDialog = false },
            onSave = { updatedPlace ->
                viewModel.updatePlace(updatedPlace.copy(isUserRenamed = true))
                showEditDialog = false
            }
        )
    }
}
```

**ViewModel**:
```kotlin
@HiltViewModel
class PlaceDetailsViewModel @Inject constructor(
    private val placeRepository: PlaceRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val placeId: Long = savedStateHandle["placeId"] ?: 0L

    val place: StateFlow<Place?> = placeRepository.getPlaceById(placeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun updatePlace(place: Place) {
        viewModelScope.launch {
            placeRepository.updatePlace(place)
        }
    }
}
```

---

### 1.10 Update DI Modules
**File to Modify**: `app/src/main/java/com/cosmiclaboratory/voyager/di/RepositoryModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    // ... existing bindings

    @Binds
    @Singleton
    abstract fun bindGeocodingRepository(
        impl: GeocodingRepositoryImpl
    ): GeocodingRepository
}
```

**File to Create**: `app/src/main/java/com/cosmiclaboratory/voyager/di/NetworkModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideAndroidGeocoderService(
        @ApplicationContext context: Context
    ): AndroidGeocoderService {
        return AndroidGeocoderService(context)
    }

    @Provides
    @Singleton
    fun provideNominatimGeocodingService(
        okHttpClient: OkHttpClient
    ): NominatimGeocodingService {
        return NominatimGeocodingService(okHttpClient)
    }
}
```

---

### 1.11 Update Permissions (if needed)
**File**: `app/src/main/AndroidManifest.xml`

**Verify Internet Permission** (already declared):
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

---

### Phase 1 Success Criteria ✅
- [ ] Places show real addresses (street, city, area)
- [ ] Places show postal codes where available
- [ ] Nominatim provides business names for some places
- [ ] Users can edit place names (overrides geocoding)
- [ ] Geocoding cache reduces API calls by 90%
- [ ] App works offline with cached geocoding results
- [ ] Nominatim rate limiting compliance (1 req/sec)
- [ ] Database migration successful (v3 → v4)

---

## PHASE 2: CRITICAL FIXES
**Goal**: Address security and performance issues
**Timeline**: 2-3 hours
**Status**: 🔴 Not Started

### 2.1 Migrate Encryption Key to Android Keystore
**Priority**: High (Security Issue)
**Current Issue**: Database passphrase stored in SharedPreferences (plain text)
**File to Modify**: `app/src/main/java/com/cosmiclaboratory/voyager/utils/SecurityUtils.kt`

**Implementation**:
```kotlin
object SecurityUtils {
    private const val KEYSTORE_ALIAS = "VoyagerDatabaseKey"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    /**
     * Get or create database passphrase using Android Keystore
     * Passphrase is encrypted by hardware-backed key
     */
    fun getDatabasePassphrase(context: Context): CharArray {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        // Check if key exists
        if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
            // Generate new key
            generateKey()
            // Generate and encrypt passphrase
            val passphrase = generateSecurePassphrase()
            saveEncryptedPassphrase(context, passphrase)
            return passphrase
        }

        // Retrieve and decrypt existing passphrase
        return loadEncryptedPassphrase(context)
    }

    private fun generateKey() {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false) // No biometric for database key
            .build()

        keyGenerator.init(keyGenParameterSpec)
        keyGenerator.generateKey()
    }

    private fun generateSecurePassphrase(): CharArray {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP).toCharArray()
    }

    private fun saveEncryptedPassphrase(context: Context, passphrase: CharArray) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val secretKey = keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val encryptedData = cipher.doFinal(passphrase.toString().toByteArray())
        val iv = cipher.iv

        // Save encrypted data and IV to SharedPreferences
        context.getSharedPreferences("security", Context.MODE_PRIVATE).edit {
            putString("encrypted_passphrase", Base64.encodeToString(encryptedData, Base64.NO_WRAP))
            putString("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
        }
    }

    private fun loadEncryptedPassphrase(context: Context): CharArray {
        val prefs = context.getSharedPreferences("security", Context.MODE_PRIVATE)
        val encryptedDataString = prefs.getString("encrypted_passphrase", null)
            ?: throw IllegalStateException("No encrypted passphrase found")
        val ivString = prefs.getString("iv", null)
            ?: throw IllegalStateException("No IV found")

        val encryptedData = Base64.decode(encryptedDataString, Base64.NO_WRAP)
        val iv = Base64.decode(ivString, Base64.NO_WRAP)

        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val secretKey = keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))

        val decryptedData = cipher.doFinal(encryptedData)
        return String(decryptedData).toCharArray()
    }
}
```

**Benefits**:
- ✅ Hardware-backed encryption (if available)
- ✅ Passphrase never stored in plain text
- ✅ Secure against ADB backup extraction
- ✅ Secure against root access (hardware-backed keys)

---

### 2.2 Add Database Indexes
**Priority**: High (Performance)
**Impact**: Query speed improvement 10-100x for large datasets

**File to Modify**: Update entity annotations

**LocationEntity**:
```kotlin
@Entity(
    tableName = "locations",
    indices = [
        Index(value = ["timestamp"]),              // NEW: For time-range queries
        Index(value = ["latitude", "longitude"]),  // NEW: For spatial queries
        Index(value = ["accuracy"])                // NEW: For quality filtering
    ]
)
data class LocationEntity(...)
```

**PlaceEntity** (already has indexes, verify):
```kotlin
@Entity(
    tableName = "places",
    indices = [
        Index(value = ["latitude", "longitude"]),
        Index(value = ["category"]),
        Index(value = ["lastVisitedAt"]),
        Index(value = ["locality"])  // NEW: For area-based queries
    ]
)
data class PlaceEntity(...)
```

**VisitEntity**:
```kotlin
@Entity(
    tableName = "visits",
    indices = [
        Index(value = ["placeId"]),           // Existing
        Index(value = ["entryTime"]),         // NEW: For timeline queries
        Index(value = ["exitTime"]),          // NEW: For completed visits
        Index(value = ["entryTime", "exitTime"])  // NEW: For date range queries
    ],
    foreignKeys = [...]
)
data class VisitEntity(...)
```

**Migration**:
```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add indexes
        database.execSQL("CREATE INDEX IF NOT EXISTS index_locations_timestamp ON locations(timestamp)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_locations_lat_lng ON locations(latitude, longitude)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_locations_accuracy ON locations(accuracy)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_places_locality ON places(locality)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_visits_entry_time ON visits(entryTime)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_visits_exit_time ON visits(exitTime)")
    }
}
```

**Expected Performance Improvements**:
- Timeline queries: 500ms → 50ms (10x faster)
- Nearby place lookup: 2000ms → 20ms (100x faster)
- Analytics calculations: 1000ms → 100ms (10x faster)

---

### 2.3 Add Pagination for Large Datasets
**Priority**: Medium (Stability)
**Library**: AndroidX Paging 3

**Add Dependency**:
```kotlin
// build.gradle.kts
implementation("androidx.paging:paging-runtime:3.2.1")
implementation("androidx.paging:paging-compose:3.2.1")
```

**Update LocationDao**:
```kotlin
@Dao
interface LocationDao {
    // Existing methods...

    @Query("SELECT * FROM locations ORDER BY timestamp DESC")
    fun getLocationsPaged(): PagingSource<Int, LocationEntity>

    @Query("SELECT * FROM locations WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getLocationsPaged(startTime: Instant, endTime: Instant): PagingSource<Int, LocationEntity>
}
```

**Usage in ViewModel**:
```kotlin
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val locationRepository: LocationRepository
) : ViewModel() {

    val locationsPaged: Flow<PagingData<Location>> = Pager(
        config = PagingConfig(
            pageSize = 100,
            prefetchDistance = 50,
            enablePlaceholders = true
        ),
        pagingSourceFactory = { locationRepository.getLocationsPaged() }
    ).flow.cachedIn(viewModelScope)
}
```

**UI with LazyColumn**:
```kotlin
@Composable
fun TimelineScreen(viewModel: TimelineViewModel) {
    val locations = viewModel.locationsPaged.collectAsLazyPagingItems()

    LazyColumn {
        items(locations.itemCount) { index ->
            locations[index]?.let { location ->
                LocationItem(location = location)
            }
        }
    }
}
```

---

### Phase 2 Success Criteria ✅
- [ ] Database passphrase encrypted with Android Keystore
- [ ] All critical queries have appropriate indexes
- [ ] Query performance improved by 10x minimum
- [ ] Pagination implemented for timeline and location history
- [ ] Memory usage reduced by 80% for large datasets
- [ ] No OutOfMemoryError with 10,000+ locations

---

## PHASE 3: USER EXPERIENCE ENHANCEMENTS
**Goal**: Complete settings UI and export functionality
**Timeline**: 2-3 hours
**Status**: 🔴 Not Started

### 3.1 Complete Advanced Settings UI
**File to Modify**: `app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/settings/SettingsScreen.kt`

**New Settings Sections**:

#### Tracking & Battery Section
```kotlin
@Composable
fun TrackingSettingsSection(
    preferences: UserPreferences,
    onUpdatePreferences: (UserPreferences) -> Unit
) {
    ExpandableSection(title = "Tracking & Battery") {
        // Battery Mode
        SettingDropdown(
            title = "Battery Mode",
            description = "Choose tracking intensity",
            options = BatteryMode.values().toList(),
            selected = preferences.batteryMode,
            onSelect = { mode ->
                onUpdatePreferences(preferences.copy(batteryMode = mode))
            }
        )

        // Update Interval
        SettingSlider(
            title = "Update Interval",
            description = "${preferences.minTimeBetweenUpdatesSeconds}s between updates",
            value = preferences.minTimeBetweenUpdatesSeconds.toFloat(),
            range = 5f..60f,
            onValueChange = { value ->
                onUpdatePreferences(preferences.copy(minTimeBetweenUpdatesSeconds = value.toInt()))
            }
        )

        // Accuracy Threshold
        SettingSlider(
            title = "GPS Accuracy Threshold",
            description = "${preferences.maxGpsAccuracyMeters}m maximum",
            value = preferences.maxGpsAccuracyMeters,
            range = 50f..500f,
            onValueChange = { value ->
                onUpdatePreferences(preferences.copy(maxGpsAccuracyMeters = value))
            }
        )
    }
}

enum class BatteryMode {
    ALWAYS_ON,      // Continuous tracking
    SMART,          // Adaptive based on movement
    GEOFENCE_ONLY,  // Only track at place boundaries
    CUSTOM          // User-defined settings
}
```

#### Place Detection Settings
```kotlin
@Composable
fun PlaceDetectionSettingsSection(
    preferences: UserPreferences,
    onUpdatePreferences: (UserPreferences) -> Unit
) {
    ExpandableSection(title = "Place Detection") {
        // Enable/Disable
        SettingSwitch(
            title = "Automatic Place Detection",
            description = "Discover new places automatically",
            checked = preferences.enablePlaceDetection,
            onCheckedChange = { enabled ->
                onUpdatePreferences(preferences.copy(enablePlaceDetection = enabled))
            }
        )

        // Trigger Count
        SettingSlider(
            title = "Detection Trigger",
            description = "After ${preferences.autoDetectTriggerCount} new locations",
            value = preferences.autoDetectTriggerCount.toFloat(),
            range = 10f..100f,
            onValueChange = { value ->
                onUpdatePreferences(preferences.copy(autoDetectTriggerCount = value.toInt()))
            }
        )

        // Frequency
        SettingSlider(
            title = "Detection Frequency",
            description = "Every ${preferences.placeDetectionFrequencyHours} hours",
            value = preferences.placeDetectionFrequencyHours.toFloat(),
            range = 1f..24f,
            onValueChange = { value ->
                onUpdatePreferences(preferences.copy(placeDetectionFrequencyHours = value.toInt()))
            }
        )

        // Clustering Distance
        SettingSlider(
            title = "Clustering Distance",
            description = "${preferences.clusteringDistanceMeters}m radius",
            value = preferences.clusteringDistanceMeters,
            range = 10f..200f,
            onValueChange = { value ->
                onUpdatePreferences(preferences.copy(clusteringDistanceMeters = value))
            }
        )
    }
}
```

#### Data Management Settings
```kotlin
@Composable
fun DataManagementSection(
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onClearData: () -> Unit
) {
    ExpandableSection(title = "Data Management") {
        // Export Options
        SettingButton(
            title = "Export as JSON",
            description = "Complete data export",
            icon = Icons.Default.Download,
            onClick = onExportJson
        )

        SettingButton(
            title = "Export as CSV",
            description = "Spreadsheet-compatible format",
            icon = Icons.Default.Download,
            onClick = onExportCsv
        )

        Divider()

        // Clear Data (Dangerous)
        SettingButton(
            title = "Clear All Data",
            description = "Permanently delete all locations and places",
            icon = Icons.Default.Delete,
            iconTint = MaterialTheme.colorScheme.error,
            onClick = onClearData
        )
    }
}
```

---

### 3.2 Implement Export Functionality

**File to Create**: `app/src/main/java/com/cosmiclaboratory/voyager/domain/usecase/ExportUseCases.kt`

```kotlin
@Singleton
class ExportDataUseCase @Inject constructor(
    private val locationRepository: LocationRepository,
    private val placeRepository: PlaceRepository,
    private val visitRepository: VisitRepository,
    private val jsonExporter: JsonExporter,
    private val csvExporter: CsvExporter
) {
    suspend fun exportAsJson(outputFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            val locations = locationRepository.getAllLocations()
            val places = placeRepository.getAllPlaces().first()
            val visits = visitRepository.getAllVisits().first()

            val exportData = ExportData(
                exportedAt = Instant.now(),
                version = "1.0",
                locations = locations,
                places = places,
                visits = visits
            )

            jsonExporter.export(exportData, outputFile)
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportAsCsv(outputDir: File): Result<List<File>> = withContext(Dispatchers.IO) {
        try {
            val locations = locationRepository.getAllLocations()
            val places = placeRepository.getAllPlaces().first()
            val visits = visitRepository.getAllVisits().first()

            val files = csvExporter.export(
                locations = locations,
                places = places,
                visits = visits,
                outputDir = outputDir
            )

            Result.success(files)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class ExportData(
    val exportedAt: Instant,
    val version: String,
    val locations: List<Location>,
    val places: List<Place>,
    val visits: List<Visit>
)
```

**JSON Exporter**:
**File to Create**: `app/src/main/java/com/cosmiclaboratory/voyager/data/export/JsonExporter.kt`

```kotlin
@Singleton
class JsonExporter @Inject constructor(
    private val gson: Gson
) {
    fun export(data: ExportData, outputFile: File) {
        outputFile.bufferedWriter().use { writer ->
            gson.toJson(data, writer)
        }
    }
}
```

**CSV Exporter**:
**File to Create**: `app/src/main/java/com/cosmiclaboratory/voyager/data/export/CsvExporter.kt`

```kotlin
@Singleton
class CsvExporter @Inject constructor() {

    fun export(
        locations: List<Location>,
        places: List<Place>,
        visits: List<Visit>,
        outputDir: File
    ): List<File> {
        val files = mutableListOf<File>()

        // Export locations
        files.add(exportLocations(locations, File(outputDir, "locations.csv")))

        // Export places
        files.add(exportPlaces(places, File(outputDir, "places.csv")))

        // Export visits
        files.add(exportVisits(visits, File(outputDir, "visits.csv")))

        return files
    }

    private fun exportLocations(locations: List<Location>, file: File): File {
        file.bufferedWriter().use { writer ->
            // Header
            writer.write("timestamp,latitude,longitude,accuracy,altitude,speed,bearing\n")

            // Data rows
            locations.forEach { location ->
                writer.write("${location.timestamp},${location.latitude},${location.longitude},${location.accuracy},${location.altitude ?: ""},${location.speed ?: ""},${location.bearing ?: ""}\n")
            }
        }
        return file
    }

    private fun exportPlaces(places: List<Place>, file: File): File {
        file.bufferedWriter().use { writer ->
            // Header
            writer.write("id,name,category,latitude,longitude,address,visitCount,totalTimeSpent,createdAt\n")

            // Data rows
            places.forEach { place ->
                writer.write("${place.id},\"${place.name}\",${place.category},${place.latitude},${place.longitude},\"${place.address ?: ""}\",${place.visitCount},${place.totalTimeSpent},${place.createdAt}\n")
            }
        }
        return file
    }

    private fun exportVisits(visits: List<Visit>, file: File): File {
        file.bufferedWriter().use { writer ->
            // Header
            writer.write("id,placeId,entryTime,exitTime,duration\n")

            // Data rows
            visits.forEach { visit ->
                writer.write("${visit.id},${visit.placeId},${visit.entryTime},${visit.exitTime ?: ""},${visit.duration}\n")
            }
        }
        return file
    }
}
```

**Usage in SettingsViewModel**:
```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val exportDataUseCase: ExportDataUseCase
) : ViewModel() {

    fun exportJson() {
        viewModelScope.launch {
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "voyager_export_${System.currentTimeMillis()}.json"
            )

            exportDataUseCase.exportAsJson(file).onSuccess {
                // Show success message
            }.onFailure {
                // Show error message
            }
        }
    }

    fun exportCsv() {
        viewModelScope.launch {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "voyager_export_${System.currentTimeMillis()}"
            )
            dir.mkdirs()

            exportDataUseCase.exportAsCsv(dir).onSuccess {
                // Show success message
            }.onFailure {
                // Show error message
            }
        }
    }
}
```

---

### 3.3 Enhance Analytics Visualization
**File to Modify**: `app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/insights/InsightsScreen.kt`

**Add Charting Library**:
```kotlin
// build.gradle.kts
implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
// OR
implementation("io.github.bytebeats:compose-charts:0.1.2")
```

**Pie Chart for Time by Category**:
```kotlin
@Composable
fun TimeByCategoryChart(data: Map<PlaceCategory, Long>) {
    Card {
        Column {
            Text("Time by Category", style = MaterialTheme.typography.titleMedium)

            // Pie chart showing percentage of time at each category
            PieChart(
                data = data.map { (category, timeMs) ->
                    PieChartEntry(
                        label = category.displayName,
                        value = timeMs.toFloat(),
                        color = category.color
                    )
                }
            )
        }
    }
}
```

**Line Chart for Daily Activity**:
```kotlin
@Composable
fun DailyActivityChart(data: List<DailyStats>) {
    Card {
        Column {
            Text("Daily Activity", style = MaterialTheme.typography.titleMedium)

            LineChart(
                data = data.map { stats ->
                    LineChartPoint(
                        x = stats.date.toEpochMilli().toFloat(),
                        y = stats.totalMinutes.toFloat()
                    )
                },
                xLabel = "Date",
                yLabel = "Minutes"
            )
        }
    }
}
```

---

### Phase 3 Success Criteria ✅
- [ ] All user preferences accessible in settings UI
- [ ] Battery mode configurable (Always-on, Smart, Geofence, Custom)
- [ ] Place detection settings adjustable with live previews
- [ ] JSON export creates valid, complete data file
- [ ] CSV export creates three files (locations, places, visits)
- [ ] Analytics screen shows pie charts, line charts
- [ ] Settings UI is intuitive and responsive

---

## PHASE 4: DAILY USAGE FEATURES
**Goal**: Make the app indispensable for daily use
**Timeline**: 2-4 hours
**Status**: 🔴 Not Started

### 4.1 Improve Onboarding Experience
**File to Create**: `app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/onboarding/OnboardingScreen.kt`

**Features**:
- Welcome screen explaining privacy-first design
- Permission requests with explanations (location, notifications)
- Quick setup (battery mode selection, basic preferences)
- Example use cases (life logging, time tracking, safety)

**Implementation** (abbreviated):
```kotlin
@Composable
fun OnboardingFlow() {
    var currentPage by remember { mutableStateOf(0) }

    when (currentPage) {
        0 -> WelcomeScreen(onNext = { currentPage++ })
        1 -> PrivacyExplanationScreen(onNext = { currentPage++ })
        2 -> PermissionsScreen(onNext = { currentPage++ })
        3 -> BatteryModeSelectionScreen(onNext = { currentPage++ })
        4 -> CompletionScreen(onFinish = { /* Navigate to dashboard */ })
    }
}
```

---

### 4.2 Add Home Screen Widget
**File to Create**: `app/src/main/java/com/cosmiclaboratory/voyager/widget/VoyagerWidget.kt`

**Widget Features**:
- Current place display
- Time at current place
- Quick action buttons (start/stop tracking)
- Today's stats summary

**Use Case**: Users can see current place without opening app

---

### 4.3 Implement Notification Summaries
**File to Create**: `app/src/main/java/com/cosmiclaboratory/voyager/notification/DailySummaryNotification.kt`

**Features**:
- Daily summary notification (evening)
- "Today you visited 5 places for 8 hours"
- Weekly summary (Monday morning)
- New place discovered notifications

**Implementation**:
```kotlin
@Singleton
class DailySummaryNotificationUseCase @Inject constructor(
    private val analyticsRepository: AnalyticsRepository,
    private val notificationManager: NotificationManager
) {
    suspend fun sendDailySummary() {
        val today = LocalDate.now()
        val stats = analyticsRepository.getDailyStats(today)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Daily Summary")
            .setContentText("Today: ${stats.placesVisited} places, ${stats.totalHours}h tracked")
            .setSmallIcon(R.drawable.ic_notification)
            .build()

        notificationManager.notify(DAILY_SUMMARY_ID, notification)
    }
}
```

**Schedule with WorkManager**:
```kotlin
class DailySummaryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        dailySummaryNotificationUseCase.sendDailySummary()
        return Result.success()
    }
}

// Schedule in Application.onCreate()
val dailySummaryWork = PeriodicWorkRequestBuilder<DailySummaryWorker>(1, TimeUnit.DAYS)
    .setInitialDelay(calculateDelayUntil(20, 0), TimeUnit.MILLISECONDS) // 8 PM
    .build()

WorkManager.getInstance(context).enqueue(dailySummaryWork)
```

---

### 4.4 Add Quick Actions
**File to Modify**: `app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/dashboard/DashboardScreen.kt`

**Quick Actions**:
- Start/Stop tracking (toggle button)
- Manual place detection (force scan now)
- View today's timeline
- View current place details

---

### Phase 4 Success Criteria ✅
- [ ] Onboarding flow completed on first launch
- [ ] Home screen widget shows current place
- [ ] Daily summary notifications sent at 8 PM
- [ ] Quick actions accessible from dashboard
- [ ] App feels essential for daily use

---

## TESTING & QUALITY ASSURANCE

### Unit Tests to Write
1. **GeocodingRepository**: Cache hit/miss, fallback logic
2. **PlaceDetectionUseCases**: Geocoding integration
3. **ExportUseCases**: JSON/CSV output validation
4. **SecurityUtils**: Keystore encryption/decryption

### Integration Tests to Write
1. **Database Migration 3→4**: Geocoding columns added
2. **WorkManager**: Place detection triggers correctly
3. **Geocoding Services**: Android Geocoder + Nominatim fallback

### Manual Testing Checklist
- [ ] Fresh install (onboarding flow)
- [ ] Geocoding works in urban areas
- [ ] Geocoding works in rural areas
- [ ] App works offline (cached geocoding)
- [ ] User can edit place names
- [ ] Export creates valid files
- [ ] Settings changes take effect immediately
- [ ] Database migration successful from v3
- [ ] Performance acceptable with 10,000+ locations
- [ ] Battery drain acceptable in all modes

---

## ESTIMATED TIMELINE SUMMARY

| Phase | Description | Time | Priority |
|-------|-------------|------|----------|
| Phase 1 | Free Geocoding Implementation | 4-6h | Critical |
| Phase 2 | Critical Fixes (Security, Performance) | 2-3h | High |
| Phase 3 | UX Enhancements (Settings, Export) | 2-3h | Medium |
| Phase 4 | Daily Usage Features (Widget, Notifications) | 2-4h | Medium |
| Phase 5 | Location Quality Enhancements | 3-4h | High |
| Phase 6 | Activity-First Enhancement (v2.0) | 12-16h | Medium (Future) |
| Testing | Unit, Integration, Manual Testing | 2-4h | High |
| **Total (Production)** | **Phases 1-5 + Testing** | **15-24h** | - |
| **Total (with v2.0)** | **All Phases** | **27-40h** | - |

---

## DEPLOYMENT CHECKLIST

### Before Production Release
- [ ] All critical bugs fixed
- [ ] Geocoding working reliably
- [ ] Database encryption key migrated to Keystore
- [ ] Database indexes added (performance)
- [ ] Export functionality tested
- [ ] Settings UI complete
- [ ] 70%+ unit test coverage
- [ ] Manual testing complete on multiple devices
- [ ] ProGuard rules configured (if using obfuscation)
- [ ] Privacy policy updated (if collecting any data)
- [ ] Play Store listing prepared

### Play Store Metadata
- **App Name**: Voyager - Location Analytics
- **Short Description**: Privacy-first location tracking & analytics
- **Category**: Tools
- **Content Rating**: Everyone
- **Privacy**: Declare no data collection (all local)

---

## FUTURE ENHANCEMENTS (Post-v1.0)

### Optional Features
1. **Google Places API Integration** (Premium)
   - Business names for all places
   - Ratings, reviews, photos
   - Opening hours
   - Place categories (restaurant, gym, etc.)
   - **Cost**: ~$5-$40 per 1000 API calls

2. **Cloud Backup** (Advanced)
   - Encrypted cloud sync
   - Multi-device support
   - Requires server infrastructure

3. **Advanced Analytics**
   - Heat maps of most visited areas
   - Movement pattern analysis
   - Predictions (where you'll go next)
   - Commute time tracking
   - Social features (share places with friends)

4. **Integrations**
   - Export to Google Timeline
   - Integration with fitness apps
   - Calendar integration (auto-tag events with location)

---

## PHASE 5: LOCATION QUALITY ENHANCEMENTS
**Goal**: Improve GPS data collection reliability
**Timeline**: 3-4 hours
**Status**: 🔴 Not Started
**Priority**: High (Affects core functionality)

### Background
Current strict GPS accuracy filtering (50-100m threshold) rejects most indoor/urban GPS readings, resulting in zero meaningful analytics data. This phase implements adaptive, context-aware filtering to improve data collection while maintaining quality.

### 5.1 Implement Adaptive Accuracy Thresholds
**File to Modify**: `app/src/main/java/com/cosmiclaboratory/voyager/data/location/LocationTrackingService.kt`

**Current Issue**:
```kotlin
// Too strict - rejects most indoor readings
val maxAccuracy = when (currentState) {
    STATIONARY -> minOf(preferences.maxGpsAccuracyMeters, 50f)
    else -> preferences.maxGpsAccuracyMeters
}
```

**Improved Implementation**:
```kotlin
private fun getContextualAccuracyThreshold(
    location: Location,
    preferences: UserPreferences,
    currentState: LocationState
): Float {
    val baseThreshold = preferences.maxGpsAccuracyMeters
    val timeSinceLastGoodReading = getTimeSinceLastAcceptedLocation()

    // Progressive relaxation when no good readings available
    val timeMultiplier = when {
        timeSinceLastGoodReading > 600_000L -> 2.0f  // 10+ min: 2x threshold
        timeSinceLastGoodReading > 300_000L -> 1.5f  // 5+ min: 1.5x threshold
        else -> 1.0f
    }

    // Environmental adaptation
    val environmentMultiplier = when {
        isIndoorEnvironment(location) -> 1.3f  // Relax 30% for indoor
        isUrbanEnvironment(location) -> 1.2f   // Relax 20% for urban
        else -> 1.0f
    }

    // Apply multipliers but respect user's maximum as ceiling
    val adaptedThreshold = baseThreshold * maxOf(timeMultiplier, environmentMultiplier)
    return minOf(adaptedThreshold, preferences.maxGpsAccuracyMeters * 2.5f) // Max 2.5x base
}

private fun isIndoorEnvironment(location: Location): Boolean {
    // Heuristics for indoor detection
    return location.accuracy > 80f && location.satelliteCount < 6
}

private fun isUrbanEnvironment(location: Location): Boolean {
    // High accuracy variance indicates urban canyon effect
    return location.accuracy in 40f..100f
}
```

**Benefits**:
- Accepts 80%+ of GPS readings in typical environments
- Maintains data flow even in poor GPS conditions
- Still respects user preferences as baseline
- Self-correcting (returns to strict when good readings available)

---

### 5.2 Multiple Location Provider Fusion
**File to Modify**: `LocationTrackingService.kt`

**Add Network Location Fallback**:
```kotlin
private suspend fun getBestAvailableLocation(): Location? {
    val gpsLocation = getGPSLocation()
    val networkLocation = getNetworkLocation()

    return when {
        // Prefer GPS if available and accurate
        gpsLocation != null && gpsLocation.accuracy < 100f -> gpsLocation

        // Fallback to network if GPS poor
        gpsLocation == null && networkLocation != null -> networkLocation

        // Use best of both
        else -> selectBestLocation(gpsLocation, networkLocation)
    }
}

private fun selectBestLocation(loc1: Location?, loc2: Location?): Location? {
    return when {
        loc1 == null -> loc2
        loc2 == null -> loc1
        loc1.accuracy < loc2.accuracy -> loc1
        else -> loc2
    }
}
```

---

### 5.3 GPS Quality Monitoring & User Feedback
**File to Create**: `app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/debug/GPSQualityScreen.kt`

**Features**:
- Real-time GPS accuracy display
- Satellite count and signal strength
- Location rejection statistics
- Suggestions for improving GPS reception
- Visual indicator of current GPS quality (Good/Fair/Poor)

**Implementation** (abbreviated):
```kotlin
@Composable
fun GPSQualityIndicator(quality: GPSQuality) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = when (quality) {
                GPSQuality.EXCELLENT -> Icons.Default.SignalCellular4Bar
                GPSQuality.GOOD -> Icons.Default.SignalCellular3Bar
                GPSQuality.FAIR -> Icons.Default.SignalCellular2Bar
                GPSQuality.POOR -> Icons.Default.SignalCellular1Bar
            },
            contentDescription = "GPS Quality",
            tint = quality.color
        )
        Text("GPS: ${quality.displayName}")
    }
}

enum class GPSQuality(val displayName: String, val color: Color) {
    EXCELLENT("Excellent", Color.Green),
    GOOD("Good", Color(0xFF8BC34A)),
    FAIR("Fair", Color.Yellow),
    POOR("Poor", Color.Red)
}
```

---

### 5.4 Settings UI for Location Quality
**File to Modify**: `SettingsScreen.kt`

**Add Location Quality Section**:
```kotlin
@Composable
fun LocationQualitySection(
    preferences: UserPreferences,
    gpsStats: GPSStatistics,
    onUpdatePreferences: (UserPreferences) -> Unit
) {
    ExpandableSection(title = "Location Quality") {
        // Current GPS status
        Card {
            Column {
                Text("Current GPS Quality: ${gpsStats.currentQuality}")
                Text("Acceptance Rate: ${gpsStats.acceptanceRate}%")
                Text("Avg Accuracy: ${gpsStats.avgAccuracy}m")
            }
        }

        // Accuracy threshold slider
        SettingSlider(
            title = "Base Accuracy Threshold",
            description = "${preferences.maxGpsAccuracyMeters.toInt()}m (adaptive: up to ${(preferences.maxGpsAccuracyMeters * 2.5).toInt()}m)",
            value = preferences.maxGpsAccuracyMeters,
            range = 50f..200f,
            onValueChange = { value ->
                onUpdatePreferences(preferences.copy(maxGpsAccuracyMeters = value))
            }
        )

        // Adaptive filtering toggle
        SettingSwitch(
            title = "Adaptive Filtering",
            description = "Automatically adjust thresholds based on GPS conditions",
            checked = preferences.enableAdaptiveFiltering,
            onCheckedChange = { enabled ->
                onUpdatePreferences(preferences.copy(enableAdaptiveFiltering = enabled))
            }
        )
    }
}
```

---

### 5.5 OSMDroid Performance Optimization
**File to Modify**: Map-related components using OSMDroid

**Tile Cache Optimization**:
```kotlin
// Configure OSMDroid for better performance
Configuration.getInstance().apply {
    tileFileSystemCacheMaxBytes = 50L * 1024 * 1024 // 50 MB
    tileFileSystemCacheTrimBytes = 40L * 1024 * 1024 // Trim to 40 MB

    // Use custom SQLite optimization
    tileFileSystemCacheMaxBytes = 100L * 1024 * 1024

    // Enable HTTP cache
    httpHeaderDateTimeFormat = "EEE, dd MMM yyyy HH:mm:ss z"
}

// Background tile loading
viewModelScope.launch(Dispatchers.IO) {
    // Load tiles in background thread
    mapView.tileProvider.ensureCapacity(100)
}
```

---

### Phase 5 Success Criteria ✅
- [ ] Location acceptance rate improves to 80%+ in typical environments
- [ ] Analytics show meaningful data even in indoor/urban scenarios
- [ ] GPS quality indicator visible to users
- [ ] Adaptive filtering reduces rejection without sacrificing too much quality
- [ ] OSMDroid tile loading doesn't block UI
- [ ] Users understand GPS quality status through clear UI feedback

---

## PHASE 6: ACTIVITY-FIRST ENHANCEMENT (FUTURE)
**Goal**: Transform from location-first to activity-first analytics
**Timeline**: 12-16 hours (4 sub-phases)
**Status**: 🔵 Planned (Not Started)
**Priority**: Medium (Major enhancement for v2.0)

### Vision
Transform Voyager from "where you were" to "what you were doing" by enriching location data with activity context (walking, driving, working out) and semantic meaning (commuting, eating, exercising).

**Key Benefits**:
- "You worked out for 45 min at Gold's Gym" vs "You were at (12.97, 77.59)"
- "Morning commute: 35 min" vs generic location tracking
- Better insights: workout sessions, meal times, work patterns
- Real place names from OSM + user customization

---

### 6.1 Foundation: Store Activity Context (Week 1)
**Goal**: Every GPS point knows what activity was happening

#### Extend Location Model
**Files to Modify**:
- `domain/model/Location.kt`
- `data/database/entity/LocationEntity.kt`

**Add Fields**:
```kotlin
data class Location(
    // ... existing fields

    // NEW: Activity context
    val userActivity: UserActivity = UserActivity.UNKNOWN,
    val activityConfidence: Float = 0f,  // 0.0-1.0

    // NEW: Semantic context (inferred from activity + time + patterns)
    val semanticContext: SemanticContext? = null
)

enum class UserActivity {
    STATIONARY, WALKING, RUNNING, CYCLING, DRIVING, UNKNOWN
}

enum class SemanticContext {
    // Work
    WORKING, COMMUTING, WORK_MEETING,

    // Health
    WORKING_OUT, OUTDOOR_EXERCISE,

    // Daily
    EATING, SHOPPING, RUNNING_ERRANDS,

    // Leisure
    SOCIALIZING, ENTERTAINMENT, RELAXING_HOME,

    // Transit
    IN_TRANSIT, TRAVELING,

    UNKNOWN
}
```

**Database Migration**:
```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE locations ADD COLUMN userActivity TEXT DEFAULT 'UNKNOWN'")
        database.execSQL("ALTER TABLE locations ADD COLUMN activityConfidence REAL DEFAULT 0.0")
        database.execSQL("ALTER TABLE locations ADD COLUMN semanticContext TEXT")
        database.execSQL("CREATE INDEX index_locations_activity ON locations(userActivity)")
        database.execSQL("CREATE INDEX index_locations_semantic ON locations(semanticContext)")
    }
}
```

---

### 6.2 Integration: Activity-Aware Place Detection (Week 2)
**Goal**: Use activity context in DBSCAN clustering

**Current Issue**: DBSCAN clusters all coordinates equally, creating false places when stuck in traffic

**Improved Logic**:
```kotlin
suspend fun detectNewPlaces(): List<Place> {
    // Filter locations by activity - exclude transit
    val stationaryLocations = locationRepository
        .getRecentLocations(preferences.clusteringWindowDays)
        .filter { location ->
            location.userActivity in listOf(
                UserActivity.STATIONARY,
                UserActivity.WALKING  // Slow walking = exploring place
            ) && location.speed < 5f  // < 5 m/s
        }

    // Run DBSCAN on filtered set
    val clusters = dbscan.cluster(stationaryLocations)

    // Create places with activity-enhanced categorization
    clusters.map { cluster ->
        val category = inferCategoryFromActivity(cluster)
        createPlace(cluster, category)
    }
}

private fun inferCategoryFromActivity(cluster: List<Location>): PlaceCategory {
    val semanticContexts = cluster.mapNotNull { it.semanticContext }

    return when {
        semanticContexts.count { it == SemanticContext.WORKING } > cluster.size * 0.7 ->
            PlaceCategory.WORK
        semanticContexts.count { it == SemanticContext.WORKING_OUT } > cluster.size * 0.5 ->
            PlaceCategory.GYM
        semanticContexts.count { it == SemanticContext.EATING } > cluster.size * 0.6 ->
            PlaceCategory.RESTAURANT
        // ... more inference rules
        else -> PlaceCategory.OTHER
    }
}
```

---

### 6.3 Intelligence: Semantic Context Inference (Week 3)
**Goal**: Infer what user was doing from activity + time + location

**File to Create**: `domain/usecase/InferSemanticContextUseCase.kt`

```kotlin
@Singleton
class InferSemanticContextUseCase @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val timePatternAnalyzer: TimePatternAnalyzer
) {
    suspend operator fun invoke(location: Location): SemanticContext {
        val nearbyPlace = findNearbyPlace(location)
        val timeOfDay = location.timestamp.hour
        val dayOfWeek = location.timestamp.dayOfWeek
        val activity = location.userActivity

        return when {
            // Work patterns
            nearbyPlace?.category == PlaceCategory.WORK &&
            timeOfDay in 8..18 && dayOfWeek in DayOfWeek.MONDAY..DayOfWeek.FRIDAY ->
                SemanticContext.WORKING

            // Commute detection
            activity == UserActivity.DRIVING &&
            timeOfDay in listOf(7, 8, 9, 17, 18, 19) &&
            isCommutePath(location) ->
                SemanticContext.COMMUTING

            // Workout detection
            nearbyPlace?.category == PlaceCategory.GYM &&
            activity in listOf(UserActivity.WALKING, UserActivity.RUNNING) ->
                SemanticContext.WORKING_OUT

            // Meal times
            nearbyPlace?.category == PlaceCategory.RESTAURANT &&
            activity == UserActivity.STATIONARY &&
            timeOfDay in listOf(7, 8, 12, 13, 19, 20) ->
                SemanticContext.EATING

            // Default inference
            else -> inferFromActivity(activity)
        }
    }
}
```

---

### 6.4 Insights: Activity-Based Analytics (Week 4)
**Goal**: Provide meaningful activity insights

**File to Create**: `domain/usecase/ActivityAnalyticsUseCases.kt`

```kotlin
@Singleton
class GetActivityInsightsUseCase @Inject constructor(
    private val locationRepository: LocationRepository,
    private val visitRepository: VisitRepository
) {
    suspend operator fun invoke(dateRange: ClosedRange<LocalDate>): ActivityInsights {
        val locations = locationRepository.getLocationsByDateRange(dateRange)

        return ActivityInsights(
            workoutSessions = detectWorkoutSessions(locations),
            commuteStats = analyzeCommutes(locations),
            mealTimes = detectMealPatterns(locations),
            timeByActivity = groupByActivity(locations),
            timeBySemanticContext = groupByContext(locations)
        )
    }

    private fun detectWorkoutSessions(locations: List<Location>): List<WorkoutSession> {
        // Group consecutive locations with WORKING_OUT context
        return locations
            .filter { it.semanticContext == SemanticContext.WORKING_OUT }
            .groupByConsecutive { it.timestamp }
            .map { session ->
                WorkoutSession(
                    startTime = session.first().timestamp,
                    endTime = session.last().timestamp,
                    duration = Duration.between(session.first().timestamp, session.last().timestamp),
                    location = session.first().nearbyPlace,
                    distanceCovered = calculateDistance(session)
                )
            }
    }
}

data class ActivityInsights(
    val workoutSessions: List<WorkoutSession>,
    val commuteStats: CommuteStatistics,
    val mealTimes: List<MealTime>,
    val timeByActivity: Map<UserActivity, Duration>,
    val timeBySemanticContext: Map<SemanticContext, Duration>
)
```

---

### Phase 6 Implementation Plan

| Sub-Phase | Focus | Files | Effort |
|-----------|-------|-------|--------|
| 6.1 | Data Model + Storage | Location.kt, LocationEntity.kt, Migration | 3h |
| 6.2 | Activity-Aware Detection | PlaceDetectionUseCases.kt | 4h |
| 6.3 | Semantic Inference | InferSemanticContextUseCase.kt | 4h |
| 6.4 | Activity Analytics | ActivityAnalyticsUseCases.kt, UI | 5h |

**Total**: 16 hours

### Phase 6 Success Criteria ✅
- [ ] Activity data stored with every location point
- [ ] DBSCAN filtering excludes transit locations
- [ ] Semantic context inference 70%+ accurate
- [ ] Activity-based insights visible in UI
- [ ] "Workout sessions" screen shows duration, place, distance
- [ ] "Commute analysis" shows average time, routes
- [ ] User can query "show me all my workouts this month"

**Note**: This is a v2.0 feature - prioritize after core stability and geocoding implementation.

---

## CONCLUSION

This roadmap provides a clear path from working prototype to production-ready application. **Priority is given to free geocoding implementation** (Phase 1) as it solves the primary user complaint about generic place names. Critical security and performance fixes (Phase 2) ensure the app is safe and responsive. UX enhancements (Phase 3) and daily usage features (Phase 4) make the app indispensable. Location quality improvements (Phase 5) ensure reliable data collection. Activity-first enhancement (Phase 6) represents the future vision for v2.0.

**Total estimated development time**:
- **Phases 1-4** (Production-ready): 12-20 hours
- **Phase 5** (Quality improvements): 3-4 hours
- **Phase 6** (Activity-first v2.0): 16 hours
- **Grand Total**: 31-40 hours

With this implementation, Voyager will transform from a technical demo to a practical, daily-use location analytics application that users will love.

---

**Next Steps**: Begin Phase 1 implementation immediately.

**For Current Status**: See `VOYAGER_PROJECT_STATUS.md`
**For Architecture Details**: See `ARCHITECTURE_GUIDE.md`
**For Usage Strategy**: See `USAGE_OPTIMIZATION_STRATEGY.md`
