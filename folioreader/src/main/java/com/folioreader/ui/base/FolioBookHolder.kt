package com.folioreader.ui.base

import android.graphics.Rect
import com.folioreader.model.HighlightImpl
import com.folioreader.model.search.SearchItem

interface FolioBookHolder {
    fun getCurrentHref(): String
    fun highlight(style: HighlightImpl.HighlightStyle, isAlreadyCreated: Boolean)
    fun getPageName(): String
    fun loadRangy(rangy: String);
    fun setSearchItemVisible(item: SearchItem?)
    fun showMenu(): Boolean {
        return true
    }
    fun triggerHighlight(rect: Rect) {
        print("triggerHighlight at ${rect})")
    }
}
