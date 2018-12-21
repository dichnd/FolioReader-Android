package com.folioreader.view;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.folioreader.Config;
import com.folioreader.Constants;
import com.folioreader.FolioReader;
import com.folioreader.R;
import com.folioreader.model.HighLight;
import com.folioreader.model.HighlightImpl;
import com.folioreader.model.ReadPosition;
import com.folioreader.model.ReadPositionImpl;
import com.folioreader.model.event.MediaOverlayHighlightStyleEvent;
import com.folioreader.model.event.MediaOverlayPlayPauseEvent;
import com.folioreader.model.event.MediaOverlaySpeedEvent;
import com.folioreader.model.event.ReloadDataEvent;
import com.folioreader.model.event.RewindIndexEvent;
import com.folioreader.model.event.UpdateHighlightEvent;
import com.folioreader.model.search.SearchItem;
import com.folioreader.model.sqlite.HighLightTable;
import com.folioreader.ui.base.FolioBookHolder;
import com.folioreader.ui.base.HtmlTask;
import com.folioreader.ui.base.HtmlTaskCallback;
import com.folioreader.ui.base.HtmlUtil;
import com.folioreader.ui.folio.activity.FolioActivityCallback;
import com.folioreader.ui.folio.fragment.FolioPageFragment;
import com.folioreader.ui.folio.fragment.MediaControllerFragment;
import com.folioreader.ui.folio.mediaoverlay.MediaController;
import com.folioreader.ui.folio.mediaoverlay.MediaControllerCallbacks;
import com.folioreader.util.AppUtil;
import com.folioreader.util.HighlightUtil;
import com.folioreader.util.UiUtil;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.readium.r2.shared.Link;

import java.util.Locale;
import java.util.regex.Pattern;

public class FolioPageView extends FrameLayout implements MediaControllerCallbacks, HtmlTaskCallback, FolioWebView.SeekBarListener, FolioBookHolder {
    public static final String LOG_TAG = FolioPageFragment.class.getSimpleName();
    public static final String KEY_FRAGMENT_FOLIO_POSITION = "com.folioreader.ui.folio.fragment.FolioPageFragment.POSITION";
    public static final String KEY_FRAGMENT_FOLIO_BOOK_TITLE = "com.folioreader.ui.folio.fragment.FolioPageFragment.BOOK_TITLE";
    public static final String KEY_FRAGMENT_EPUB_FILE_NAME = "com.folioreader.ui.folio.fragment.FolioPageFragment.EPUB_FILE_NAME";
    private static final String KEY_IS_SMIL_AVAILABLE = "com.folioreader.ui.folio.fragment.FolioPageFragment.IS_SMIL_AVAILABLE";
    private static final String BUNDLE_READ_POSITION_CONFIG_CHANGE = "BUNDLE_READ_POSITION_CONFIG_CHANGE";
    public static final String BUNDLE_SEARCH_ITEM = "BUNDLE_SEARCH_ITEM";

    private static final int ACTION_ID_COPY = 1001;
    private static final int ACTION_ID_SHARE = 1002;
    private static final int ACTION_ID_HIGHLIGHT = 1003;
    private static final int ACTION_ID_DEFINE = 1004;

    private static final int ACTION_ID_HIGHLIGHT_COLOR = 1005;
    private static final int ACTION_ID_DELETE = 1006;

    private static final int ACTION_ID_HIGHLIGHT_YELLOW = 1007;
    private static final int ACTION_ID_HIGHLIGHT_GREEN = 1008;
    private static final int ACTION_ID_HIGHLIGHT_BLUE = 1009;
    private static final int ACTION_ID_HIGHLIGHT_PINK = 1010;
    private static final int ACTION_ID_HIGHLIGHT_UNDERLINE = 1011;
    private static final String KEY_TEXT_ELEMENTS = "text_elements";
    private static final String SPINE_ITEM = "spine_item";

    private String mHtmlString = null;
    private boolean hasMediaOverlay = false;
    private String mAnchorId;
    private String rangy = "";
    private String highlightId;

    private ReadPosition lastReadPosition;
    private Bundle outState;
    private Bundle savedInstanceState;

    private View mRootView;

    private LoadingView loadingView;
    private VerticalSeekbar mScrollSeekbar;
    public FolioWebView mWebview;
    private WebViewPager webViewPager;
    private TextView mPagesLeftTextView, mMinutesLeftTextView;
    private FolioActivityCallback mActivityCallback;

    private int mTotalMinutes;
    private String mSelectedText;
    private Animation mFadeInAnimation, mFadeOutAnimation;

    public Link spineItem;
    private int mPosition = -1;
    private String mBookTitle;
    private String mEpubFileName = null;
    private boolean mIsPageReloaded;

    private String highlightStyle;

    private MediaController mediaController;
    private Config mConfig;
    private String mBookId;
    public SearchItem searchItemVisible;
    private Handler handler;
    private Bundle arguments;

    public static FolioPageView newInstance(Context context, int position, String bookTitle, Link spineRef, String bookId, FolioActivityCallback cb) {
        Bundle args = new Bundle();
        args.putInt(KEY_FRAGMENT_FOLIO_POSITION, position);
        args.putString(KEY_FRAGMENT_FOLIO_BOOK_TITLE, bookTitle);
        args.putString(FolioReader.INTENT_BOOK_ID, bookId);
        args.putSerializable(SPINE_ITEM, spineRef);
        return new FolioPageView(context, args, cb);
    }

    public FolioPageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public FolioPageView(Context context) {
        super(context);
        initView();
    }

    public FolioPageView(Context context, Bundle arguments, FolioActivityCallback cb) {
        super(context);
        this.arguments = arguments;
        this.mActivityCallback = cb;
        initView();
    }

    private void initView() {
        handler = new Handler();
        this.savedInstanceState = getArguments();
        EventBus.getDefault().register(this);

        mPosition = getArguments().getInt(KEY_FRAGMENT_FOLIO_POSITION);
        mBookTitle = getArguments().getString(KEY_FRAGMENT_FOLIO_BOOK_TITLE);
        mEpubFileName = getArguments().getString(KEY_FRAGMENT_EPUB_FILE_NAME);
        spineItem = (Link) getArguments().getSerializable(SPINE_ITEM);
        mBookId = getArguments().getString(FolioReader.INTENT_BOOK_ID);

        if (savedInstanceState != null) {
            searchItemVisible = savedInstanceState.getParcelable(BUNDLE_SEARCH_ITEM);
        }

        if (spineItem != null) {
            // SMIL Parsing not yet implemented in r2-streamer-kotlin
            //if (spineItem.getProperties().contains("media-overlay")) {
            //    mediaController = new MediaController(getActivity(), MediaController.MediaType.SMIL, this);
            //    hasMediaOverlay = true;
            //} else {
            mediaController = new MediaController(getContext(), MediaController.MediaType.TTS, this);
            mediaController.setTextToSpeech(getContext());
            //}
        }
        highlightStyle = HighlightImpl.HighlightStyle.classForStyle(HighlightImpl.HighlightStyle.Normal);
        mRootView = inflate(getContext(), R.layout.folio_page_fragment, this);
        mPagesLeftTextView = mRootView.findViewById(R.id.pagesLeft);
        mMinutesLeftTextView = mRootView.findViewById(R.id.minutesLeft);

        mConfig = AppUtil.getSavedConfig(getContext());

        loadingView = mRootView.findViewById(R.id.loadingView);
        initSeekbar();
        initAnimations();
        initWebView();
        updatePagesLeftTextBg();
    }

    private Bundle getArguments() {
//        return ((Activity)getContext()).getIntent().getExtras();
        return arguments;
    }

    private void initSeekbar() {
        mScrollSeekbar = (VerticalSeekbar) mRootView.findViewById(R.id.scrollSeekbar);
        if (getSeekBarDrawable() != null) {
            getSeekBarDrawable()
                    .setColorFilter(getResources()
                                    .getColor(R.color.app_green),
                            PorterDuff.Mode.SRC_IN);
        }
    }

    private Drawable getSeekBarDrawable() {
        if (mScrollSeekbar.getProgressDrawable() != null) return mScrollSeekbar.getProgressDrawable();
        else return mScrollSeekbar.getIndeterminateDrawable();
    }

    private void initAnimations() {
        mFadeInAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.fadein);
        mFadeInAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                mScrollSeekbar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                fadeOutSeekBarIfVisible();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        mFadeOutAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.fadeout);
        mFadeOutAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mScrollSeekbar.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
    }

    private void updatePagesLeftText(int scrollY) {
        try {
            int currentPage = (int) (Math.ceil((double) scrollY / mWebview.getWebViewHeight()) + 1);
            int totalPages =
                    (int) Math.ceil((double) mWebview.getContentHeightVal()
                            / mWebview.getWebViewHeight());
            int pagesRemaining = totalPages - currentPage;
            String pagesRemainingStrFormat =
                    pagesRemaining > 1 ?
                            getContext().getResources().getString(R.string.pages_left) :
                            getContext().getResources().getString(R.string.page_left);
            String pagesRemainingStr = String.format(Locale.US,
                    pagesRemainingStrFormat, pagesRemaining);

            int minutesRemaining =
                    (int) Math.ceil((double) (pagesRemaining * mTotalMinutes) / totalPages);
            String minutesRemainingStr;
            if (minutesRemaining > 1) {
                minutesRemainingStr =
                        String.format(Locale.US, getContext().getResources().getString(R.string.minutes_left),
                                minutesRemaining);
            } else if (minutesRemaining == 1) {
                minutesRemainingStr =
                        String.format(Locale.US, getContext().getResources().getString(R.string.minute_left),
                                minutesRemaining);
            } else {
                minutesRemainingStr = getContext().getResources().getString(R.string.less_than_minute);
            }

            mMinutesLeftTextView.setText(minutesRemainingStr);
            mPagesLeftTextView.setText(pagesRemainingStr);
        } catch (java.lang.ArithmeticException | IllegalStateException exp) {
            Log.e("divide error", exp.toString());
        }
    }

    private void updatePagesLeftTextBg() {
        if (mConfig.isNightMode()) {
            mRootView.findViewById(R.id.indicatorLayout)
                    .setBackgroundColor(Color.parseColor("#131313"));
        } else {
            mRootView.findViewById(R.id.indicatorLayout)
                    .setBackgroundColor(Color.WHITE);
        }
    }

    @Override
    public String getPageName() {
        return mBookTitle + "$" + spineItem.getHref();
    }

    @Override
    public String getCurrentHref() {
        return spineItem.getHref();
    }

    @Override
    public void highlight(HighlightImpl.HighlightStyle style, boolean isAlreadyCreated) {
        if (!isAlreadyCreated) {
            mWebview.loadUrl(String.format("javascript:if(typeof ssReader !== \"undefined\"){ssReader.highlightSelection('%s');}", HighlightImpl.HighlightStyle.classForStyle(style)));
        } else {
            mWebview.loadUrl(String.format("javascript:setHighlightStyle('%s')", HighlightImpl.HighlightStyle.classForStyle(style)));
        }
    }

    @Override
    public void loadRangy(String rangy) {
        mWebview.loadUrl(String.format("javascript:if(typeof ssReader !== \"undefined\"){ssReader.setHighlights('%s');}", rangy));
    }

    @Override
    public void setSearchItemVisible(SearchItem item) {
        searchItemVisible = item;
    }

    public void fadeInSeekBarIfInvisible() {
        if (mScrollSeekbar.getVisibility() == View.INVISIBLE ||
                mScrollSeekbar.getVisibility() == View.GONE) {
            mScrollSeekbar.startAnimation(mFadeInAnimation);
        }
    }

    public void fadeOutSeekBarIfVisible() {
        if (mScrollSeekbar.getVisibility() == View.VISIBLE) {
            mScrollSeekbar.startAnimation(mFadeOutAnimation);
        }
    }

    private void setupScrollBar() {
        UiUtil.setColorIntToDrawable(mConfig.getThemeColor(), getSeekBarDrawable());
        Drawable thumbDrawable = ContextCompat.getDrawable(getContext(), R.drawable.icons_sroll);
        UiUtil.setColorIntToDrawable(mConfig.getThemeColor(), thumbDrawable);
        mScrollSeekbar.setThumb(thumbDrawable);
    }

    private void initWebView() {
        WebView.setWebContentsDebuggingEnabled(true);
        FrameLayout webViewLayout = mRootView.findViewById(R.id.webViewLayout);
        mWebview = webViewLayout.findViewById(R.id.folioWebView);
        mWebview.setFolioBookHolder(this);
        webViewPager = webViewLayout.findViewById(R.id.webViewPager);

//        if (getActivity() instanceof FolioActivityCallback)
            mWebview.setFolioActivityCallback(mActivityCallback);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            WebView.setWebContentsDebuggingEnabled(true);

        setupScrollBar();
        mWebview.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                int height =
                        (int) Math.floor(mWebview.getContentHeight() * mWebview.getScale());
                int webViewHeight = mWebview.getMeasuredHeight();
                mScrollSeekbar.setMaximum(height - webViewHeight);
            }
        });

        mWebview.getSettings().setJavaScriptEnabled(true);
        mWebview.setVerticalScrollBarEnabled(false);
        mWebview.getSettings().setAllowFileAccess(true);

        mWebview.setHorizontalScrollBarEnabled(false);

        mWebview.addJavascriptInterface(this, "Highlight");
        mWebview.addJavascriptInterface(this, "FolioPageFragment");
        mWebview.addJavascriptInterface(webViewPager, "WebViewPager");
        mWebview.addJavascriptInterface(loadingView, "LoadingView");
        mWebview.addJavascriptInterface(mWebview, "FolioWebView");

        mWebview.setScrollListener(new FolioWebView.ScrollListener() {
            @Override
            public void onScrollChange(int percent) {

                mScrollSeekbar.setProgressAndThumb(percent);
                updatePagesLeftText(percent);
            }
        });

        mWebview.setWebViewClient(webViewClient);
        mWebview.setWebChromeClient(webChromeClient);

        mWebview.getSettings().setDefaultTextEncodingName("utf-8");
        new HtmlTask(this).execute(getWebviewUrl());
    }

    private String getWebviewUrl() {
        return Constants.LOCALHOST + Uri.encode(mBookTitle) + spineItem.getHref();
    }

    private WebViewClient webViewClient = new WebViewClient() {

        @Override
        public void onPageFinished(WebView view, String url) {

            mWebview.loadUrl("javascript:getCompatMode()");
            mWebview.loadUrl("javascript:alert(getReadingTime())");

            if (!hasMediaOverlay)
                mWebview.loadUrl("javascript:wrappingSentencesWithinPTags()");

            if (mActivityCallback.getDirection() == Config.Direction.HORIZONTAL)
                mWebview.loadUrl("javascript:initHorizontalDirection()");

            view.loadUrl(String.format(getContext().getResources().getString(R.string.setmediaoverlaystyle),
                    HighlightImpl.HighlightStyle.classForStyle(
                            HighlightImpl.HighlightStyle.Normal)));

            String rangy = HighlightUtil.generateRangyString(getPageName());
            FolioPageView.this.rangy = rangy;
            if (!rangy.isEmpty())
                loadRangy(rangy);

            if (mIsPageReloaded) {

                if (searchItemVisible != null) {
                    String escapedSearchQuery = searchItemVisible.getSearchQuery()
                            .replace("\"", "\\\"");
                    String call = String.format(getContext().getResources().getString(R.string.highlight_search_result),
                            escapedSearchQuery, searchItemVisible.getOccurrenceInChapter());
                    mWebview.loadUrl(call);

                } else {
                    mWebview.loadUrl(String.format("javascript:scrollToSpan(%b, %s)",
                            lastReadPosition.isUsingId(), lastReadPosition.getValue()));
                }

//                else if (isCurrentFragment()) {
//                    mWebview.loadUrl(String.format("javascript:scrollToSpan(%b, %s)",
//                            lastReadPosition.isUsingId(), lastReadPosition.getValue()));
//                } else {
//                    if (mPosition == mActivityCallback.getCurrentChapterIndex() - 1) {
//                        // Scroll to last, the page before current page
//                        mWebview.loadUrl("javascript:scrollToLast()");
//                    } else {
//                        // Make loading view invisible for all other fragments
//                        loadingView.hide();
//                    }
//                }

                mIsPageReloaded = false;

            } else if (!TextUtils.isEmpty(mAnchorId)) {
                mWebview.loadUrl(String.format(getContext().getResources().getString(R.string.go_to_anchor), mAnchorId));
                mAnchorId = null;

            } else if (!TextUtils.isEmpty(highlightId)) {
                mWebview.loadUrl(String.format(getContext().getResources().getString(R.string.go_to_highlight), highlightId));
                highlightId = null;

            } else if (searchItemVisible != null) {
                String escapedSearchQuery = searchItemVisible.getSearchQuery()
                        .replace("\"", "\\\"");
                String call = String.format(getContext().getResources().getString(R.string.highlight_search_result),
                        escapedSearchQuery, searchItemVisible.getOccurrenceInChapter());
                mWebview.loadUrl(call);

            } else if (true /*isCurrentFragment()*/) {

                ReadPosition readPosition;
                if (savedInstanceState == null) {
                    Log.v(LOG_TAG, "-> onPageFinished -> took from getEntryReadPosition");
                    readPosition = mActivityCallback.getEntryReadPosition();
                } else {
                    Log.v(LOG_TAG, "-> onPageFinished -> took from bundle");
                    readPosition = savedInstanceState.getParcelable(BUNDLE_READ_POSITION_CONFIG_CHANGE);
                    savedInstanceState.remove(BUNDLE_READ_POSITION_CONFIG_CHANGE);
                }

                if (readPosition != null) {
                    Log.v(LOG_TAG, "-> scrollToSpan -> " + readPosition.getValue());
                    mWebview.loadUrl(String.format("javascript:scrollToSpan(%b, %s)",
                            readPosition.isUsingId(), readPosition.getValue()));
                } else {
                    loadingView.hide();
                }

            } else {

                if (mPosition == mActivityCallback.getCurrentChapterIndex() - 1) {
                    // Scroll to last, the page before current page
                    mWebview.loadUrl("javascript:scrollToLast()");
                } else {
                    // Make loading view invisible for all other fragments
                    loadingView.hide();
                }
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {

            if (url.isEmpty())
                return true;

            boolean urlOfEpub = mActivityCallback.goToChapter(url);
            if (!urlOfEpub) {
                // Otherwise, give the default behavior (open in browser)
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                getContext().startActivity(intent);
            }

            return true;
        }

        // prevent favicon.ico to be loaded automatically
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            if (url.toLowerCase().contains("/favicon.ico")) {
                try {
                    return new WebResourceResponse("image/png", null, null);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "shouldInterceptRequest failed", e);
                }
            }
            return null;
        }

        // prevent favicon.ico to be loaded automatically
        @Override
        @SuppressLint("NewApi")
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (!request.isForMainFrame()
                    && request.getUrl().getPath() != null
                    && request.getUrl().getPath().endsWith("/favicon.ico")) {
                try {
                    return new WebResourceResponse("image/png", null, null);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "shouldInterceptRequest failed", e);
                }
            }
            return null;
        }
    };

    public void scrollToAnchorId(String href) {

        if (!TextUtils.isEmpty(href) && href.indexOf('#') != -1) {
            mAnchorId = href.substring(href.lastIndexOf('#') + 1);
            if (loadingView != null && loadingView.getVisibility() != View.VISIBLE) {
                loadingView.show();
                mWebview.loadUrl(String.format(getContext().getResources().getString(R.string.go_to_anchor), mAnchorId));
                mAnchorId = null;
            }
        }
    }

    private WebChromeClient webChromeClient = new WebChromeClient() {

        @Override
        public boolean onConsoleMessage(final ConsoleMessage cm) {
            super.onConsoleMessage(cm);
            String msg = cm.message() + ", From line " + cm.lineNumber() + " of " +
                    cm.sourceId();
            return FolioWebView.onWebViewConsoleMessage(cm, "WebViewConsole", msg);
        }

        @Override
        public void onProgressChanged(WebView view, int progress) {
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {

            // This if block can be dropped
            if (!FolioPageView.this.isShown())
                return true;

            if (TextUtils.isDigitsOnly(message)) {
                try {
                    mTotalMinutes = Integer.parseInt(message);
                } catch (NumberFormatException e) {
                    mTotalMinutes = 0;
                }
            } else {
                // to handle TTS playback when highlight is deleted.
                Pattern p = Pattern.compile("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}");
                if (!p.matcher(message).matches() && (!message.equals("undefined"))) {
                    mediaController.speakAudio(message);
                }
            }

            result.confirm();
            return true;
        }
    };

    private void setHtml(boolean reloaded) {
        if (spineItem != null) {
            /*if (!reloaded && spineItem.properties.contains("media-overlay")) {
                mediaController.setSMILItems(SMILParser.parseSMIL(mHtmlString));
                mediaController.setUpMediaPlayer(spineItem.mediaOverlay, spineItem.mediaOverlay.getAudioPath(spineItem.href), mBookTitle);
            }*/
            mConfig = AppUtil.getSavedConfig(getContext());

            String href = spineItem.getHref();
            String path;
            int forwardSlashLastIndex = href.lastIndexOf('/');
            if (forwardSlashLastIndex != -1) {
                path = href.substring(0, forwardSlashLastIndex + 1);
            } else {
                path = "/";
            }

            String mimeType;
            if (spineItem.getTypeLink().equalsIgnoreCase(getContext().getResources().getString(R.string.xhtml_mime_type))) {
                mimeType = getContext().getResources().getString(R.string.xhtml_mime_type);
            } else {
                mimeType = getContext().getResources().getString(R.string.html_mime_type);
            }

            mWebview.loadDataWithBaseURL(
                    Constants.LOCALHOST + mBookTitle + path,
                    HtmlUtil.getHtmlContent(getContext(), mHtmlString, mConfig),
                    mimeType,
                    "UTF-8",
                    null);
        }
    }

    public void scrollToLast() {
        boolean isPageLoading = loadingView == null || loadingView.getVisibility() == View.VISIBLE;
        Log.v(LOG_TAG, "-> scrollToLast -> isPageLoading = " + isPageLoading);

        if (!isPageLoading) {
            loadingView.show();
            mWebview.loadUrl("javascript:scrollToLast()");
        }
    }

    public void scrollToFirst() {
        boolean isPageLoading = loadingView == null || loadingView.getVisibility() == View.VISIBLE;
        Log.v(LOG_TAG, "-> scrollToFirst -> isPageLoading = " + isPageLoading);

        if (!isPageLoading) {
            loadingView.show();
            mWebview.loadUrl("javascript:scrollToFirst()");
        }
    }

    /**
     * Calls the /assets/js/Bridge.js#getFirstVisibleSpan(boolean)
     */
    public ReadPosition getLastReadPosition() {
        Log.v(LOG_TAG, "-> getLastReadPosition -> " + spineItem.getHref());

        try {
            synchronized (this) {
                boolean isHorizontal = mActivityCallback.getDirection() ==
                        Config.Direction.HORIZONTAL;
                mWebview.loadUrl("javascript:getFirstVisibleSpan(" + isHorizontal + ")");

                wait(2000);
            }
        } catch (InterruptedException e) {
            Log.e(LOG_TAG, "-> ", e);
        }

        return lastReadPosition;
    }

    public void onStop() {
        Log.v(LOG_TAG, "-> onStop -> " + spineItem.getHref() + " -> " + isShown());

        mediaController.stop();
        //TODO save last media overlay item

        if (isShown())
            getLastReadPosition();
    }

    @Override
    public void highLightText(String fragmentId) {
        mWebview.loadUrl(String.format(getContext().getResources().getString(R.string.audio_mark_id), fragmentId));
    }

    @Override
    public void highLightTTS() {
        mWebview.loadUrl("javascript:alert(getSentenceWithIndex('epub-media-overlay-playing'))");
    }

    @Override
    public void resetCurrentIndex() {
        mWebview.loadUrl("javascript:rewindCurrentIndex()");
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public String getDirection() {
        return mActivityCallback.getDirection().toString();
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public int getTopDistraction() {
        return mActivityCallback.getTopDistraction();
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public int getBottomDistraction() {
        return mActivityCallback.getBottomDistraction();
    }

    /**
     * Callback method called from /assets/js/Bridge.js#getFirstVisibleSpan(boolean)
     * and then ReadPositionImpl is broadcast to {@link FolioReader#readPositionReceiver}
     *
     * @param usingId if span tag has id then true or else false
     * @param value   if usingId true then span id else span index
     */
    @SuppressWarnings("unused")
    @JavascriptInterface
    public void storeFirstVisibleSpan(boolean usingId, String value) {

        synchronized (this) {
            lastReadPosition = new ReadPositionImpl(mBookId, spineItem.getHref(), usingId, value);
            Intent intent = new Intent(FolioReader.ACTION_SAVE_READ_POSITION);
            intent.putExtra(FolioReader.EXTRA_READ_POSITION, lastReadPosition);
            LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);

            notify();
        }
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public void setHorizontalPageCount(int horizontalPageCount) {
        Log.v(LOG_TAG, "-> setHorizontalPageCount = " + horizontalPageCount
                + " -> " + spineItem.getHref());

        mWebview.setHorizontalPageCount(horizontalPageCount);
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public void onReceiveHighlights(String html) {
        if (html != null) {
            rangy = HighlightUtil.createHighlightRangy(getContext().getApplicationContext(),
                    html,
                    mBookId,
                    getPageName(),
                    mPosition,
                    rangy);
        }
    }

    @JavascriptInterface
    public void getUpdatedHighlightId(String id, String style) {
        if (id != null) {
            HighlightImpl highlightImpl = HighLightTable.updateHighlightStyle(id, style);
            if (highlightImpl != null) {
                HighlightUtil.sendHighlightBroadcastEvent(
                        getContext().getApplicationContext(),
                        highlightImpl,
                        HighLight.HighLightAction.MODIFY);
            }
            final String rangyString = HighlightUtil.generateRangyString(getPageName());
            handler.post(new Runnable() {
                public void run() {
                    loadRangy(rangyString);
                }
            });

        }
    }

    @Override
    public void onReceiveHtml(String html) {
        if (isShown()) {
            mHtmlString = html;
            setHtml(false);
        }
    }

    @Override
    public void onError() {

    }

    /**
     * [EVENT BUS FUNCTION]
     * Function triggered from {@link MediaControllerFragment#initListeners()} when pause/play
     * button is clicked
     *
     * @param event of type {@link MediaOverlayPlayPauseEvent} contains if paused/played
     */
    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void pauseButtonClicked(MediaOverlayPlayPauseEvent event) {
        if (isShown()
                && spineItem.getHref().equals(event.getHref())) {
            mediaController.stateChanged(event);
        }
    }

    /**
     * [EVENT BUS FUNCTION]
     * Function triggered from {@link MediaControllerFragment#initListeners()} when speed
     * change buttons are clicked
     *
     * @param event of type {@link MediaOverlaySpeedEvent} contains selected speed
     *              type HALF,ONE,ONE_HALF and TWO.
     */
    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void speedChanged(MediaOverlaySpeedEvent event) {
        if (mediaController != null)
            mediaController.setSpeed(event.getSpeed());
    }

    /**
     * [EVENT BUS FUNCTION]
     * Function triggered from {@link MediaControllerFragment#initListeners()} when new
     * style is selected on button click.
     *
     * @param event of type {@link MediaOverlaySpeedEvent} contains selected style
     *              of type DEFAULT,UNDERLINE and BACKGROUND.
     */
    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void styleChanged(MediaOverlayHighlightStyleEvent event) {
        if (isShown()) {
            switch (event.getStyle()) {
                case DEFAULT:
                    highlightStyle =
                            HighlightImpl.HighlightStyle.classForStyle(HighlightImpl.HighlightStyle.Normal);
                    break;
                case UNDERLINE:
                    highlightStyle =
                            HighlightImpl.HighlightStyle.classForStyle(HighlightImpl.HighlightStyle.DottetUnderline);
                    break;
                case BACKGROUND:
                    highlightStyle =
                            HighlightImpl.HighlightStyle.classForStyle(HighlightImpl.HighlightStyle.TextColor);
                    break;
            }
            mWebview.loadUrl(String.format(getContext().getResources().getString(R.string.setmediaoverlaystyle), highlightStyle));
        }
    }

    /**
     * [EVENT BUS FUNCTION]
     * Function triggered when any EBook configuration is changed.
     *
     * @param reloadDataEvent empty POJO.
     */
    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void reload(ReloadDataEvent reloadDataEvent) {

        if (isShown())
            getLastReadPosition();

        if (isShown()) {
            mWebview.dismissPopupWindow();
            mWebview.initViewTextSelection();
            loadingView.updateTheme();
            loadingView.show();
            mIsPageReloaded = true;
            setHtml(true);
            updatePagesLeftTextBg();
        }
    }

    /**
     * [EVENT BUS FUNCTION]
     * <p>
     * Function triggered when highlight is deleted and page is needed to
     * be updated.
     *
     * @param event empty POJO.
     */
    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void updateHighlight(UpdateHighlightEvent event) {
        if (isShown()) {
            this.rangy = HighlightUtil.generateRangyString(getPageName());
            loadRangy(this.rangy);
        }
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void resetCurrentIndex(RewindIndexEvent resetIndex) {
        if (isShown()) {
            mWebview.loadUrl("javascript:rewindCurrentIndex()");
        }
    }
}
