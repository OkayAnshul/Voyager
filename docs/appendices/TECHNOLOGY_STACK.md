# Appendix D: Technology Stack Deep Dive

**Last Updated:** December 11, 2025

Comprehensive reference for all technologies, libraries, and frameworks used in Voyager.

---

## Core Technologies

### Kotlin 2.0.21

**Why Chosen:** Modern, null-safe, concise

**Key Features:**
- Coroutines for async programming
- Flow for reactive data streams
- Data classes reduce boilerplate
- Sealed classes for type-safe states
- Extension functions

**Example:**
```kotlin
// Coroutines
suspend fun detectPlaces() = withContext(Dispatchers.IO) { /* Heavy work */ }

// Flow
fun observePlaces(): Flow<List<Place>> = flow { emit(getPlaces()) }

// Sealed class
sealed class UiState {
    object Loading : UiState()
    data class Success(val data: List<Place>) : UiState()
    data class Error(val message: String) : UiState()
}
```

---

### Jetpack Compose

**Why Over XML:** Declarative UI, less boilerplate, better performance

**Material Design 3:** Modern design language with dynamic theming

---

### Hilt (Dagger)

**9 Hilt Modules:**
1. DatabaseModule
2. RepositoryModule
3. UseCasesModule
4. StateModule
5. LocationModule
6. ValidationModule
7. UtilsModule
8. NetworkModule
9. OrchestratorModule

---

### Room + SQLCipher

**10 Entities:**
1. LocationEntity - GPS coordinates
2. PlaceEntity - Detected places
3. VisitEntity - Time at places
4. GeofenceEntity - Geofences
5. PlaceReviewEntity - User reviews
6. VisitReviewEntity
7. UserCorrectionEntity
8. CategoryPreferenceEntity
9. GeocodingCacheEntity
10. CurrentStateEntity

**Encryption:** AES-256 via SQLCipher

---

## Key Algorithms

### DBSCAN Clustering

**Parameters:**
- eps: 50 meters
- minPts: 3 points

**Complexity:** O(n²) current, O(n log n) with spatial index

---

### Haversine Distance

**Formula:**
```
a = sin²(Δlat/2) + cos(lat1)⋅cos(lat2)⋅sin²(Δlon/2)
c = 2⋅atan2(√a, √(1−a))
d = R⋅c (R = 6371 km)
```

**Accuracy:** ±0.5% for distances < 1000 km

---

## Libraries

### Google Play Services
- FusedLocationProviderClient
- GeofencingClient
- ActivityRecognitionClient

### OpenStreetMap
- OSMDroid for maps
- Nominatim for geocoding

### Testing (Planned)
- JUnit 5
- MockK
- Turbine
- Robolectric
- Espresso

---

## Build Tools

**Gradle 8.x** with Kotlin DSL
**Android Gradle Plugin:** 8.2.x
**Kotlin Plugin:** 2.0.21
**KSP:** Kotlin Symbol Processing

---

## Performance

**Database Size:** 10-50 MB for year of data
**DBSCAN:** 2000 points in ~5 seconds
**GPS Filtering:** 60% reduction in writes
**Battery:** 40% improvement vs. no filtering

---

For detailed implementation examples, see the main VOYAGER_COMPLETE_GUIDE.md
