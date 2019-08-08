package com.stone.card.library;

import android.graphics.Rect;
import android.support.v4.widget.ViewDragHelper;
import android.view.View;

/**
 * 这是 view drag helper 拖拽效果的主要逻辑
 *
 * Created by im_dsd on 2019-08-08
 */
public class DragHelperCallback extends ViewDragHelper.Callback {
    /**
     * view叠加缩放的步长
     */
    private static final float SCALE_STEP = 0.08f;
    private final CardSlideView mView;

    public DragHelperCallback(CardSlideView view) {
        mView = view;
    }

    @Override
    public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
        if (mView != null) {
            mView.onViewPosChanged(changedView);
        }
    }

    @Override
    public boolean tryCaptureView(View child, int pointerId) {
        if (mView == null) {
            return false;
        }
        // 如果数据List为空，或者子View不可见，则不予处理
        if (mView.getAdapter() == null || mView.getAdapter().getCount() == 0
            || child.getVisibility() != View.VISIBLE || child.getScaleX() <= 1.0f - SCALE_STEP) {
            // 一般来讲，如果拖动的是第三层、或者第四层的View，则直接禁止
            // 此处用getScale的用法来巧妙回避
            return false;
        }
        if (mView.isBtnLocked()) {
            return false;
        }
        // 1. 只有顶部的View才允许滑动
        if (mView.getViewIndex(child) > 0) {
            return false;
        }
        // 2. 获取可滑动区域
        ((CardItemView) child).onStartDragging();
        if (mView.getDraggableArea() == null && mView.getAdapter() != null) {
            Rect draggableArea = mView.getAdapter().obtainDraggableArea(child);
            mView.setDraggableArea(draggableArea);
        }
        // 3. 判断是否可滑动
        boolean shouldCapture = true;
        if (null != mView.getDraggableArea()) {
            shouldCapture = mView.getDraggableArea().contains(mView.getClickDownPoint().x, mView.getClickDownPoint().y);
        }
        // 4. 如果确定要滑动，就让touch事件交给自己消费
        if (shouldCapture) {
            mView.getItemViewParent().requestDisallowInterceptTouchEvent(shouldCapture);
        }
        return shouldCapture;
    }

    @Override
    public int getViewHorizontalDragRange(View child) {
        // 这个用来控制拖拽过程中松手后，自动滑行的速度
        return 256;
    }

    @Override
    public void onViewReleased(View releasedChild, float xvel, float yvel) {
        if (mView != null) {
            mView.onViewReleased(releasedChild, (int) xvel, (int) yvel);
        }
    }

    @Override
    public int clampViewPositionHorizontal(View child, int left, int dx) {
        return left;
    }

    @Override
    public int clampViewPositionVertical(View child, int top, int dy) {
        return top;
    }
}