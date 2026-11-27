package io.github.liuran001.mmliquidglass

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/** Local owners: native host activities need not be AndroidX ComponentActivity. */
internal class GlasmioOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val state = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry get() = state.savedStateRegistry
    init {
        state.performAttach()
        state.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }
    fun start() { registry.currentState = Lifecycle.State.RESUMED }
    fun stop() { registry.currentState = Lifecycle.State.DESTROYED }
}