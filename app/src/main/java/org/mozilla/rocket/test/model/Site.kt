package org.mozilla.rocket.test.model

sealed class Site(open val title: String, open val url: String, open val iconUri: String?) {
    data class FixedSite(
            override val title: String,
            override val url: String,
            override val iconUri: String
    ) : Site(title, iconUri, url)

    data class RemovableSite(
            override val title: String,
            override val url: String,
            override val iconUri: String
    ) : Site(title, iconUri, url)
}

data class SitePage(var sites: List<Site>)