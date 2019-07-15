package org.mozilla.rocket.privately.browse

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.fragment_private_browser.browser_bottom_bar
import mozilla.components.browser.session.Session
import mozilla.components.browser.session.SessionManager
import mozilla.components.concept.engine.EngineView
import mozilla.components.concept.engine.LifecycleObserver
import org.mozilla.focus.BuildConfig
import org.mozilla.focus.FocusApplication
import org.mozilla.focus.Inject
import org.mozilla.focus.R
import org.mozilla.focus.download.EnqueueDownloadTask
import org.mozilla.focus.locale.LocaleAwareFragment
import org.mozilla.focus.menu.WebContextMenu
import org.mozilla.focus.navigation.ScreenNavigator
import org.mozilla.focus.telemetry.TelemetryWrapper
import org.mozilla.focus.utils.ViewUtils
import org.mozilla.focus.web.BrowsingSession
import org.mozilla.focus.web.HttpAuthenticationDialogBuilder
import org.mozilla.focus.widget.AnimatedProgressBar
import org.mozilla.focus.widget.BackKeyHandleable
import org.mozilla.permissionhandler.PermissionHandle
import org.mozilla.permissionhandler.PermissionHandler
import org.mozilla.rocket.chrome.BottomBarItemAdapter
import org.mozilla.rocket.chrome.ChromeViewModel
import org.mozilla.rocket.content.app
import org.mozilla.rocket.content.view.BottomBar
import org.mozilla.rocket.extension.nonNullObserve
import org.mozilla.rocket.extension.switchFrom
import org.mozilla.rocket.tabs.TabView.FullscreenCallback
import org.mozilla.rocket.tabs.TabView.HitTarget
import org.mozilla.rocket.tabs.TabViewClient
import org.mozilla.rocket.tabs.TabViewEngineSession
import org.mozilla.rocket.tabs.web.Download
import org.mozilla.rocket.tabs.web.DownloadCallback
import org.mozilla.threadutils.ThreadUtils
import org.mozilla.urlutils.UrlUtils

private const val SITE_GLOBE = 0
private const val SITE_LOCK = 1
private const val ACTION_DOWNLOAD = 0

class BrowserFragment : LocaleAwareFragment(),
        ScreenNavigator.BrowserScreen,
        BackKeyHandleable {

    private val sessionManager: SessionManager by lazy {
        app().sessionManager
    }
    private lateinit var permissionHandler: PermissionHandler
    private lateinit var bottomBarItemAdapter: BottomBarItemAdapter
    private lateinit var chromeViewModel: ChromeViewModel

    private lateinit var browserContainer: ViewGroup
    private lateinit var videoContainer: ViewGroup
    private lateinit var tabViewSlot: ViewGroup
    private lateinit var engineView: EngineView
    private lateinit var displayUrlView: TextView
    private lateinit var progressView: AnimatedProgressBar
    private lateinit var siteIdentity: ImageView

    private lateinit var toolbarRoot: ViewGroup

    private lateinit var trackerPopup: TrackerPopup

    private var lastSession: Session? = null

    private var systemVisibility = ViewUtils.SYSTEM_UI_VISIBILITY_NONE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chromeViewModel = Inject.obtainChromeViewModel(activity)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_private_browser, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val fragmentActivity = activity
        if (fragmentActivity == null) {
            if (BuildConfig.DEBUG) {
                throw RuntimeException("No activity to use")
            }
        }
    }

    override fun onViewCreated(view: View, savedState: Bundle?) {
        super.onViewCreated(view, savedState)

        setupBottomBar(view)

        displayUrlView = view.findViewById(R.id.display_url)
        displayUrlView.setOnClickListener {
            chromeViewModel.showUrlInput.setValue(chromeViewModel.currentUrl.value)
        }

        siteIdentity = view.findViewById(R.id.site_identity)

        browserContainer = view.findViewById(R.id.browser_container)
        videoContainer = view.findViewById(R.id.video_container)
        tabViewSlot = view.findViewById(R.id.tab_view_slot)
        progressView = view.findViewById(R.id.progress)

        attachEngineView(tabViewSlot)

        initTrackerView(view)

        monitorTrackerBlocked { count -> updateTrackerBlockedCount(count) }

        view.findViewById<View>(R.id.appbar).setOnApplyWindowInsetsListener { v, insets ->
            (v.layoutParams as LinearLayout.LayoutParams).topMargin = insets.systemWindowInsetTop
            insets
        }

        toolbarRoot = view.findViewById(R.id.toolbar_root)

        sessionManager.register(sessionManagerObserver)
        sessionManager.selectedSession?.let {
            it.register(sessionObserver)
            lastSession = it
        }

        observeChromeAction()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        sessionManager.unregister(sessionManagerObserver)
        lastSession?.unregister(sessionObserver)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        permissionHandler = PermissionHandler(object : PermissionHandle {
            override fun doActionDirect(permission: String?, actionId: Int, params: Parcelable?) {

                this@BrowserFragment.context?.also {
                    val download = params as Download

                    if (PackageManager.PERMISSION_GRANTED ==
                            ContextCompat.checkSelfPermission(it, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    ) {
                        // We do have the permission to write to the external storage. Proceed with the download.
                        queueDownload(download)
                    }
                } ?: run {
                    Log.e("BrowserFragment.kt", "No context to use, abort callback onDownloadStart")
                }
            }

            fun actionDownloadGranted(parcelable: Parcelable?) {
                val download = parcelable as Download
                queueDownload(download)
            }

            override fun doActionGranted(permission: String?, actionId: Int, params: Parcelable?) {
                actionDownloadGranted(params)
            }

            override fun doActionSetting(permission: String?, actionId: Int, params: Parcelable?) {
                actionDownloadGranted(params)
            }

            override fun doActionNoPermission(
                permission: String?,
                actionId: Int,
                params: Parcelable?
            ) {
            }

            override fun makeAskAgainSnackBar(actionId: Int): Snackbar {
                activity?.also {
                    return PermissionHandler.makeAskAgainSnackBar(
                            this@BrowserFragment,
                            it.findViewById(R.id.container),
                            R.string.permission_toast_storage
                    )
                }
                throw IllegalStateException("No Activity to show Snackbar.")
            }

            override fun permissionDeniedToast(actionId: Int) {
                Toast.makeText(getContext(), R.string.permission_toast_storage_deny, Toast.LENGTH_LONG).show()
            }

            override fun requestPermissions(actionId: Int) {
                this@BrowserFragment.requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), actionId)
            }

            private fun queueDownload(download: Download?) {
                activity?.let { activity ->
                    download?.let {
                        EnqueueDownloadTask(activity, it, displayUrlView.text.toString()).execute()
                    }
                }
            }
        })
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        trackerPopup.dismiss()

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            toolbarRoot.visibility = View.GONE
            browser_bottom_bar.visibility = View.GONE
        } else {
            browser_bottom_bar.visibility = View.VISIBLE
            toolbarRoot.visibility = View.VISIBLE
        }
    }

    override fun applyLocale() {
        // We create and destroy a new WebView here to force the internal state of WebView to know
        // about the new language. See issue #666.
        val unneeded = WebView(context)
        unneeded.destroy()
    }

    override fun onBackPressed(): Boolean {
        if (sessionManager.selectedSession?.canGoBack == true) {
            goBack()
            return true
        }
        sessionManager.remove()
        ScreenNavigator.get(activity).popToHomeScreen(true)
        chromeViewModel.dropCurrentPage.call()
        return true
    }

    override fun getFragment(): Fragment {
        return this
    }

    override fun switchToTab(tabId: String?) {
        // Do nothing in private mode
    }

    override fun goForeground() {
        // Do nothing
    }

    override fun goBackground() {
        // Do nothing
    }

    override fun loadUrl(
        url: String,
        openNewTab: Boolean,
        isFromExternal: Boolean,
        onViewReadyCallback: Runnable?
    ) {
        if (url.isNotBlank()) {
            displayUrlView.text = url
            val selectedSession = sessionManager.selectedSession
            if (selectedSession == null) {
                val newSession = Session(url)
                sessionManager.add(newSession)
                engineView.render(sessionManager.getOrCreateEngineSession(newSession))
            } else {
                sessionManager.getOrCreateEngineSession(selectedSession).loadUrl(url)
            }

            ThreadUtils.postToMainThread(onViewReadyCallback)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        permissionHandler.onRequestPermissionsResult(context, requestCode, permissions, grantResults)
    }

    private fun goBack() = sessionManager.selectedSession?.let {
        sessionManager.getEngineSession()?.goBack()
    }

    private fun goForward() = sessionManager.selectedSession?.let {
        sessionManager.getEngineSession()?.goForward()
    }

    private fun stop() = sessionManager.selectedSession?.let {
        sessionManager.getEngineSession()?.stopLoading()
    }

    private fun reload() = sessionManager.selectedSession?.let {
        sessionManager.getEngineSession()?.reload()
    }

    private fun onTrackerButtonClicked() {
        view?.let { parentView -> trackerPopup.show(parentView) }
    }

    private fun onDeleteClicked() {
        sessionManager.removeSessions()
        chromeViewModel.dropCurrentPage.call()
        ScreenNavigator.get(activity).popToHomeScreen(true)
    }

    private fun attachEngineView(parentView: ViewGroup) {
        engineView = app().engine.createView(requireContext())
        lifecycle.addObserver(LifecycleObserver(engineView))
        parentView.addView(engineView.asView())
    }

    private fun setupBottomBar(rootView: View) {
        val bottomBar = rootView.findViewById<BottomBar>(R.id.browser_bottom_bar)
        bottomBar.setOnItemClickListener(object : BottomBar.OnItemClickListener {
            override fun onItemClick(type: Int, position: Int) {
                when (type) {
                    BottomBarItemAdapter.TYPE_SEARCH -> chromeViewModel.showUrlInput.setValue(chromeViewModel.currentUrl.value)
                    BottomBarItemAdapter.TYPE_PIN_SHORTCUT -> chromeViewModel.pinShortcut.call()
                    BottomBarItemAdapter.TYPE_REFRESH -> chromeViewModel.refreshOrStop.call()
                    BottomBarItemAdapter.TYPE_SHARE -> chromeViewModel.share.call()
                    BottomBarItemAdapter.TYPE_NEXT -> chromeViewModel.goNext.call()
                    BottomBarItemAdapter.TYPE_PRIVATE_HOME -> {
                        chromeViewModel.togglePrivateMode.call()
                        TelemetryWrapper.togglePrivateMode(false)
                    }
                    BottomBarItemAdapter.TYPE_DELETE -> onDeleteClicked()
                    BottomBarItemAdapter.TYPE_TRACKER -> onTrackerButtonClicked()
                    else -> throw IllegalArgumentException("Unhandled bottom bar item, type: $type")
                }
            }
        })
        bottomBarItemAdapter = BottomBarItemAdapter(bottomBar, BottomBarItemAdapter.Theme.PrivateMode)
        val bottomBarViewModel = Inject.obtainPrivateBottomBarViewModel(activity)
        bottomBarViewModel.items.nonNullObserve(this) {
            bottomBarItemAdapter.setItems(it)
            bottomBarItemAdapter.endPrivateHomeAnimation()
            bottomBarItemAdapter.setTrackerSwitch(isTurboModeEnabled(rootView.context))
        }

        chromeViewModel.isRefreshing.switchFrom(bottomBarViewModel.items)
                .observe(this, Observer { bottomBarItemAdapter.setRefreshing(it == true) })
        chromeViewModel.canGoForward.switchFrom(bottomBarViewModel.items)
                .observe(this, Observer { bottomBarItemAdapter.setCanGoForward(it == true) })
    }

    private fun initTrackerView(parentView: View) {
        trackerPopup = TrackerPopup(parentView.context)

        trackerPopup.onSwitchToggled = { enabled ->
            val appContext = (parentView.context.applicationContext as FocusApplication)
            appContext.settings.privateBrowsingSettings.setTurboMode(enabled)
            // TODO: Evan, uncomment this
//            sessionManager.focusSession?.engineSession?.tabView?.setContentBlockingEnabled(enabled)

            bottomBarItemAdapter.setTrackerSwitch(enabled)
            stop()
            reload()
        }
    }

    private fun isTurboModeEnabled(context: Context): Boolean {
        val appContext = (context.applicationContext as FocusApplication)
        return appContext.settings.privateBrowsingSettings.shouldUseTurboMode()
    }

    private fun monitorTrackerBlocked(onUpdate: (Int) -> Unit) {
        BrowsingSession.getInstance().blockedTrackerCount.observe(viewLifecycleOwner, Observer {
            val count = it ?: return@Observer
            onUpdate(count)
        })
    }

    private fun updateTrackerBlockedCount(count: Int) {
        bottomBarItemAdapter.setTrackerBadgeEnabled(count > 0)
        trackerPopup.blockedCount = count
    }

    private fun observeChromeAction() {
        chromeViewModel.refreshOrStop.observe(this, Observer {
            if (chromeViewModel.isRefreshing.value == true) {
                stop()
            } else {
                reload()
            }
        })
        chromeViewModel.goNext.observe(this, Observer {
            if (chromeViewModel.canGoForward.value == true) {
                goForward()
            }
        })
    }

    val sessionManagerObserver = object : SessionManager.Observer {
        override fun onAllSessionsRemoved() {
        }

        override fun onSessionAdded(session: Session) {
        }

        override fun onSessionRemoved(session: Session) {
            session.unregister(sessionObserver)
        }

        override fun onSessionSelected(session: Session) {
            lastSession?.unregister(sessionObserver)
            session.register(sessionObserver)
            lastSession = session
        }

        override fun onSessionsRestored() {
        }
    }

    val sessionObserver = object : Session.Observer {
        // TODO: Evan
    }

    class Observer(val fragment: BrowserFragment) : org.mozilla.rocket.tabs.SessionManager.Observer, org.mozilla.rocket.tabs.Session.Observer {
        override fun updateFailingUrl(url: String?, updateFromError: Boolean) {
            // do nothing, exist for interface compatibility only.
        }

        override fun handleExternalUrl(url: String?): Boolean {
            // do nothing, exist for interface compatibility only.
            return false
        }

        override fun onShowFileChooser(
            es: TabViewEngineSession,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: WebChromeClient.FileChooserParams?
        ): Boolean {
            // do nothing, exist for interface compatibility only.
            return false
        }

        var callback: FullscreenCallback? = null
        var session: org.mozilla.rocket.tabs.Session? = null

        override fun onSessionAdded(session: org.mozilla.rocket.tabs.Session, arguments: Bundle?) {
        }

        override fun onProgress(session: org.mozilla.rocket.tabs.Session, progress: Int) {
            fragment.progressView.progress = progress
        }

        override fun onTitleChanged(session: org.mozilla.rocket.tabs.Session, title: String?) {
            fragment.chromeViewModel.onFocusedTitleChanged(title)
            session.let {
                if (fragment.displayUrlView.text.toString() != it.url) {
                    fragment.displayUrlView.text = it.url
                }
            }
        }

        override fun onLongPress(session: org.mozilla.rocket.tabs.Session, hitTarget: HitTarget) {
            fragment.activity?.let {
                WebContextMenu.show(true,
                        it,
                        PrivateDownloadCallback(fragment, session.url),
                        hitTarget)
            }
        }

        override fun onEnterFullScreen(callback: FullscreenCallback, view: View?) {
            with(fragment) {
                browserContainer.visibility = View.INVISIBLE
                videoContainer.visibility = View.VISIBLE
                videoContainer.addView(view)

                // Switch to immersive mode: Hide system bars other UI controls
                systemVisibility = ViewUtils.switchToImmersiveMode(activity)
            }
        }

        override fun onExitFullScreen() {
            with(fragment) {
                browserContainer.visibility = View.VISIBLE
                videoContainer.visibility = View.INVISIBLE
                videoContainer.removeAllViews()

                if (systemVisibility != ViewUtils.SYSTEM_UI_VISIBILITY_NONE) {
                    ViewUtils.exitImmersiveMode(systemVisibility, activity)
                }
            }

            callback?.fullScreenExited()
            callback = null

            // WebView gets focus, but unable to open the keyboard after exit Fullscreen for Android 7.0+
            // We guess some component in WebView might lock focus
            // So when user touches the input text box on Webview, it will not trigger to open the keyboard
            // It may be a WebView bug.
            // The workaround is clearing WebView focus
            // The WebView will be normal when it gets focus again.
            // If android change behavior after, can remove this.
            session?.engineSession?.tabView?.let { if (it is WebView) it.clearFocus() }
        }

        override fun onUrlChanged(session: org.mozilla.rocket.tabs.Session, url: String?) {
            fragment.chromeViewModel.onFocusedUrlChanged(url)
            if (!UrlUtils.isInternalErrorURL(url)) {
                fragment.displayUrlView.text = url
            }
        }

        override fun onLoadingStateChanged(session: org.mozilla.rocket.tabs.Session, loading: Boolean) {
            if (loading) {
                fragment.chromeViewModel.onPageLoadingStarted()
            } else {
                fragment.chromeViewModel.onPageLoadingStopped()
            }
        }

        override fun onSecurityChanged(session: org.mozilla.rocket.tabs.Session, isSecure: Boolean) {
            val level = if (isSecure) SITE_LOCK else SITE_GLOBE
            fragment.siteIdentity.setImageLevel(level)
        }

        override fun onSessionCountChanged(count: Int) {
            fragment.chromeViewModel.onTabCountChanged(count)
            if (count == 0) {
                session?.unregister(this)
            } else {
                // TODO: Evan, uncomment this
//                session = fragment.sessionManager.focusSession
                session?.register(this)
            }
        }

        override fun onDownload(
            session: org.mozilla.rocket.tabs.Session,
            download: mozilla.components.browser.session.Download
        ): Boolean {
            val activity = fragment.activity
            if (activity == null || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                return false
            }

            val d = Download(
                    download.url,
                    download.fileName,
                    download.userAgent,
                    "",
                    download.contentType,
                    download.contentLength!!,
                    false
                    )
            fragment.permissionHandler.tryAction(
                    fragment,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    ACTION_DOWNLOAD,
                    d
                    )
            return true
        }

        override fun onHttpAuthRequest(
            callback: TabViewClient.HttpAuthCallback,
            host: String?,
            realm: String?
        ) {
            val builder = HttpAuthenticationDialogBuilder.Builder(fragment.activity, host, realm)
                    .setOkListener { _, _, username, password -> callback.proceed(username, password) }
                    .setCancelListener { callback.cancel() }
                    .build()

            builder.createDialog()
            builder.show()
        }

        override fun onNavigationStateChanged(session: org.mozilla.rocket.tabs.Session, canGoBack: Boolean, canGoForward: Boolean) {
            fragment.chromeViewModel.onNavigationStateChanged(canGoBack, canGoForward)
        }

        override fun onFocusChanged(session: org.mozilla.rocket.tabs.Session?, factor: org.mozilla.rocket.tabs.SessionManager.Factor) {
            fragment.chromeViewModel.onFocusedUrlChanged(session?.url)
            fragment.chromeViewModel.onFocusedTitleChanged(session?.title)
            if (session != null) {
                // TODO: Evan, uncomment this
//                val canGoBack = fragment.sessionManager.focusSession?.canGoBack ?: false
//                val canGoForward = fragment.sessionManager.focusSession?.canGoForward ?: false
//                fragment.chromeViewModel.onNavigationStateChanged(canGoBack, canGoForward)
            }
        }
    }

    class PrivateDownloadCallback(val fragment: BrowserFragment, val refererUrl: String?) : DownloadCallback {
        override fun onDownloadStart(download: Download) {
            fragment.activity?.let {
                if (!it.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    return
                }
            }

            fragment.permissionHandler.tryAction(
                    fragment,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    ACTION_DOWNLOAD,
                    download
                    )
        }
    }
}