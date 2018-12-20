package com.folioreader.ui.base;

import com.folioreader.model.HighlightImpl;
import com.folioreader.model.search.SearchItem;

public interface FolioBookHolder {
    String getCurrentHref();
    void highlight(HighlightImpl.HighlightStyle style, boolean isAlreadyCreated);
    String getPageName();
    void loadRangy(String rangy);
    void setSearchItemVisible(SearchItem item);
}
