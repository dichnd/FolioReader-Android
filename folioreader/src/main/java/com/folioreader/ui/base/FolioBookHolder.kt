package com.folioreader.ui.base

import android.graphics.Rect
import com.folioreader.model.HighlightImpl
import com.folioreader.model.locators.SearchLocator

interface FolioBookHolder {
    val currentHref: String?
    val pageName: String
    fun highlight(style: HighlightImpl.HighlightStyle, isAlreadyCreated: Boolean)
    fun loadRangy(rangy: String)
    fun loadMarker(rangy: String, globalIds: String)
    var searchLocatorVisible: SearchLocator?
    fun showMenu(): Boolean {
        return true
    }
    fun triggerHighlight(rect: Rect) {
        print("triggerHighlight at $rect)")
    }
}
