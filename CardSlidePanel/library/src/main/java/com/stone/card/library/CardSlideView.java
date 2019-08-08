package com.stone.card.library;

import android.graphics.Point;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewParent;

/**
 * 视图接口
 * Created by im_dsd on 2019-08-08
 */
public interface CardSlideView {
    /**
     * 当child（需要捕捉的View）位置改变时执行
     *
     * @param changedView 位置发生改变的 View
     */
    void onViewPosChanged(View changedView);

    /**
     * 获取 Adapter
     */
    CardAdapter getAdapter();

    /**
     * 手指抬起的时候执行该方法
     *
     * @param xvel： x方向移动的速度，若是正值，则代表向右移动，若是负值则向左移动；
     * @param yvel： y方向移动的速度，若是正值则向下移动，若是负值则向上移动。
     */
    void onViewReleased(View releasedChild, float xvel, float yvel);

    /**
     * 点击的
     *
     * @return
     */
    boolean isBtnLocked();

    /**
     * 获取 View 的索引
     */
    int getViewIndex(View view);

    /**
     * 获取拖动区域
     */
    Rect getDraggableArea();

    /**
     * 设置可拖动区域
     *
     * @param area 区域
     */
    void setDraggableArea(Rect area);

    /**
     * 获取 item view 的 parent 对象
     */
    ViewParent getItemViewParent();

    /**
     * 手指点击的位置
     */
    Point getClickDownPoint();
}
