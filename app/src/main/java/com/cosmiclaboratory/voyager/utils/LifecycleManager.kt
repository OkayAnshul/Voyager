package com.cosmiclaboratory.voyager.utils

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized Lifecycle Manager for proper resource management
 * CRITICAL: Prevents memory leaks and ensures proper cleanup of coroutine scopes
 */
@Singleton
class LifecycleManager @Inject constructor(
    private val logger: ProductionLogger
) {
    
    companion object {
        private const val TAG = "LifecycleManager"
    }
    
    private val scopeRegistry = ConcurrentHashMap<String, ManagedScope>()
    private val registryMutex = Mutex()
    
    /**
     * Register a managed coroutine scope with automatic lifecycle tracking
     */
    suspend fun registerScope(
        name: String,
        scope: CoroutineScope,
        parentJob: Job? = null
    ): ManagedScope {
        return registryMutex.withLock {
            val managedScope = ManagedScope(
                name = name,
                scope = scope,
                parentJob = parentJob,
                createdAt = System.currentTimeMillis()
            )
            
            scopeRegistry[name] = managedScope
            
            // Add exception handler to the scope if possible
            scope.coroutineContext[Job]?.invokeOnCompletion { exception ->
                if (exception != null) {
                    logger.e(TAG, "Scope '$name' completed with exception", exception)
                } else {
                    logger.d(TAG, "Scope '$name' completed successfully")
                }
                
                // Auto-cleanup completed scopes
                runBlocking {
                    unregisterScope(name)
                }
            }
            
            logger.i(TAG, "Registered scope: $name")
            managedScope
        }
    }
    
    /**
     * Create a new managed scope with proper cancellation handling
     */
    suspend fun createManagedScope(
        name: String,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
        supervisorJob: Boolean = true
    ): ManagedScope {
        val job = if (supervisorJob) SupervisorJob() else Job()
        val scope = CoroutineScope(job + dispatcher + CoroutineExceptionHandler { _, exception ->
            logger.e(TAG, "Uncaught exception in scope '$name'", exception)
        })
        
        return registerScope(name, scope, job)
    }
    
    /**
     * Unregister and cleanup a managed scope
     */
    suspend fun unregisterScope(name: String): Boolean {
        return registryMutex.withLock {
            val managedScope = scopeRegistry.remove(name)
            if (managedScope != null) {
                try {
                    // Cancel the scope
                    managedScope.parentJob?.cancel()
                    managedScope.scope.cancel()
                    
                    val lifetime = System.currentTimeMillis() - managedScope.createdAt
                    logger.i(TAG, "Unregistered scope: $name (lifetime: ${lifetime}ms)")
                    true
                } catch (e: Exception) {
                    logger.e(TAG, "Error unregistering scope: $name", e)
                    false
                }
            } else {
                logger.w(TAG, "Attempted to unregister unknown scope: $name")
                false
            }
        }
    }
    
    /**
     * Get a registered scope by name
     */
    fun getScope(name: String): ManagedScope? {
        return scopeRegistry[name]
    }
    
    /**
     * Cleanup all registered scopes (for app shutdown)
     */
    suspend fun cleanupAllScopes() {
        registryMutex.withLock {
            logger.i(TAG, "Cleaning up ${scopeRegistry.size} registered scopes")
            
            scopeRegistry.values.forEach { managedScope ->
                try {
                    managedScope.parentJob?.cancel()
                    managedScope.scope.cancel()
                } catch (e: Exception) {
                    logger.e(TAG, "Error cleaning up scope: ${managedScope.name}", e)
                }
            }
            
            scopeRegistry.clear()
            logger.i(TAG, "All scopes cleaned up")
        }
    }
    
    /**
     * Get health status of all scopes
     */
    suspend fun getScopeHealthStatus(): ScopeHealthStatus {
        return registryMutex.withLock {
            val activeScopes = mutableListOf<String>()
            val completedScopes = mutableListOf<String>()
            val cancelledScopes = mutableListOf<String>()
            
            scopeRegistry.forEach { (name, managedScope) ->
                when {
                    managedScope.scope.isActive -> activeScopes.add(name)
                    managedScope.parentJob?.isCompleted == true -> completedScopes.add(name)
                    managedScope.parentJob?.isCancelled == true -> cancelledScopes.add(name)
                    else -> cancelledScopes.add(name) // Assume cancelled if unknown state
                }
            }
            
            ScopeHealthStatus(
                totalScopes = scopeRegistry.size,
                activeScopes = activeScopes,
                completedScopes = completedScopes,
                cancelledScopes = cancelledScopes
            )
        }
    }
    
    /**
     * Force cleanup of stale scopes (running longer than threshold)
     */
    suspend fun cleanupStaleScopes(maxLifetimeMs: Long = 3600_000L) { // 1 hour default
        registryMutex.withLock {
            val currentTime = System.currentTimeMillis()
            val staleScopes = scopeRegistry.filter { (_, managedScope) ->
                (currentTime - managedScope.createdAt) > maxLifetimeMs
            }
            
            if (staleScopes.isNotEmpty()) {
                logger.w(TAG, "Found ${staleScopes.size} stale scopes, cleaning up...")
                
                staleScopes.forEach { (name, managedScope) ->
                    try {
                        managedScope.parentJob?.cancel()
                        managedScope.scope.cancel()
                        scopeRegistry.remove(name)
                        
                        val lifetime = currentTime - managedScope.createdAt
                        logger.w(TAG, "Cleaned up stale scope: $name (lifetime: ${lifetime}ms)")
                    } catch (e: Exception) {
                        logger.e(TAG, "Error cleaning up stale scope: $name", e)
                    }
                }
            }
        }
    }
}

/**
 * Managed scope wrapper with metadata
 */
data class ManagedScope(
    val name: String,
    val scope: CoroutineScope,
    val parentJob: Job?,
    val createdAt: Long
) {
    val isActive: Boolean
        get() = scope.isActive
        
    val lifetime: Long
        get() = System.currentTimeMillis() - createdAt
}

/**
 * Health status of all managed scopes
 */
data class ScopeHealthStatus(
    val totalScopes: Int,
    val activeScopes: List<String>,
    val completedScopes: List<String>,
    val cancelledScopes: List<String>
) {
    val healthyRatio: Float
        get() = if (totalScopes == 0) 1.0f else activeScopes.size.toFloat() / totalScopes.toFloat()
}

/**
 * Extension functions for easy lifecycle management
 */
suspend fun CoroutineScope.registerWith(lifecycleManager: LifecycleManager, name: String): ManagedScope {
    return lifecycleManager.registerScope(name, this)
}

fun CoroutineScope.launchManaged(
    name: String,
    lifecycleManager: LifecycleManager,
    block: suspend CoroutineScope.() -> Unit
): Job {
    return this.launch {
        try {
            lifecycleManager.registerScope(name, this@launchManaged)
            block()
        } finally {
            lifecycleManager.unregisterScope(name)
        }
    }
}