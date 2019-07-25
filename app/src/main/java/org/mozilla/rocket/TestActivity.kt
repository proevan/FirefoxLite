package org.mozilla.rocket

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import androidx.fragment.app.FragmentActivity
import org.mozilla.focus.R

class TestActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)
        setContentView(R.layout.activity_test)
    }

    companion object {
        fun getStartIntent(context: Context) = Intent(context, TestActivity::class.java)
    }
}