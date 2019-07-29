package org.mozilla.rocket.test

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_top_site_page.main_list
import org.mozilla.focus.R
import org.mozilla.rocket.test.model.SitePage

class TopSitesPageAdapter : RecyclerView.Adapter<TopSitesPageAdapter.SitePageViewHolder>() {

    private var data = mutableListOf<SitePage>()

    init {
        setHasStableIds(true)
    }

    fun setData(data: List<SitePage>) {
        this.data.clear()
        this.data.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SitePageViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_top_site_page, parent, false)

        return SitePageViewHolder(view)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: SitePageViewHolder, position: Int) {
        holder.bind(data[position])
    }

    class SitePageViewHolder(override val containerView: View) : RecyclerView.ViewHolder(containerView), LayoutContainer {
        private var adapter = TopSitesAdapter()

        init {
            main_list.adapter = adapter
        }

        fun bind(sitePage: SitePage) {
            adapter.setData(sitePage.sites)
        }
    }

}