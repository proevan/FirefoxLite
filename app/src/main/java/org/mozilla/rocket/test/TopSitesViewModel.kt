package org.mozilla.rocket.test

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.mozilla.focus.utils.TopSitesUtils.TOP_SITE_ASSET_PREFIX
import org.mozilla.rocket.test.model.Site.RemovableSite
import org.mozilla.rocket.test.model.SitePage

class TopSitesViewModel : ViewModel() {

    val sitePages = MutableLiveData<List<SitePage>>()

    init {
        initFakeSites()
    }

    private fun initFakeSites() {
        sitePages.value = listOf(
            SitePage(
                    listOf(
                            RemovableSite(title = "YouTube", url = "https://m.youtube.com/", iconUri = TOP_SITE_ASSET_PREFIX + "ic_youtube.png"),
                            RemovableSite(title = "YouTube", url = "https://m.youtube.com/", iconUri = TOP_SITE_ASSET_PREFIX + "ic_youtube.png"),
                            RemovableSite(title = "YouTube", url = "https://m.youtube.com/", iconUri = TOP_SITE_ASSET_PREFIX + "ic_youtube.png"),
                            RemovableSite(title = "YouTube", url = "https://m.youtube.com/", iconUri = TOP_SITE_ASSET_PREFIX + "ic_youtube.png"),
                            RemovableSite(title = "YouTube", url = "https://m.youtube.com/", iconUri = TOP_SITE_ASSET_PREFIX + "ic_youtube.png"),
                            RemovableSite(title = "YouTube", url = "https://m.youtube.com/", iconUri = TOP_SITE_ASSET_PREFIX + "ic_youtube.png"),
                            RemovableSite(title = "YouTube", url = "https://m.youtube.com/", iconUri = TOP_SITE_ASSET_PREFIX + "ic_youtube.png"),
                            RemovableSite(title = "YouTube", url = "https://m.youtube.com/", iconUri = TOP_SITE_ASSET_PREFIX + "ic_youtube.png")
                    )
            ),
            SitePage(
                    listOf(
                            RemovableSite(title = "YouTube", url = "https://m.youtube.com/", iconUri = TOP_SITE_ASSET_PREFIX + "ic_youtube.png"),
                            RemovableSite(title = "YouTube", url = "https://m.youtube.com/", iconUri = TOP_SITE_ASSET_PREFIX + "ic_youtube.png"),
                            RemovableSite(title = "YouTube", url = "https://m.youtube.com/", iconUri = TOP_SITE_ASSET_PREFIX + "ic_youtube.png"),
                            RemovableSite(title = "YouTube", url = "https://m.youtube.com/", iconUri = TOP_SITE_ASSET_PREFIX + "ic_youtube.png"),
                            RemovableSite(title = "YouTube", url = "https://m.youtube.com/", iconUri = TOP_SITE_ASSET_PREFIX + "ic_youtube.png"),
                            RemovableSite(title = "YouTube", url = "https://m.youtube.com/", iconUri = TOP_SITE_ASSET_PREFIX + "ic_youtube.png"),
                            RemovableSite(title = "YouTube", url = "https://m.youtube.com/", iconUri = TOP_SITE_ASSET_PREFIX + "ic_youtube.png"),
                            RemovableSite(title = "YouTube", url = "https://m.youtube.com/", iconUri = TOP_SITE_ASSET_PREFIX + "ic_youtube.png")
                    )
            )
        )
    }

}