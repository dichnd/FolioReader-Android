package com.folioreader.ui.activity;

import android.graphics.Rect;
import com.folioreader.Config;
import com.folioreader.model.DisplayUnit;
import com.folioreader.model.locators.ReadLocator;

import java.lang.ref.WeakReference;

public interface FolioActivityCallback {

    int getCurrentChapterIndex();

    ReadLocator getEntryReadLocator();

    boolean goToChapter(String href);

    Config.Direction getDirection();

    void onDirectionChange(Config.Direction newDirection);

    void storeLastReadLocator(ReadLocator lastReadLocator);

    void toggleSystemUI();

    void setDayMode();

    void setNightMode();

    int getTopDistraction(final DisplayUnit unit);

    int getBottomDistraction(final DisplayUnit unit);

    Rect getViewportRect(final DisplayUnit unit);

    WeakReference<FolioActivity> getActivity();

    void onClickHtml();
    String getStreamerUrl();
    void onMarkerClick(String id);

    void highlightTriggerAt(Rect rect, String highlightId, String gid, int style);

    void saveReadPosition(String json, String text); //TODO migrate to ReadLocator
}
