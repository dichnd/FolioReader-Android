package com.folioreader.util;

import android.graphics.Rect;

import com.folioreader.model.HighLight;
import com.folioreader.model.HighlightImpl;

/**
 * Interface to convey highlight events.
 *
 * @author gautam chibde on 26/9/17.
 */

public abstract class OnHighlightListener {

    /**
     * This method will be invoked when a highlight is created, deleted or modified.
     *
     * @param highlight meta-data for created highlight {@link HighlightImpl}.
     * @param type      type of event e.g new,edit or delete {@link com.folioreader.model.HighlightImpl.HighLightAction}.
     */
    public abstract void onHighlight(HighLight highlight, HighLight.HighLightAction type);
    public void onDeleteHighlight(HighLight highLight) {
        //TODO
    }
    public void onTriggerHighlight(Rect rect, String highlightId) {
        //TODO
    };
    public void onDismissPopup() {
        //TODO
    };
}
