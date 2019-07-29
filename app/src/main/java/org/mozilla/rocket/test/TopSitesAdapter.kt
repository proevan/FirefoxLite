package org.mozilla.rocket.test

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.os.StrictMode.ThreadPolicy.Builder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_top_site.content_image
import kotlinx.android.synthetic.main.item_top_site.text
import org.mozilla.focus.R
import org.mozilla.focus.utils.DimenUtils
import org.mozilla.icon.FavIconUtils
import org.mozilla.rocket.test.model.Site
import org.mozilla.strictmodeviolator.StrictModeViolation

class TopSitesAdapter : RecyclerView.Adapter<TopSitesAdapter.SiteViewHolder>() {

    private var data = mutableListOf<Site>()

    init {
        setHasStableIds(true)
    }

    fun setData(data: List<Site>) {
        this.data.clear()
        this.data.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SiteViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_top_site, parent, false)

        return SiteViewHolder(view)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: SiteViewHolder, position: Int) {
        holder.bind(data[position])
    }

    class SiteViewHolder(override val containerView: View) : RecyclerView.ViewHolder(containerView), LayoutContainer {
        fun bind(site: Site) {
            text.text = site.title

            // Tried AsyncTask and other simple offloading, the performance drops significantly.
            // FIXME: 9/21/18 by saving bitmap color, cause FaviconUtils.getDominantColor runs slow.
            // Favicon
            val favicon = StrictModeViolation.tempGrant(
                { obj: Builder -> obj.permitDiskReads() },
                { getFavicon(itemView.context, site) }
            )
            content_image.visibility = View.VISIBLE
            content_image.setImageBitmap(favicon)

            // Background color
            val backgroundColor = calculateBackgroundColor(favicon)
            ViewCompat.setBackgroundTintList(content_image, ColorStateList.valueOf(backgroundColor))
        }

        private fun getFavicon(context: Context, site: Site): Bitmap {
            val faviconUri = site.iconUri
            var favicon: Bitmap? = null
            if (faviconUri != null) {
                favicon = FavIconUtils.getBitmapFromUri(context, faviconUri)
            }

            return getBestFavicon(context.resources, site.url, favicon)
        }

        private fun getBestFavicon(res: Resources, url: String, favicon: Bitmap?): Bitmap {
            return if (favicon == null) {
                createFavicon(res, url, Color.WHITE)
            } else if (DimenUtils.iconTooBlurry(res, favicon.width)) {
                createFavicon(res, url, FavIconUtils.getDominantColor(favicon))
            } else {
                favicon
            }
        }

        private fun createFavicon(resources: Resources, url: String, backgroundColor: Int): Bitmap {
            return DimenUtils.getInitialBitmap(resources, FavIconUtils.getRepresentativeCharacter(url),
                    backgroundColor)
        }

        private fun calculateBackgroundColor(favicon: Bitmap): Int {
            val dominantColor = FavIconUtils.getDominantColor(favicon)
            val alpha = dominantColor and -0x1000000
            // Add 25% white to dominant Color
            val red = addWhiteToColorCode(dominantColor and 0x00FF0000 shr 16, 0.25f) shl 16
            val green = addWhiteToColorCode(dominantColor and 0x0000FF00 shr 8, 0.25f) shl 8
            val blue = addWhiteToColorCode(dominantColor and 0x000000FF, 0.25f)
            return alpha + red + green + blue
        }

        private fun addWhiteToColorCode(colorCode: Int, percentage: Float): Int {
            var result = (colorCode + 0xFF * percentage / 2).toInt()
            if (result > 0xFF) {
                result = 0xFF
            }
            return result
        }
    }
}