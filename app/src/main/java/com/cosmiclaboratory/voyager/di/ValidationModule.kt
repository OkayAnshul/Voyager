package com.cosmiclaboratory.voyager.di

import com.cosmiclaboratory.voyager.domain.validation.*
import com.cosmiclaboratory.voyager.utils.ErrorHandler
import com.cosmiclaboratory.voyager.utils.ProductionLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency injection module for validation framework
 * Provides all validation-related dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object ValidationModule {
    
    @Provides
    @Singleton
    fun provideLocationValidator(): LocationValidator {
        return LocationValidator()
    }
    
    @Provides
    @Singleton
    fun providePlaceValidator(
        locationValidator: LocationValidator
    ): PlaceValidator {
        return PlaceValidator(locationValidator)
    }
    
    @Provides
    @Singleton
    fun provideBusinessRuleEngine(): BusinessRuleEngine {
        return BusinessRuleEngine()
    }
    
    @Provides
    @Singleton
    fun provideValidationService(
        locationValidator: LocationValidator,
        placeValidator: PlaceValidator,
        businessRuleEngine: BusinessRuleEngine,
        errorHandler: ErrorHandler,
        logger: ProductionLogger
    ): ValidationService {
        return ValidationService(
            locationValidator = locationValidator,
            placeValidator = placeValidator,
            businessRuleEngine = businessRuleEngine,
            errorHandler = errorHandler,
            logger = logger
        )
    }
}