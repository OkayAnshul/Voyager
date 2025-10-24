package com.cosmiclaboratory.voyager.di

import com.cosmiclaboratory.voyager.utils.ProductionLogger
import com.cosmiclaboratory.voyager.utils.LifecycleManager
import com.cosmiclaboratory.voyager.utils.WorkManagerHelper
import com.cosmiclaboratory.voyager.utils.ErrorHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency Injection module for utility components
 */
@Module
@InstallIn(SingletonComponent::class)
object UtilsModule {
    
    @Provides
    @Singleton
    fun provideProductionLogger(): ProductionLogger {
        return ProductionLogger()
    }
    
    @Provides
    @Singleton
    fun provideLifecycleManager(
        logger: ProductionLogger
    ): LifecycleManager {
        return LifecycleManager(logger)
    }
    
    // ErrorHandler is automatically provided by @Inject constructor
    // No explicit provider needed due to @Singleton annotation
    
    // WorkManagerHelper is automatically provided by @Inject constructor  
    // No explicit provider needed due to @Singleton annotation
}