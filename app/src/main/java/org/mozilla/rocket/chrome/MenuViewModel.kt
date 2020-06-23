package org.mozilla.rocket.chrome

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.mozilla.focus.utils.AppConfigWrapper
import org.mozilla.rocket.chrome.domain.ReadNewMenuItemsUseCase
import org.mozilla.rocket.chrome.domain.ShouldShowNewMenuItemHintUseCase

class MenuViewModel(
    shouldShowNewMenuItemHintUseCase: ShouldShowNewMenuItemHintUseCase,
    private val readNewMenuItemsUseCase: ReadNewMenuItemsUseCase
) : ViewModel() {
    val bottomItems = MutableLiveData<List<BottomBarItemAdapter.ItemData>>()
    val shouldShowNewMenuItemHint: LiveData<Boolean> = shouldShowNewMenuItemHintUseCase()

    init {
        refresh()
    }

    fun refresh() {
        val configuredBottomBarItems = getConfiguredBottomBarItems() ?: DEFAULT_MENU_BOTTOM_ITEMS
        bottomItems.value.let { currentValue ->
            if (configuredBottomBarItems != currentValue) {
                bottomItems.value = configuredBottomBarItems
            }
        }
    }

    private fun getConfiguredBottomBarItems(): List<BottomBarItemAdapter.ItemData>? = AppConfigWrapper.getMenuBottomBarItems()

    fun onNewMenuItemDisplayed() {
        readNewMenuItemsUseCase()
    }

    companion object {
        @JvmStatic
        val DEFAULT_MENU_BOTTOM_ITEMS: List<BottomBarItemAdapter.ItemData> = listOf(
            BottomBarItemAdapter.ItemData(BottomBarItemAdapter.TYPE_BACK),
            BottomBarItemAdapter.ItemData(BottomBarItemAdapter.TYPE_NEXT),
            BottomBarItemAdapter.ItemData(BottomBarItemAdapter.TYPE_BOOKMARK),
            BottomBarItemAdapter.ItemData(BottomBarItemAdapter.TYPE_CAPTURE),
            BottomBarItemAdapter.ItemData(BottomBarItemAdapter.TYPE_SHARE)
        )
    }
}
