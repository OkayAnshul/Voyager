package com.cosmiclaboratory.voyager.di

import com.cosmiclaboratory.voyager.billing.FdroidBillingGateway
import com.cosmiclaboratory.voyager.billing.FdroidEntitlementSource
import com.cosmiclaboratory.voyager.domain.billing.BillingGateway
import com.cosmiclaboratory.voyager.domain.billing.EntitlementSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * F-Droid-flavor billing bindings — no proprietary billing code. The `play` flavor
 * ships its own [BillingModule] in `src/play`; exactly one is compiled per build.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {

    @Binds
    @Singleton
    abstract fun bindEntitlementSource(impl: FdroidEntitlementSource): EntitlementSource

    @Binds
    @Singleton
    abstract fun bindBillingGateway(impl: FdroidBillingGateway): BillingGateway
}
