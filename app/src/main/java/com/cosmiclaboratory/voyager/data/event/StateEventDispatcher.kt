package com.cosmiclaboratory.voyager.data.event

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * State Event Dispatcher - Handles event-driven state synchronization
 * CRITICAL: Ensures proper propagation of state changes across all components
 */
@Singleton
class StateEventDispatcher @Inject constructor() {
    
    companion object {
        private const val TAG = "StateEventDispatcher"
    }
    
    private val eventMutex = Mutex()
    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Event channels for different types of state changes
    private val _locationEvents = MutableSharedFlow<LocationEvent>(replay = 0, extraBufferCapacity = 64)
    val locationEvents: SharedFlow<LocationEvent> = _locationEvents.asSharedFlow()
    
    private val _placeEvents = MutableSharedFlow<PlaceEvent>(replay = 0, extraBufferCapacity = 64)
    val placeEvents: SharedFlow<PlaceEvent> = _placeEvents.asSharedFlow()
    
    private val _visitEvents = MutableSharedFlow<VisitEvent>(replay = 0, extraBufferCapacity = 64)
    val visitEvents: SharedFlow<VisitEvent> = _visitEvents.asSharedFlow()
    
    private val _trackingEvents = MutableSharedFlow<TrackingEvent>(replay = 0, extraBufferCapacity = 64)
    val trackingEvents: SharedFlow<TrackingEvent> = _trackingEvents.asSharedFlow()
    
    // Event listeners registry
    private val eventListeners = mutableMapOf<String, EventListener>()
    
    /**
     * Dispatch location-related events
     */
    suspend fun dispatchLocationEvent(event: LocationEvent) {
        eventMutex.withLock {
            try {
                Log.d(TAG, "Dispatching location event: ${event.type} at ${event.timestamp}")
                _locationEvents.emit(event)
                
                // Notify registered listeners
                notifyListeners(event)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dispatch location event: ${event.type}", e)
            }
        }
    }
    
    /**
     * Dispatch place-related events
     */
    suspend fun dispatchPlaceEvent(event: PlaceEvent) {
        eventMutex.withLock {
            try {
                Log.d(TAG, "Dispatching place event: ${event.type} for place ${event.placeId}")
                _placeEvents.emit(event)
                
                // Notify registered listeners
                notifyListeners(event)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dispatch place event: ${event.type}", e)
            }
        }
    }
    
    /**
     * Dispatch visit-related events
     */
    suspend fun dispatchVisitEvent(event: VisitEvent) {
        eventMutex.withLock {
            try {
                Log.d(TAG, "Dispatching visit event: ${event.type} for visit ${event.visitId}")
                _visitEvents.emit(event)
                
                // Notify registered listeners
                notifyListeners(event)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dispatch visit event: ${event.type}", e)
            }
        }
    }
    
    /**
     * Dispatch tracking-related events
     */
    suspend fun dispatchTrackingEvent(event: TrackingEvent) {
        eventMutex.withLock {
            try {
                Log.d(TAG, "Dispatching tracking event: ${event.type} - active: ${event.isActive}")
                _trackingEvents.emit(event)
                
                // Notify registered listeners
                notifyListeners(event)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dispatch tracking event: ${event.type}", e)
            }
        }
    }
    
    /**
     * Register event listener
     */
    fun registerListener(name: String, listener: EventListener) {
        eventListeners[name] = listener
        Log.d(TAG, "Registered event listener: $name")
    }
    
    /**
     * Unregister event listener
     */
    fun unregisterListener(name: String) {
        eventListeners.remove(name)
        Log.d(TAG, "Unregistered event listener: $name")
    }
    
    /**
     * Notify all registered listeners
     */
    private suspend fun notifyListeners(event: StateEvent) {
        eventListeners.values.forEach { listener ->
            try {
                listener.onStateEvent(event)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying listener about event: ${event.type}", e)
            }
        }
    }
    
    /**
     * Batch dispatch multiple events atomically
     */
    suspend fun dispatchBatch(events: List<StateEvent>) {
        eventMutex.withLock {
            try {
                Log.d(TAG, "Dispatching batch of ${events.size} events")
                
                events.forEach { event ->
                    when (event) {
                        is LocationEvent -> _locationEvents.emit(event)
                        is PlaceEvent -> _placeEvents.emit(event)
                        is VisitEvent -> _visitEvents.emit(event)
                        is TrackingEvent -> _trackingEvents.emit(event)
                    }
                }
                
                // Notify listeners of batch completion
                events.forEach { event ->
                    notifyListeners(event)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dispatch event batch", e)
            }
        }
    }
    
    /**
     * Clear all event buffers
     */
    suspend fun clearEvents() {
        eventMutex.withLock {
            Log.d(TAG, "Clearing all event buffers")
            // Note: SharedFlow doesn't have clear method, events will naturally expire
        }
    }
}

// Event interfaces and data classes

interface EventListener {
    suspend fun onStateEvent(event: StateEvent)
}

sealed class StateEvent {
    abstract val type: String
    abstract val timestamp: LocalDateTime
}

data class LocationEvent(
    override val type: String,
    override val timestamp: LocalDateTime = LocalDateTime.now(),
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float?,
    val source: String
) : StateEvent()

data class PlaceEvent(
    override val type: String,
    override val timestamp: LocalDateTime = LocalDateTime.now(),
    val placeId: Long,
    val placeName: String,
    val action: PlaceAction
) : StateEvent()

data class VisitEvent(
    override val type: String,
    override val timestamp: LocalDateTime = LocalDateTime.now(),
    val visitId: Long,
    val placeId: Long,
    val action: VisitAction,
    val entryTime: LocalDateTime?,
    val exitTime: LocalDateTime?
) : StateEvent()

data class TrackingEvent(
    override val type: String,
    override val timestamp: LocalDateTime = LocalDateTime.now(),
    val isActive: Boolean,
    val reason: String,
    val source: String
) : StateEvent()

enum class PlaceAction {
    DETECTED,
    ENTERED,
    EXITED,
    UPDATED,
    DELETED
}

enum class VisitAction {
    STARTED,
    ENDED,
    UPDATED,
    CANCELLED
}

// Event type constants
object EventTypes {
    const val LOCATION_UPDATE = "location_update"
    const val LOCATION_LOST = "location_lost"
    
    const val PLACE_DETECTED = "place_detected"
    const val PLACE_ENTERED = "place_entered"
    const val PLACE_EXITED = "place_exited"
    
    const val VISIT_STARTED = "visit_started"
    const val VISIT_ENDED = "visit_ended"
    
    const val TRACKING_STARTED = "tracking_started"
    const val TRACKING_STOPPED = "tracking_stopped"
    const val TRACKING_PAUSED = "tracking_paused"
    const val TRACKING_RESUMED = "tracking_resumed"
}