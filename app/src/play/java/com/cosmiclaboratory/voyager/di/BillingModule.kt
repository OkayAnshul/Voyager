package com.cosmiclaboratory.voyager.di

import com.cosmiclaboratory.voyager.billing.PlayEntitlementSource
import com.cosmiclaboratory.voyager.domain.billing.EntitlementSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Play-flavor billing bindings. The `fdroid` flavor ships its own [BillingModule]
 * in `src/fdroid`; exactly one is compiled per build.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {

    @Binds
    @Singleton
    abstract fun bindEntitlementSource(impl: PlayEntitlementSource): EntitlementSource
}
