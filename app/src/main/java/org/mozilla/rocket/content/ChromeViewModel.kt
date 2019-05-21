package org.mozilla.rocket.content

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Transformations
import android.arch.lifecycle.ViewModel
import android.os.Parcel
import android.os.Parcelable
import org.mozilla.focus.persistence.BookmarkModel
import org.mozilla.focus.repository.BookmarkRepository
import org.mozilla.focus.utils.Settings
import org.mozilla.rocket.download.SingleLiveEvent
import org.mozilla.rocket.extension.invalidate

class ChromeViewModel(
    settings: Settings,
    bookmarkRepo: BookmarkRepository
) : ViewModel() {
    val isNightMode = MutableLiveData<Boolean>()
    val tabCount = MutableLiveData<TabCountChangedEvent>()
    val focusedSessionUrl = MutableLiveData<String>()
    var isCurrentTabInBookmark: LiveData<Boolean>
    val isRefreshing = MutableLiveData<Boolean>()
    val canGoBack = MutableLiveData<Boolean>()
    val canGoForward = MutableLiveData<Boolean>()

    val showTabTray = SingleLiveEvent<Unit>()
    val showMenu = SingleLiveEvent<Unit>()
    val showNewTab = SingleLiveEvent<Unit>()
    val showUrlInput = SingleLiveEvent<String?>()
    val doScreenshot = SingleLiveEvent<ScreenCaptureTelemetryData>()
    val pinShortcut = SingleLiveEvent<Unit>()
    val toggleBookmark = SingleLiveEvent<Unit>()
    // TODO: separate to startRefresh / stopLoading
    val refreshOrStop = SingleLiveEvent<Unit>()
    val share = SingleLiveEvent<Unit>()
    val goNext = SingleLiveEvent<Unit>()
    val showDownloadPanel = SingleLiveEvent<Unit>()

    init {
        isNightMode.value = settings.isNightModeEnable
        tabCount.value = TabCountChangedEvent(0, false)
        isRefreshing.value = false
        canGoBack.value = false
        canGoForward.value = false

        isCurrentTabInBookmark = Transformations.switchMap<String, List<BookmarkModel>>(focusedSessionUrl, bookmarkRepo::getBookmarksByUrl)
                .let { urlBookmarksLiveData ->
                    Transformations.map<List<BookmarkModel>, Boolean>(urlBookmarksLiveData) { it.isNotEmpty() }
                }
    }

    fun invalidate() {
        isNightMode.invalidate()
        tabCount.invalidate()
        isRefreshing.invalidate()
        canGoBack.invalidate()
        canGoForward.invalidate()
    }

    fun onNightModeChanged(isEnabled: Boolean) {
        if (isNightMode.value != isEnabled) {
            isNightMode.value = isEnabled
        }
    }

    @JvmOverloads
    fun onTabCountChanged(count: Int, needAnimation: Boolean = false) {
        val currentCount = tabCount.value?.count
        if (currentCount != count) {
            tabCount.value = TabCountChangedEvent(count, needAnimation)
        }
    }

    fun onFocusedSessionChanged(url: String?) {
        if (url != focusedSessionUrl.value) {
            focusedSessionUrl.value = url
        }
    }

    fun onPageLoadingStarted() {
        if (isRefreshing.value != true) {
            isRefreshing.value = true
        }
    }

    fun onPageLoadingStopped() {
        if (isRefreshing.value == true) {
            isRefreshing.value = false
        }
    }

    fun onNavigationStateChanged(canGoBack: Boolean, canGoForward: Boolean) {
        if (this.canGoBack.value != canGoBack) {
            this.canGoBack.value = canGoBack
        }
        if (this.canGoForward.value != canGoForward) {
            this.canGoForward.value = canGoForward
        }
    }

    data class TabCountChangedEvent(val count: Int, val withAnimation: Boolean)

    data class ScreenCaptureTelemetryData(val mode: String, val position: Int) : Parcelable {
        constructor(source: Parcel) : this(
                source.readString()!!,
                source.readInt()
        )

        override fun describeContents() = 0

        override fun writeToParcel(dest: Parcel, flags: Int) = with(dest) {
            writeString(mode)
            writeInt(position)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<ScreenCaptureTelemetryData> = object : Parcelable.Creator<ScreenCaptureTelemetryData> {
                override fun createFromParcel(source: Parcel): ScreenCaptureTelemetryData = ScreenCaptureTelemetryData(source)
                override fun newArray(size: Int): Array<ScreenCaptureTelemetryData?> = arrayOfNulls(size)
            }
        }
    }
}
