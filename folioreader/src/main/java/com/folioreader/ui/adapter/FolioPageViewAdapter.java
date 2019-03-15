package com.folioreader.ui.adapter;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;
import android.view.View;
import android.view.ViewGroup;

import com.folioreader.ui.activity.FolioActivityCallback;
import com.folioreader.ui.view.FolioPageView;

import org.readium.r2.shared.Link;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Copy from FolioPageFragmentAdapter on 2/2019
 */
public class FolioPageViewAdapter extends PagerAdapter {
    private List<Link> mSpineReferences;
    private String mEpubFileName;
    private String mBookId;
    private ArrayList<FolioPageView> views;
//    private ArrayList<Fragment.SavedState> savedStateList;
    private Context mContext;
    private FolioActivityCallback mActivityCallback;

    public FolioPageViewAdapter(Context context, List<Link> spineReferences, String epubFileName, String bookId) {
        super();
        mContext = context;
        this.mSpineReferences = spineReferences;
        this.mEpubFileName = epubFileName;
        this.mBookId = bookId;
        views = new ArrayList<>(Arrays.asList(new FolioPageView[mSpineReferences.size()]));
    }

    public void setActivityCallback(FolioActivityCallback mActivityCallback) {
        this.mActivityCallback = mActivityCallback;
    }

    public FolioPageView getItem(int position) {

        if (mSpineReferences.size() == 0 || position < 0 || position >= mSpineReferences.size())
            return null;

        FolioPageView folioPageView = views.get(position);
        if (folioPageView == null) {
            folioPageView = FolioPageView.newInstance(mContext, position,
                    mEpubFileName, mSpineReferences.get(position), mBookId, mActivityCallback);
            views.set(position, folioPageView);
        }
        return folioPageView;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull  Object object) {
        ((FolioPageView) object).onStop();
        container.removeView((FolioPageView) object);
//        views.set(position, null);
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        FolioPageView folioPageView = getItem(position); //FIXME why NonNull?
        //views.set(position, folioPageView);
        container.addView(folioPageView);
        return folioPageView;
    }



    @Override
    public int getCount() {
        return mSpineReferences.size();
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object o) {
        return o == view;
    }
}
