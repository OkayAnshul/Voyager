package com.cosmiclaboratory.voyager.platform.scope

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Singleton
class VoyagerApplicationScope @Inject constructor() {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
