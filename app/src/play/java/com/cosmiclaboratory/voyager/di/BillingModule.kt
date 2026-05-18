package com.cosmiclaboratory.voyager.di

import com.cosmiclaboratory.voyager.billing.BillingClientWrapper
import com.cosmiclaboratory.voyager.domain.billing.BillingGateway
import com.cosmiclaboratory.voyager.domain.billing.EntitlementSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Play-flavor billing bindings. A single [BillingClientWrapper] singleton backs both
 * billing surfaces. The `fdroid` flavor ships its own [BillingModule] in `src/fdroid`;
 * exactly one is compiled per build.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {

    @Binds
    @Singleton
    abstract fun bindEntitlementSource(impl: BillingClientWrapper): EntitlementSource

    @Binds
    @Singleton
    abstract fun bindBillingGateway(impl: BillingClientWrapper): BillingGateway
}
