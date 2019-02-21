package com.folioreader.ui.base

import com.folioreader.model.HighlightImpl
import com.folioreader.model.locators.SearchLocator

interface FolioBookHolder {
    val currentHref: String?
    val pageName: String
    fun highlight(style: HighlightImpl.HighlightStyle, isAlreadyCreated: Boolean)
    fun loadRangy(rangy: String)
    var searchLocatorVisible: SearchLocator?
}
