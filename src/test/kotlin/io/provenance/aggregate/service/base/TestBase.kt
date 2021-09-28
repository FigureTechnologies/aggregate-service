package io.provenance.aggregate.service.base

import io.provenance.aggregate.service.utils.Defaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
open class TestBase {

    val moshi = Defaults.moshi
    val templates = Defaults.templates

    val dispatcherProvider = TestDispatcherProvider()

    open fun setup() {
        Dispatchers.setMain(dispatcherProvider.dispatcher)
    }

    open fun tearDown() {
        Dispatchers.resetMain()
    }
}