package org.mozilla.rocket.test

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class TopSitesViewModelFactory private constructor() : ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TopSitesViewModel::class.java)) {
            return TopSitesViewModel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
    }

    companion object {
        @JvmStatic
        val INSTANCE: TopSitesViewModelFactory by lazy { TopSitesViewModelFactory() }
    }
}
