package com.cosmiclaboratory.voyager.utils

import android.content.Context
import android.util.Log

/**
 * Utility functions for safe Android framework interactions to prevent NPEs
 */
object AndroidSafetyUtils {
    
    const val TAG = "AndroidSafetyUtils"
    
    /**
     * Safely get a system service with null checks and exception handling
     */
    inline fun <reified T> Context.getSystemServiceSafely(serviceName: String): T? {
        return try {
            val service = getSystemService(serviceName)
            if (service is T) {
                service
            } else {
                Log.w(TAG, "System service $serviceName is not of expected type ${T::class.simpleName}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL: Failed to get system service $serviceName", e)
            null
        }
    }
    
    /**
     * Safely execute a block with potential reflection/system calls
     */
    inline fun <T> executeWithAndroidSafety(
        operation: String,
        fallback: T,
        block: () -> T
    ): T {
        return try {
            block()
        } catch (e: NullPointerException) {
            Log.w(TAG, "NPE prevented in $operation, using fallback", e)
            fallback
        } catch (e: SecurityException) {
            Log.w(TAG, "Security exception in $operation, using fallback", e)
            fallback
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Illegal state in $operation, using fallback", e)
            fallback
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in $operation, using fallback", e)
            fallback
        }
    }
    
    /**
     * Safely execute a block that might use reflection
     */
    inline fun <T> executeWithReflectionSafety(
        operation: String,
        fallback: T? = null,
        block: () -> T
    ): T? {
        return try {
            block()
        } catch (e: NullPointerException) {
            if (e.message?.contains("invoke") == true || 
                e.message?.contains("Method") == true ||
                e.message?.contains("reflect") == true) {
                Log.w(TAG, "CRITICAL: Reflection NPE prevented in $operation - method or object is null", e)
            } else {
                Log.w(TAG, "NPE prevented in $operation", e)
            }
            fallback
        } catch (e: NoSuchMethodException) {
            Log.w(TAG, "Method not found in $operation - possibly obfuscated", e)
            fallback
        } catch (e: IllegalAccessException) {
            Log.w(TAG, "Access denied in $operation - method not accessible", e)
            fallback
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Invalid arguments in $operation - method signature mismatch", e)
            fallback
        } catch (e: java.lang.reflect.InvocationTargetException) {
            Log.w(TAG, "Method invocation failed in $operation", e.cause ?: e)
            fallback
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "Class not found in $operation - possibly obfuscated", e)
            fallback
        } catch (e: InstantiationException) {
            Log.w(TAG, "Cannot instantiate class in $operation", e)
            fallback
        } catch (e: SecurityException) {
            Log.w(TAG, "Security restriction in $operation", e)
            fallback
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected reflection error in $operation", e)
            fallback
        }
    }
    
    /**
     * Safely invoke a method via reflection with comprehensive error handling
     */
    fun <T> safeReflectionInvoke(
        target: Any?,
        methodName: String,
        vararg args: Any?,
        fallback: T? = null
    ): T? {
        return executeWithReflectionSafety("safeReflectionInvoke($methodName)", fallback) {
            if (target == null) {
                Log.w(TAG, "Cannot invoke $methodName - target object is null")
                return@executeWithReflectionSafety fallback
            }
            
            val targetClass = target::class.java
            val argTypes = args.map { it?.javaClass ?: Object::class.java }.toTypedArray()
            
            val method = targetClass.getMethod(methodName, *argTypes)
            if (method == null) {
                Log.w(TAG, "Method $methodName not found on ${targetClass.simpleName}")
                return@executeWithReflectionSafety fallback
            }
            
            method.isAccessible = true
            val result = method.invoke(target, *args)
            
            @Suppress("UNCHECKED_CAST")
            result as? T
        }
    }
    
    /**
     * Safely get a field value via reflection
     */
    fun <T> safeReflectionFieldGet(
        target: Any?,
        fieldName: String,
        fallback: T? = null
    ): T? {
        return executeWithReflectionSafety("safeReflectionFieldGet($fieldName)", fallback) {
            if (target == null) {
                Log.w(TAG, "Cannot get field $fieldName - target object is null")
                return@executeWithReflectionSafety fallback
            }
            
            val targetClass = target::class.java
            val field = targetClass.getDeclaredField(fieldName)
            if (field == null) {
                Log.w(TAG, "Field $fieldName not found on ${targetClass.simpleName}")
                return@executeWithReflectionSafety fallback
            }
            
            field.isAccessible = true
            val result = field.get(target)
            
            @Suppress("UNCHECKED_CAST")
            result as? T
        }
    }
    
    /**
     * Safely access nullable properties with logging
     */
    inline fun <T, R> T?.accessSafely(
        propertyName: String,
        fallback: R,
        accessor: T.() -> R
    ): R {
        return if (this != null) {
            try {
                accessor()
            } catch (e: Exception) {
                Log.w(TAG, "Error accessing $propertyName, using fallback", e)
                fallback
            }
        } else {
            Log.d(TAG, "Null object when accessing $propertyName, using fallback")
            fallback
        }
    }
    
    /**
     * Create a safe wrapper for potentially unstable Android operations
     */
    inline fun withAndroidStability(operation: String, block: () -> Unit): Boolean {
        return try {
            block()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Android operation '$operation' failed safely", e)
            false
        }
    }
}