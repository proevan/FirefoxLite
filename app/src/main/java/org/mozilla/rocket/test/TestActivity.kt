package org.mozilla.rocket.test

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import kotlinx.android.synthetic.main.activity_test.view_pager
import org.mozilla.focus.R

class TestActivity : FragmentActivity() {

    private lateinit var topSitesViewModel: TopSitesViewModel
    private lateinit var adapter: TopSitesPageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        topSitesViewModel = ViewModelProviders.of(this, TopSitesViewModelFactory.INSTANCE).get(TopSitesViewModel::class.java)


        setContentView(R.layout.activity_test)

        setUpRecyclerView()

        bindTopSites()
    }

    fun bindTopSites() {
        topSitesViewModel.run {
             sitePages.observe(this@TestActivity, Observer {
                adapter.setData(it)
            })
        }
    }

    private fun setUpRecyclerView() {
        adapter = TopSitesPageAdapter()
        view_pager.apply {
            adapter = this@TestActivity.adapter
        }
    }

    companion object {
        fun getStartIntent(context: Context) = Intent(context, TestActivity::class.java)
    }
}