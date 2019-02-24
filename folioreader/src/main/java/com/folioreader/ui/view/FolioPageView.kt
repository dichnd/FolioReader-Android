package com.folioreader.ui.view

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Parcelable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.webkit.*
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.folioreader.Config
import com.folioreader.FolioReader
import com.folioreader.R
import com.folioreader.mediaoverlay.MediaController
import com.folioreader.mediaoverlay.MediaControllerCallbacks
import com.folioreader.model.HighLight
import com.folioreader.model.HighlightImpl
import com.folioreader.model.event.*
import com.folioreader.model.locators.ReadLocator
import com.folioreader.model.locators.SearchLocator
import com.folioreader.model.sqlite.HighLightTable
import com.folioreader.ui.base.FolioBookHolder
import com.folioreader.ui.activity.FolioActivityCallback
import com.folioreader.ui.base.HtmlTask
import com.folioreader.ui.base.HtmlTaskCallback
import com.folioreader.ui.base.HtmlUtil
import com.folioreader.ui.view.FolioWebView
import com.folioreader.ui.view.LoadingView
import com.folioreader.ui.view.VerticalSeekbar
import com.folioreader.ui.view.WebViewPager
import com.folioreader.util.AppUtil
import com.folioreader.util.HighlightUtil
import com.folioreader.util.UiUtil
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.readium.r2.shared.Link
import org.readium.r2.shared.Locations
import java.util.*
import java.util.regex.Pattern

/**
 * Copy from FolioPageFragment on 2/2019
 */
class FolioPageView : FrameLayout,
    HtmlTaskCallback, MediaControllerCallbacks, FolioWebView.SeekBarListener, FolioBookHolder {

    companion object {

        @JvmField
        val LOG_TAG: String = FolioPageView::class.java.simpleName

        private const val BUNDLE_SPINE_INDEX = "BUNDLE_SPINE_INDEX"
        private const val BUNDLE_BOOK_TITLE = "BUNDLE_BOOK_TITLE"
        private const val BUNDLE_SPINE_ITEM = "BUNDLE_SPINE_ITEM"
        private const val BUNDLE_READ_LOCATOR_CONFIG_CHANGE = "BUNDLE_READ_LOCATOR_CONFIG_CHANGE"
        const val BUNDLE_SEARCH_LOCATOR = "BUNDLE_SEARCH_LOCATOR"

        @JvmStatic
        fun newInstance(context: Context,
                        spineIndex: Int, bookTitle: String, spineRef: Link, bookId: String,
                        cb: FolioActivityCallback): FolioPageView {
            val args = Bundle()
            args.putInt(BUNDLE_SPINE_INDEX, spineIndex)
            args.putString(BUNDLE_BOOK_TITLE, bookTitle)
            args.putString(FolioReader.EXTRA_BOOK_ID, bookId)
            args.putSerializable(BUNDLE_SPINE_ITEM, spineRef)
            return FolioPageView(context, args, cb)
        }
    }

// TODO impl
//    constructor(context: Context) : super(context) { initView() }
//    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { initView() }
    constructor(context: Context, arguments: Bundle, cb: FolioActivityCallback) : super(context) {
        this.savedInstanceState = arguments
        this.mActivityCallback = cb
        initView()
    }

    private lateinit var uiHandler: Handler
    private var mHtmlString: String? = null
    private val hasMediaOverlay = false
    private var mAnchorId: String? = null
    private var rangy = ""
    private var highlightId: String? = null
    private var rangyToScroll: String? = null

    private var lastReadLocator: ReadLocator? = null
    private var outState: Bundle? = null
    private var savedInstanceState: Bundle? = null

    private var mRootView: View? = null

    private var loadingView: LoadingView? = null
    private var mScrollSeekbar: VerticalSeekbar? = null
    var mWebview: FolioWebView? = null
    private var webViewPager: WebViewPager? = null
    private var mPagesLeftTextView: TextView? = null
    private var mMinutesLeftTextView: TextView? = null
    private var mActivityCallback: FolioActivityCallback? = null

    private var mTotalMinutes: Int = 0
    private var mFadeInAnimation: Animation? = null
    private var mFadeOutAnimation: Animation? = null

    lateinit var spineItem: Link
    private var spineIndex = -1
    private var mBookTitle: String? = null
    private var mIsPageReloaded: Boolean = false

    private var highlightStyle: String? = null

    private var mediaController: MediaController? = null
    private var mConfig: Config? = null
    private var mBookId: String? = null
    override var searchLocatorVisible: SearchLocator? = null

    private lateinit var chapterUrl: Uri

    private var mIsPageLoaded = false
    private var mMarkerInfo: String? = null
    private var mHighlightToShow: String? = null
    private var mIsLoadedMarker = false

    override val pageName: String
        get() = mBookTitle + "$" + spineItem.href

    private val isCurrentPage: Boolean get() = spineIndex == mActivityCallback?.currentChapterIndex
//    private val isCurrentFragment: Boolean
//        get() {
//            return isAdded && mActivityCallback!!.currentChapterIndex == spineIndex
//        }

    private val arguments: Bundle get() = savedInstanceState!!

    fun initView() {

        uiHandler = Handler()

//        if (activity is FolioActivityCallback)
//            mActivityCallback = activity as FolioActivityCallback?

        EventBus.getDefault().register(this)

        spineIndex = arguments.getInt(BUNDLE_SPINE_INDEX)
        mBookTitle = arguments.getString(BUNDLE_BOOK_TITLE)
        spineItem = arguments.getSerializable(BUNDLE_SPINE_ITEM) as Link
        mBookId = arguments.getString(FolioReader.EXTRA_BOOK_ID)

        chapterUrl = Uri.parse(mActivityCallback?.streamerUrl + spineItem.href!!.substring(1))

        searchLocatorVisible = savedInstanceState?.getParcelable(BUNDLE_SEARCH_LOCATOR)
        Log.d(LOG_TAG, "initView $spineIndex")

//        if (spineItem != null) {
//            // SMIL Parsing not yet implemented in r2-streamer-kotlin
//            //if (spineItem.getProperties().contains("media-overlay")) {
//            //    mediaController = new MediaController(getActivity(), MediaController.MediaType.SMIL, this);
//            //    hasMediaOverlay = true;
//            //} else {
//            mediaController = MediaController(activity, MediaController.MediaType.TTS, this)
//            mediaController!!.setTextToSpeech(activity)
//            //}
//        }
        highlightStyle = HighlightImpl.HighlightStyle.classForStyle(HighlightImpl.HighlightStyle.Normal)
        mRootView = inflate(context, R.layout.folio_page_fragment, this)
        mPagesLeftTextView = mRootView!!.findViewById<View>(R.id.pagesLeft) as TextView
        mMinutesLeftTextView = mRootView!!.findViewById<View>(R.id.minutesLeft) as TextView

        mConfig = AppUtil.getSavedConfig(context)

        loadingView = mRootView!!.findViewById(R.id.loadingView)
        initSeekbar()
        initAnimations()
        initWebView()
        updatePagesLeftTextBg()
    }

    /**
     * [EVENT BUS FUNCTION]
     * Function triggered from [MediaControllerFragment.initListeners] when pause/play
     * button is clicked
     *
     * @param event of type [MediaOverlayPlayPauseEvent] contains if paused/played
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun pauseButtonClicked(event: MediaOverlayPlayPauseEvent) {
//        if (isAdded && spineItem!!.href == event.href) {
//            mediaController!!.stateChanged(event)
//        }
    }

    /**
     * [EVENT BUS FUNCTION]
     * Function triggered from [MediaControllerFragment.initListeners] when speed
     * change buttons are clicked
     *
     * @param event of type [MediaOverlaySpeedEvent] contains selected speed
     * type HALF,ONE,ONE_HALF and TWO.
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun speedChanged(event: MediaOverlaySpeedEvent) {
        if (mediaController != null)
            mediaController!!.setSpeed(event.speed)
    }

    /**
     * [EVENT BUS FUNCTION]
     * Function triggered from [MediaControllerFragment.initListeners] when new
     * style is selected on button click.
     *
     * @param event of type [MediaOverlaySpeedEvent] contains selected style
     * of type DEFAULT,UNDERLINE and BACKGROUND.
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun styleChanged(event: MediaOverlayHighlightStyleEvent) {
        if (isShown) {
            when (event.style) {
                MediaOverlayHighlightStyleEvent.Style.DEFAULT -> highlightStyle =
                        HighlightImpl.HighlightStyle.classForStyle(HighlightImpl.HighlightStyle.Normal)
                MediaOverlayHighlightStyleEvent.Style.UNDERLINE -> highlightStyle =
                        HighlightImpl.HighlightStyle.classForStyle(HighlightImpl.HighlightStyle.DottetUnderline)
                MediaOverlayHighlightStyleEvent.Style.BACKGROUND -> highlightStyle =
                        HighlightImpl.HighlightStyle.classForStyle(HighlightImpl.HighlightStyle.TextColor)
            }
            mWebview!!.loadUrl(String.format(getString(R.string.setmediaoverlaystyle), highlightStyle))
        }
    }

    /**
     * [EVENT BUS FUNCTION]
     * Function triggered when any EBook configuration is changed.
     *
     * @param reloadDataEvent empty POJO.
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun reload(reloadDataEvent: ReloadDataEvent) {

        if (isCurrentPage)
            getLastReadLocator()

        if (isShown) {
            mWebview!!.dismissPopupWindow()
            mWebview!!.initViewTextSelection()
            loadingView!!.updateTheme()
            loadingView!!.show()
            mIsPageReloaded = true
            setHtml(true)
            updatePagesLeftTextBg()
        }
    }

    /**
     * [EVENT BUS FUNCTION]
     *
     *
     * Function triggered when highlight is deleted and page is needed to
     * be updated.
     *
     * @param event empty POJO.
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun updateHighlight(event: UpdateHighlightEvent) {
//        if (isShown) {
//            this.rangy = HighlightUtil.generateRangyString(pageName)
//            loadRangy(this.rangy)
//        }
    }

    fun scrollToAnchorId(href: String) {

        if (!TextUtils.isEmpty(href) && href.indexOf('#') != -1) {
            mAnchorId = href.substring(href.lastIndexOf('#') + 1)
            if (loadingView != null && loadingView!!.visibility != View.VISIBLE) {
                loadingView!!.show()
                mWebview!!.loadUrl(String.format(getString(R.string.go_to_anchor), mAnchorId))
                mAnchorId = null
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun resetCurrentIndex(resetIndex: RewindIndexEvent) {
        if (isCurrentPage) {
            mWebview!!.loadUrl("javascript:rewindCurrentIndex()")
        }
    }

    override fun onReceiveHtml(html: String) {
        if (isShown) {
            mHtmlString = html
            setHtml(false)
        }
    }

    private fun setHtml(reloaded: Boolean) {
        if (spineItem != null) {
            /*if (!reloaded && spineItem.properties.contains("media-overlay")) {
                mediaController.setSMILItems(SMILParser.parseSMIL(mHtmlString));
                mediaController.setUpMediaPlayer(spineItem.mediaOverlay, spineItem.mediaOverlay.getAudioPath(spineItem.href), mBookTitle);
            }*/
            mConfig = AppUtil.getSavedConfig(context)

            val href = spineItem.href
            var path = ""
            val forwardSlashLastIndex = href!!.lastIndexOf('/')
            if (forwardSlashLastIndex != -1) {
                path = href.substring(1, forwardSlashLastIndex + 1)
            }

            val mimeType: String =
                if (spineItem.typeLink!!.equals(getString(R.string.xhtml_mime_type), true)) {
                    getString(R.string.xhtml_mime_type)
                } else {
                    getString(R.string.html_mime_type)
                }

            uiHandler.post {
                mWebview!!.loadDataWithBaseURL(
                    mActivityCallback?.streamerUrl + path,
                    HtmlUtil.getHtmlContent(context!!, mHtmlString, mConfig!!),
                    mimeType,
                    "UTF-8", null
                )
            }
        }
    }

    fun scrollToLast() {

        val isPageLoading = loadingView == null || loadingView!!.visibility == View.VISIBLE
        Log.v(LOG_TAG, "-> scrollToLast -> isPageLoading = $isPageLoading")

        if (!isPageLoading) {
            loadingView!!.show()
            mWebview!!.loadUrl("javascript:scrollToLast()")
        }
    }

    fun scrollToFirst() {

        val isPageLoading = loadingView == null || loadingView!!.visibility == View.VISIBLE
        Log.v(LOG_TAG, "-> scrollToFirst -> isPageLoading = $isPageLoading")

        if (!isPageLoading) {
            loadingView!!.show()
            mWebview!!.loadUrl("javascript:scrollToFirst()")
        }
    }

    @SuppressLint("JavascriptInterface", "SetJavaScriptEnabled")
    private fun initWebView() {
        WebView.setWebContentsDebuggingEnabled(true)

        val webViewLayout = mRootView!!.findViewById<FrameLayout>(R.id.webViewLayout)
        mWebview = webViewLayout.findViewById(R.id.folioWebView)
        mWebview!!.setFolioBookHolder(this)
        webViewPager = webViewLayout.findViewById(R.id.webViewPager)

//        if (activity is FolioActivityCallback)
            mWebview!!.setFolioActivityCallback(mActivityCallback!!)

        setupScrollBar()
        mWebview!!.addOnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val height = Math.floor((mWebview!!.contentHeight * mWebview!!.scale).toDouble()).toInt()
            val webViewHeight = mWebview!!.measuredHeight
            mScrollSeekbar!!.maximum = height - webViewHeight
        }

        mWebview!!.settings.javaScriptEnabled = true
        mWebview!!.isVerticalScrollBarEnabled = false
        mWebview!!.settings.allowFileAccess = true

        mWebview!!.isHorizontalScrollBarEnabled = false

        mWebview!!.addJavascriptInterface(this, "Highlight")
        mWebview!!.addJavascriptInterface(this, "FolioPageFragment")
        mWebview!!.addJavascriptInterface(webViewPager, "WebViewPager")
        mWebview!!.addJavascriptInterface(loadingView, "LoadingView")
        mWebview!!.addJavascriptInterface(mWebview, "FolioWebView")

        mWebview!!.setScrollListener(object : FolioWebView.ScrollListener {
            override fun onScrollChange(percent: Int) {

                mScrollSeekbar!!.setProgressAndThumb(percent)
                updatePagesLeftText(percent)
            }
        })

        mWebview!!.webViewClient = webViewClient
        mWebview!!.webChromeClient = webChromeClient

        mWebview!!.settings.defaultTextEncodingName = "utf-8"
        HtmlTask(this).execute(chapterUrl.toString())
    }

    private val webViewClient = object : WebViewClient() {

        override fun onPageFinished(view: WebView, url: String) {
            Log.d(LOG_TAG, "onPageFinished")
            mIsPageLoaded = true // TODO do we need reset mIsPageLoaded = false in onPageStarted ?
            mWebview!!.loadUrl("javascript:checkCompatMode()")
//            mWebview!!.loadUrl("javascript:alert(getReadingTime())")

            if (mActivityCallback!!.direction == Config.Direction.HORIZONTAL)
                mWebview!!.loadUrl("javascript:initHorizontalDirection()")

            view.loadUrl(
                String.format(
                    getString(R.string.setmediaoverlaystyle),
                    HighlightImpl.HighlightStyle.classForStyle(
                        HighlightImpl.HighlightStyle.Normal
                    )
                )
            )

            // load highlight from api instead of from local
//            val rangy = HighlightUtil.generateRangyString(pageName)
//            this@FolioPageView.rangy = rangy
//            if (!rangy.isEmpty())
//                loadRangy(rangy)
            mHighlightToShow?.let { hl ->
                val infos = hl.split("@@".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                showHighlight(infos[0], infos[1])
                mHighlightToShow = null
            }

            mMarkerInfo?.let { m ->
                val infos = m.split("@@".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                loadMarker(infos[0], infos[1])
                mMarkerInfo = null
            }

            if (mIsPageReloaded) {

                if (searchLocatorVisible != null) {
                    val callHighlightSearchLocator = String.format(
                        getString(R.string.callHighlightSearchLocator),
                        searchLocatorVisible?.locations?.cfi
                    )
                    mWebview!!.loadUrl(callHighlightSearchLocator)

                } else if (isCurrentPage) {
                    val cfi = lastReadLocator!!.locations.cfi
                    mWebview!!.loadUrl(String.format(getString(R.string.callScrollToCfi), cfi))

                } else {
                    if (spineIndex == mActivityCallback!!.currentChapterIndex - 1) {
                        // Scroll to last, the page before current page
                        mWebview!!.loadUrl("javascript:scrollToLast()")
                    } else {
                        // Make loading view invisible for all other fragments
                        loadingView!!.hide()
                    }
                }

                mIsPageReloaded = false

            } else if (!TextUtils.isEmpty(mAnchorId)) {
                mWebview!!.loadUrl(String.format(getString(R.string.go_to_anchor), mAnchorId))
                mAnchorId = null

            } else if (!TextUtils.isEmpty(highlightId)) {
                mWebview!!.loadUrl(String.format(getString(R.string.go_to_highlight), highlightId))
                highlightId = null

            } else if (!TextUtils.isEmpty(rangyToScroll)) {
                mWebview!!.loadUrl(
                    String.format(
                        "javascript:if(typeof ssReader !== \"undefined\"){ssReader.goToRangy('%s');}",
                        rangyToScroll
                    )
                )
                rangyToScroll = null

            } else if (searchLocatorVisible != null) {
                val callHighlightSearchLocator = String.format(
                    getString(R.string.callHighlightSearchLocator),
                    searchLocatorVisible?.locations?.cfi
                )
                mWebview!!.loadUrl(callHighlightSearchLocator)

            } else if (isCurrentPage) {

                val readLocator: ReadLocator?
                // FIXME dich only get readLocator from mActivityCallback
                if (savedInstanceState == null) {
                    Log.v(LOG_TAG, "-> onPageFinished -> took from getEntryReadLocator")
                    readLocator = mActivityCallback!!.entryReadLocator
                } else {
                    Log.v(LOG_TAG, "-> onPageFinished -> took from bundle")
                    readLocator = savedInstanceState!!.getParcelable(BUNDLE_READ_LOCATOR_CONFIG_CHANGE)
                    savedInstanceState!!.remove(BUNDLE_READ_LOCATOR_CONFIG_CHANGE)
                }

                if (readLocator != null) {
                    val cfi = readLocator.locations.cfi
                    Log.v(LOG_TAG, "-> onPageFinished -> readLocator -> " + cfi!!)
                    mWebview!!.loadUrl(String.format(getString(R.string.callScrollToCfi), cfi))
                } else {
                    loadingView!!.hide()
                }

            } else if (spineIndex == mActivityCallback!!.currentChapterIndex - 1) {
                // Scroll to last, the page before current page
                mWebview!!.loadUrl("javascript:scrollToLast()")
            } else if (spineIndex == mActivityCallback!!.currentChapterIndex + 1) { // FIXME @dich WHY?
                mWebview!!.loadUrl("javascript:scrollToFirst()")
            } else {
                // Make loading view invisible for all other fragments
                loadingView!!.hide()
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {

            if (url.isEmpty())
                return true

            val urlOfEpub = mActivityCallback!!.goToChapter(url)
            if (!urlOfEpub) {
                // Otherwise, give the default behavior (open in browser)
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            }

            return true
        }

        // prevent favicon.ico to be loaded automatically
        override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? {
            if (url.toLowerCase().contains("/favicon.ico")) {
                try {
                    return WebResourceResponse("image/png", null, null)
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "shouldInterceptRequest failed", e)
                }

            }
            return null
        }

        // prevent favicon.ico to be loaded automatically
        @SuppressLint("NewApi")
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (!request.isForMainFrame
                && request.url.path != null
                && request.url.path!!.endsWith("/favicon.ico")
            ) {
                try {
                    return WebResourceResponse("image/png", null, null)
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "shouldInterceptRequest failed", e)
                }

            }
            return null
        }
    }

    private val webChromeClient = object : WebChromeClient() {

        override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
            super.onConsoleMessage(cm)
            val msg = cm.message() + " [" + cm.sourceId() + ":" + cm.lineNumber() + "]"
            return FolioWebView.onWebViewConsoleMessage(cm, "WebViewConsole", msg)
        }

        override fun onProgressChanged(view: WebView, progress: Int) {}

        override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {

            // Check if this `if` block can be dropped?
            if (!this@FolioPageView.isCurrentPage)
                return true

            if (TextUtils.isDigitsOnly(message)) {
                try {
                    mTotalMinutes = Integer.parseInt(message)
                } catch (e: NumberFormatException) {
                    mTotalMinutes = 0
                }

            } else {
                // to handle TTS playback when highlight is deleted.
                val p = Pattern.compile("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")
                if (!p.matcher(message).matches() && message != "undefined") {
                    mediaController!!.speakAudio(message)
                }
            }

            result.confirm()
            return true
        }
    }

    fun onStop() {
//        super.onStop()
        Log.v(LOG_TAG, "-> onStop -> " + spineItem.href + " -> " + isCurrentPage)

//        mediaController!!.stop()
        //TODO save last media overlay item

        if (isCurrentPage)
            getLastReadLocator()
    }

    fun getLastReadLocator(): ReadLocator? {
        Log.v(LOG_TAG, "-> getLastReadLocator -> " + spineItem.href!!)
        try {
            synchronized(this) {
                mWebview!!.loadUrl(getString(R.string.callComputeLastReadCfi))
                (this as java.lang.Object).wait(5000)
            }
        } catch (e: InterruptedException) {
            Log.e(LOG_TAG, "-> ", e)
        }

        return lastReadLocator
    }

    @JavascriptInterface
    fun storeLastReadCfi(cfi: String) {

        synchronized(this) {
            var href = spineItem.href
            if (href == null) href = ""
            val created = Date().time
            val locations = Locations()
            locations.cfi = cfi
            lastReadLocator = ReadLocator(mBookId!!, href, created, locations)

            val intent = Intent(FolioReader.ACTION_SAVE_READ_LOCATOR)
            intent.putExtra(FolioReader.EXTRA_READ_LOCATOR, lastReadLocator as Parcelable?)
            LocalBroadcastManager.getInstance(context!!).sendBroadcast(intent)

            (this as java.lang.Object).notify()
        }
    }

    @JavascriptInterface
    fun setHorizontalPageCount(horizontalPageCount: Int) {
        Log.v(
            LOG_TAG, "-> setHorizontalPageCount = " + horizontalPageCount
                    + " -> " + spineItem.href
        )

        mWebview!!.setHorizontalPageCount(horizontalPageCount)
    }

    override fun loadRangy(rangy: String) {
        mWebview!!.loadUrl(
            String.format(
                "javascript:if(typeof ssReader !== \"undefined\"){ssReader.setHighlights('%s');}",
                rangy
            )
        )
    }

    override fun loadMarker(rangy: String, globalIds: String) {
        mIsLoadedMarker = true
        if (mIsPageLoaded)
            mWebview?.loadUrl(
                String.format(
                    "javascript:if(typeof ssReader !== \"undefined\"){ssReader.markHighlight('%s', '%s');}",
                    rangy,
                    globalIds
                )
            )
        else
            mMarkerInfo = "$rangy@@$globalIds"
    }

    fun showHighlight(rangy: String, globalIds: String) {
        if (mIsPageLoaded) {
            this.rangy = rangy
            mWebview?.loadUrl(
                String.format(
                    "javascript:if(typeof ssReader !== \"undefined\"){ssReader.setHighlights('%s', '%s');}",
                    rangy,
                    globalIds
                )
            )
        } else
            this.mHighlightToShow = "$rangy@@$globalIds"
    }

    fun updateHighlightStyle(highlightId: String, style: HighlightImpl.HighlightStyle) {
        mWebview?.loadUrl(
            String.format(
                "javascript:if(typeof ssReader !== \"undefined\"){ssReader.updateHighlightStyle('%s', '%s');}",
                highlightId, HighlightImpl.HighlightStyle.classForStyle(style)
            )
        )
    }

    fun updateHighlightGlobalId(highlightId: String, globalId: String) {
        mWebview?.loadUrl(
            String.format(
                "javascript:if(typeof ssReader !== \"undefined\"){ssReader.updateHighlightGlobalId('%s', '%s');}",
                highlightId, globalId
            )
        )
    }


    private fun setupScrollBar() {
        UiUtil.setColorIntToDrawable(mConfig!!.themeColor, seekBarDrawable)
        val thumbDrawable = ContextCompat.getDrawable(context, R.drawable.icons_sroll)
        UiUtil.setColorIntToDrawable(mConfig!!.themeColor, thumbDrawable!!)
        mScrollSeekbar!!.thumb = thumbDrawable
    }

    private fun initSeekbar() {
        mScrollSeekbar = mRootView!!.findViewById<View>(R.id.scrollSeekbar) as VerticalSeekbar
        seekBarDrawable
            ?.setColorFilter(
                resources
                    .getColor(R.color.default_theme_accent_color),
                PorterDuff.Mode.SRC_IN
            )
    }

    private val seekBarDrawable: Drawable? get() {
        return if (mScrollSeekbar?.progressDrawable != null)
            mScrollSeekbar?.progressDrawable
        else
            mScrollSeekbar?.indeterminateDrawable
    }

    private fun updatePagesLeftTextBg() {

        if (mConfig!!.isNightMode) {
            mRootView!!.findViewById<View>(R.id.indicatorLayout)
                .setBackgroundColor(Color.parseColor("#131313"))
        } else {
            mRootView!!.findViewById<View>(R.id.indicatorLayout)
                .setBackgroundColor(Color.WHITE)
        }
    }

    private fun updatePagesLeftText(scrollY: Int) {
        try {
            val currentPage = (Math.ceil(scrollY.toDouble() / mWebview!!.webViewHeight) + 1).toInt()
            val totalPages = Math.ceil(mWebview!!.contentHeightVal.toDouble() / mWebview!!.webViewHeight).toInt()
            val pagesRemaining = totalPages - currentPage
            val pagesRemainingStrFormat = if (pagesRemaining > 1)
                getString(R.string.pages_left)
            else
                getString(R.string.page_left)
            val pagesRemainingStr = String.format(
                Locale.US,
                pagesRemainingStrFormat, pagesRemaining
            )

            val minutesRemaining = Math.ceil((pagesRemaining * mTotalMinutes).toDouble() / totalPages).toInt()
            val minutesRemainingStr: String
            if (minutesRemaining > 1) {
                minutesRemainingStr = String.format(
                    Locale.US, getString(R.string.minutes_left),
                    minutesRemaining
                )
            } else if (minutesRemaining == 1) {
                minutesRemainingStr = String.format(
                    Locale.US, getString(R.string.minute_left),
                    minutesRemaining
                )
            } else {
                minutesRemainingStr = getString(R.string.less_than_minute)
            }

            mMinutesLeftTextView!!.text = minutesRemainingStr
            mPagesLeftTextView!!.text = pagesRemainingStr
        } catch (exp: java.lang.ArithmeticException) {
            Log.e("divide error", exp.toString())
        } catch (exp: IllegalStateException) {
            Log.e("divide error", exp.toString())
        }

    }

    private fun initAnimations() {
        mFadeInAnimation = AnimationUtils.loadAnimation(context, R.anim.fadein)
        mFadeInAnimation!!.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                mScrollSeekbar!!.visibility = View.VISIBLE
            }

            override fun onAnimationEnd(animation: Animation) {
                fadeOutSeekBarIfVisible()
            }

            override fun onAnimationRepeat(animation: Animation) {

            }
        })
        mFadeOutAnimation = AnimationUtils.loadAnimation(context, R.anim.fadeout)
        mFadeOutAnimation!!.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {

            }

            override fun onAnimationEnd(animation: Animation) {
                mScrollSeekbar!!.visibility = View.INVISIBLE
            }

            override fun onAnimationRepeat(animation: Animation) {

            }
        })
    }

    override fun fadeInSeekBarIfInvisible() {
        if (mScrollSeekbar!!.visibility == View.INVISIBLE || mScrollSeekbar!!.visibility == View.GONE) {
            mScrollSeekbar!!.startAnimation(mFadeInAnimation)
        }
    }

    fun fadeOutSeekBarIfVisible() {
        if (mScrollSeekbar!!.visibility == View.VISIBLE) {
            mScrollSeekbar!!.startAnimation(mFadeOutAnimation)
        }
    }

//    override fun onDestroyView() {
//        mFadeInAnimation!!.setAnimationListener(null)
//        mFadeOutAnimation!!.setAnimationListener(null)
//        EventBus.getDefault().unregister(this)
//        super.onDestroyView()
//    }
//
//    /**
//     * If called, this method will occur after onStop() for applications targeting platforms
//     * starting with Build.VERSION_CODES.P. For applications targeting earlier platform versions
//     * this method will occur before onStop() and there are no guarantees about whether it will
//     * occur before or after onPause()
//     *
//     * @see Activity.onSaveInstanceState
//     */
//    override fun onSaveInstanceState(outState: Bundle) {
//        super.onSaveInstanceState(outState)
//        Log.v(LOG_TAG, "-> onSaveInstanceState -> ${spineItem.href}")
//
//        this.outState = outState
//        outState.putParcelable(BUNDLE_SEARCH_LOCATOR, searchLocatorVisible)
//    }

    override val currentHref: String?
        get() = spineItem.href

    override fun highlight(style: HighlightImpl.HighlightStyle, isAlreadyCreated: Boolean) {
        if (!isAlreadyCreated) {
            mWebview!!.loadUrl(
                String.format(
                    "javascript:if(typeof ssReader !== \"undefined\"){ssReader.highlightSelection('%s');}",
                    HighlightImpl.HighlightStyle.classForStyle(style)
                )
            )
        } else {
            mWebview!!.loadUrl(
                String.format(
                    "javascript:setHighlightStyle('%s')",
                    HighlightImpl.HighlightStyle.classForStyle(style)
                )
            )
        }
    }

    override fun resetCurrentIndex() {
//        if (isCurrentFragment) // FIXME @dich why remove this check?
            mWebview!!.loadUrl("javascript:rewindCurrentIndex()")
    }

    @JavascriptInterface
    fun onReceiveHighlights(html: String?) {
        if (html != null) {
            rangy = HighlightUtil.createHighlightRangy(
                context.applicationContext,
                html,
                mBookId,
                pageName,
                spineIndex,
                rangy
            )
        }
    }

    override fun highLightText(fragmentId: String) {
        mWebview!!.loadUrl(String.format(getString(R.string.audio_mark_id), fragmentId))
    }

    override fun highLightTTS() {
        mWebview!!.loadUrl("javascript:alert(getSentenceWithIndex('epub-media-overlay-playing'))")
    }

    @JavascriptInterface
    fun getUpdatedHighlightId(id: String?, style: String) {
        Log.d(LOG_TAG, "getUpdatedHighlightId -- $id, $style")
//        if (id != null) {
//            val highlightImpl = HighLightTable.updateHighlightStyle(id, style)
//            if (highlightImpl != null) {
//                HighlightUtil.sendHighlightBroadcastEvent(
//                    activity!!.applicationContext,
//                    highlightImpl,
//                    HighLight.HighLightAction.MODIFY
//                )
//            }
//            val rangyString = HighlightUtil.generateRangyString(pageName)
//            activity!!.runOnUiThread { loadRangy(rangyString) }
//        }
    }

//    override fun onDestroy() {
//        super.onDestroy()
//
//        if (isCurrentFragment) {
//            if (outState != null)
//                outState!!.putSerializable(BUNDLE_READ_LOCATOR_CONFIG_CHANGE, lastReadLocator)
//            if (activity != null && !activity!!.isFinishing)
//                mActivityCallback!!.storeLastReadLocator(lastReadLocator)
//        }
//        if (mWebview != null) mWebview!!.destroy()
//    }

    override fun onError() {}

    fun scrollToHighlightId(highlightId: String) {
        this.highlightId = highlightId

        if (loadingView != null && loadingView!!.visibility != View.VISIBLE) {
            loadingView!!.show()
            mWebview!!.loadUrl(String.format(getString(R.string.go_to_highlight), highlightId))
            this.highlightId = null
        }
    }

    fun scrollToRangy(rangy: String) {
        Log.d(LOG_TAG, "scrollToRangy -- $rangy")
        this.rangyToScroll = rangy

        loadingView?.let {
            if (it.visibility != View.VISIBLE) {
                it.show()
                mWebview?.loadUrl(
                    String.format(
                        "javascript:if(typeof ssReader !== \"undefined\"){ssReader.goToRangy('%s');}",
                        rangy
                    )
                )
                this.rangyToScroll = null
            }
        }
    }

    fun highlightSearchLocator(searchLocator: SearchLocator) {
        Log.v(LOG_TAG, "-> highlightSearchLocator")
        this.searchLocatorVisible = searchLocator

        if (loadingView != null && loadingView!!.visibility != View.VISIBLE) {
            loadingView!!.show()
            val callHighlightSearchLocator = String.format(
                getString(R.string.callHighlightSearchLocator),
                searchLocatorVisible?.locations?.cfi
            )
            mWebview!!.loadUrl(callHighlightSearchLocator)
        }
    }

    fun clearSearchLocator() {
        Log.v(LOG_TAG, "-> clearSearchLocator -> " + spineItem.href!!)
        mWebview!!.loadUrl(getString(R.string.callClearSelection))
        searchLocatorVisible = null
    }

    private fun getString(@StringRes resId: Int): String {
        return context.resources.getString(resId)
    }

    override fun showMenu(): Boolean {
        return false
    }

    override fun triggerHighlight(rect: Rect) {
        //TODO
    }

    fun deleteHighlight(highlightId: String, globalId: String) {
        mWebview?.loadUrl("javascript:clearSelection()")
        mWebview?.loadUrl(
            String.format(
                "javascript:if(typeof ssReader !== \"undefined\"){ssReader.deleteHighlight('%s');}",
                highlightId
            )
        )
        mWebview?.loadUrl(
            String.format(
                "javascript:if(typeof ssReader !== \"undefined\"){ssReader.removeMarker('%s');}",
                globalId
            )
        )
    }

//    @Override
//    public boolean onTouchEvent(MotionEvent event) {
//        int count = webViewPager.getAdapter().getCount();
//        int current = webViewPager.getCurrentItem();
//        if (current < count - 1) {
//            super.onTouchEvent(event);
//            return true;
//        } else return super.onTouchEvent(event);
//    }
//
//    @Override
//    public boolean onInterceptTouchEvent(MotionEvent ev) {
//        Log.d(LOG_TAG, "-> onInterceptTouchEvent -> " + AppUtil.actionToString(ev.getAction()));
//        Log.d(LOG_TAG, "onInterceptTouchEvent: spinItem: " + spineItem.getHref() + ": " + mIsPageLoaded);
//        Log.d(LOG_TAG, "webViewPager child count: " + webViewPager.getAdapter().getCount());
//        Log.d(LOG_TAG, "webViewPager current page: " + webViewPager.getCurrentItem());
//        int count = webViewPager.getAdapter().getCount();
//        int current = webViewPager.getCurrentItem();
//        if (current < count - 1) {
//            return true;
//        } else return super.onInterceptTouchEvent(ev);
//    }
}
