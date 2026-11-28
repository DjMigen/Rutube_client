package ru.rutubeclient.core.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Tiny manual ViewModel factory — avoids a DI framework for a handful of screens. */
inline fun <reified VM : ViewModel> vmFactory(
    crossinline create: () -> VM,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(VM::class.java)) {
            "Unknown ViewModel ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return create() as T
    }
}
