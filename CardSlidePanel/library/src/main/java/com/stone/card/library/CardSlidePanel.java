package com.stone.card.library;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * 卡片滑动面板，主要逻辑实现类
 *
 * @author xmuSistone
 */
@SuppressLint({"HandlerLeak", "NewApi", "ClickableViewAccessibility"})
public class CardSlidePanel extends ViewGroup implements CardSlideView {
    /**
     * 存放的是每一层的 view，从顶到底
     */
    private List<CardItemView> mViewList = new ArrayList<>();
    /**
     * 手指松开后存放的 view 列表
     */
    private List<View> mReleasedViewList = new ArrayList<>();
    /**
     * 拖拽工具类
     * 这个跟原生的ViewDragHelper差不多，我仅仅只是修改了Interpolator
     */
    private final ViewDragHelper mDragHelper;
    /**
     * 最初时，中间View的 x 位置,y 位置
     */
    private int mInitCenterViewX = 0;
    private int mInitCenterViewY = 0;
    /**
     * 面板的宽度
     */
    private int mAllWidth = 0;
    private int mAllHeight = 0;
    /**
     * 每一个子 View 对应的宽度
     */
    private int mChildWith = 0;
    /**
     * view 叠加缩放的步长
     */
    private static final float SCALE_STEP = 0.08f;
    /**
     * 水平距离 + 垂直距离
     */
    private static final int MAX_SLIDE_DISTANCE_LINKAGE = 500;
    /**
     * 卡片距离顶部的偏移量
     */
    private int mItemMarginTop = 10;
    /**
     * 底部按钮与卡片的 margin 值
     */
    private int mBottomMarginTop = 40;
    /**
     * view叠加垂直偏移量的步长
     */
    private int mYOffset = 40;

    private static final int X_VEL_THRESHOLD = 800;
    private static final int X_DISTANCE_THRESHOLD = 300;

    /**
     * 消失类型
     */
    public static final int VANISH_TYPE_LEFT = 0;
    public static final int VANISH_TYPE_RIGHT = 1;


    /**
     * 回调接口
     */
    private CardSwitchListener mCardSwitchListener;
    /**
     * 当前正在显示的小项
     */
    private int isShowing = 0;
    private boolean isBtnLocked = false;
    private GestureDetectorCompat mMoveDetector;
    private Point mClickDownPoint = new Point();
    private CardAdapter mAdapter;
    private static final int MAX_VIEW_COUNT = 4;
    private Rect mDraggableArea;
    private WeakReference<Object> mSavedFirstItemData;
    private DragHelperCallback mDragHelperCallback;

    public CardSlidePanel(Context context) {
        this(context, null);
    }

    public CardSlidePanel(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CardSlidePanel(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.card);
        mItemMarginTop = (int) array.getDimension(R.styleable.card_itemMarginTop, mItemMarginTop);
        mBottomMarginTop = (int) array.getDimension(R.styleable.card_bottomMarginTop, mBottomMarginTop);
        mYOffset = (int) array.getDimension(R.styleable.card_yOffsetStep, mYOffset);
        // 滑动相关类
        mDragHelperCallback = new DragHelperCallback(this);
        mDragHelper = ViewDragHelper.create(this, 10f, mDragHelperCallback);
        mDragHelper.setEdgeTrackingEnabled(ViewDragHelper.EDGE_BOTTOM);
        array.recycle();

        mMoveDetector = new GestureDetectorCompat(context, new MoveDetector(getContext()));
        mMoveDetector.setIsLongpressEnabled(false);
        // TODO: 2019-08-08 为什么要在布局改变的时候重新绑定 adapter ？
        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (getChildCount() != MAX_VIEW_COUNT) {
                    bindAdapter();
                }
            }
        });
    }

    private void bindAdapter() {
        if (mAdapter == null || mAllWidth <= 0 || mAllHeight <= 0) {
            return;
        }
        mViewList.clear();
        for (int i = 0; i < MAX_VIEW_COUNT; i++) {
            CardItemView itemView = new CardItemView(getContext());
            itemView.bindLayoutResId(mAdapter.getLayoutId());
            itemView.setParentView(this);
            // 1. addView添加到ViewGroup中
            addView(itemView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            if (i == 0) {
                itemView.setAlpha(0);
            }
        }

        for (int i = 0; i < MAX_VIEW_COUNT; i++) {
            // 2. viewList初始化,
            mViewList.add((CardItemView) getChildAt(MAX_VIEW_COUNT - 1 - i));
            // 3. 填充数据
            if (i < mAdapter.getCount()) {
                mAdapter.bindView(mViewList.get(i), i);
                if (i == 0) {
                    mSavedFirstItemData = new WeakReference<>(mAdapter.getItem(i));
                }
            } else {
                mViewList.get(i).setVisibility(View.INVISIBLE);
            }
        }
    }

    /**
     * 手势探测器
     */
    static class MoveDetector extends SimpleOnGestureListener {

        private final int mTouchSlop;

        public MoveDetector(Context context) {
            mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
            // 拖动了，touch不往下传递
            return Math.abs(dy) + Math.abs(dx) > mTouchSlop;
        }
    }

    /**
     * 对 View 重新排序, 达到复用已经消失的 View 的效果
     */
    private void orderViewStack() {
        if (mReleasedViewList.size() == 0) {
            return;
        }

        CardItemView changedView = (CardItemView) mReleasedViewList.get(0);
        if (changedView.getLeft() == mInitCenterViewX) {
            mReleasedViewList.remove(0);
            return;
        }

        // 1. 消失的卡片View位置重置，由于大多手机会重新调用onLayout函数，所以此处大可以不做处理，不信你注释掉看看
        changedView.offsetLeftAndRight(mInitCenterViewX - changedView.getLeft());
        changedView.offsetTopAndBottom(mInitCenterViewY - changedView.getTop() + mYOffset * 2);
        float scale = 1.0f - SCALE_STEP * 2;
        changedView.setScaleX(scale);
        changedView.setScaleY(scale);
        changedView.setAlpha(0);

        // 2. 卡片View在ViewGroup中的顺次调整
        LayoutParams lp = changedView.getLayoutParams();
        removeViewInLayout(changedView);
        addViewInLayout(changedView, 0, lp, true);

        // 3. changedView填充新数据
        int newIndex = isShowing + 4;
        if (newIndex < mAdapter.getCount()) {
            mAdapter.bindView(changedView, newIndex);
        } else {
            changedView.setVisibility(View.INVISIBLE);
        }

        // 4. viewList中的卡片view的位次调整
        mViewList.remove(changedView);
        mViewList.add(changedView);
        mReleasedViewList.remove(0);

        // 5. 更新showIndex、接口回调
        if (isShowing + 1 < mAdapter.getCount()) {
            isShowing++;
        }
        if (null != mCardSwitchListener) {
            mCardSwitchListener.onShow(isShowing);
        }
    }

    /**
     * 点击按钮消失动画
     */
    public void vanishOnBtnClick(int type) {
        View animateView = mViewList.get(0);
        if (animateView.getVisibility() != View.VISIBLE || mReleasedViewList.contains(animateView)) {
            return;
        }

        int finalX = 0;
        // 为加快vanish的速度，额外添加消失的距离
        int extraVanishDistance = 100;
        if (type == VANISH_TYPE_LEFT) {
            finalX = -mChildWith - extraVanishDistance;
        } else if (type == VANISH_TYPE_RIGHT) {
            finalX = mAllWidth + extraVanishDistance;
        }

        if (finalX != 0) {
            mReleasedViewList.add(animateView);
            if (mDragHelper.smoothSlideViewTo(animateView, finalX, mInitCenterViewY + mAllHeight / 2)) {
                ViewCompat.postInvalidateOnAnimation(this);
                isBtnLocked = true;
            }
        }

        if (type >= 0 && mCardSwitchListener != null) {
            mCardSwitchListener.onCardVanish(isShowing, type);
        }
    }

    @Override
    public void computeScroll() {
        if (mDragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this);
        } else {
            // 动画结束
            if (mDragHelper.getViewDragState() == ViewDragHelper.STATE_IDLE) {
                orderViewStack();
                isBtnLocked = false;
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        // 按下时保存坐标信息
        if (action == MotionEvent.ACTION_DOWN) {
            this.mClickDownPoint.x = (int) ev.getX();
            this.mClickDownPoint.y = (int) ev.getY();
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * touch事件的拦截与处理都交给 DragHelper 来处理
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        boolean shouldIntercept = mDragHelper.shouldInterceptTouchEvent(ev);
        boolean moveFlag = mMoveDetector.onTouchEvent(ev);
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            // ACTION_DOWN的时候就对view重新排序
            if (mDragHelper.getViewDragState() == ViewDragHelper.STATE_SETTLING) {
                mDragHelper.abort();
            }
            orderViewStack();

            // 保存初次按下时arrowFlagView的Y坐标
            // action_down时就让mDragHelper开始工作，否则有时候导致异常
            mDragHelper.processTouchEvent(ev);
        }

        return shouldIntercept && moveFlag;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        try {
            // 统一交给mDragHelper处理，由DragHelperCallback实现拖动效果
            // 该行代码可能会抛异常，正式发布时请将这行代码加上try catch
            mDragHelper.processTouchEvent(e);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        measureChildren(widthMeasureSpec, heightMeasureSpec);
        int maxWidth = MeasureSpec.getSize(widthMeasureSpec);
        int maxHeight = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(
            resolveSizeAndState(maxWidth, widthMeasureSpec, 0),
            resolveSizeAndState(maxHeight, heightMeasureSpec, 0));

        mAllWidth = getMeasuredWidth();
        mAllHeight = getMeasuredHeight();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View viewItem = mViewList.get(i);
            // 1. 先layout出来
            int childHeight = viewItem.getMeasuredHeight();
            int viewLeft = (getWidth() - viewItem.getMeasuredWidth()) / 2;
            viewItem.layout(viewLeft, mItemMarginTop, viewLeft + viewItem.getMeasuredWidth(), mItemMarginTop + childHeight);

            // 2. 调整位置
            int offset = mYOffset * i;
            float scale = 1 - SCALE_STEP * i;
            if (i > 2) {
                // 备用的view
                offset = mYOffset * 2;
                scale = 1 - SCALE_STEP * 2;
            }
            viewItem.offsetTopAndBottom(offset);

            // 3. 调整缩放、重心等
            viewItem.setPivotY(viewItem.getMeasuredHeight());
            viewItem.setPivotX(viewItem.getMeasuredWidth() / 2);
            viewItem.setScaleX(scale);
            viewItem.setScaleY(scale);
        }

        if (childCount > 0) {
            // 初始化一些中间参数
            mInitCenterViewX = mViewList.get(0).getLeft();
            mInitCenterViewY = mViewList.get(0).getTop();
            mChildWith = mViewList.get(0).getMeasuredWidth();
        }
    }

    public void setAdapter(final CardAdapter adapter) {
        this.mAdapter = adapter;
        bindAdapter();
        adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                orderViewStack();

                boolean reset = false;
                if (adapter.getCount() > 0) {
                    Object firstObj = adapter.getItem(0);
                    if (null == mSavedFirstItemData) {
                        // 此前就没有数据，需要保存第一条数据
                        mSavedFirstItemData = new WeakReference<>(firstObj);
                        isShowing = 0;
                    } else {
                        Object savedObj = mSavedFirstItemData.get();
                        if (firstObj != savedObj) {
                            // 如果第一条数据不等的话，需要重置
                            isShowing = 0;
                            reset = true;
                            mSavedFirstItemData = new WeakReference<>(firstObj);
                        }
                    }
                }

                int delay = 0;
                for (int i = 0; i < MAX_VIEW_COUNT; i++) {
                    CardItemView itemView = mViewList.get(i);
                    if (isShowing + i < adapter.getCount()) {
                        adapter.bindView(itemView, isShowing + i);
                        if (itemView.getVisibility() == View.VISIBLE) {
                            if (!reset) {
                                continue;
                            }
                        } else if (i == 0) {
                            if (isShowing > 0) {
                                isShowing++;
                            }
                            mCardSwitchListener.onShow(isShowing);
                        }
                        if (i == MAX_VIEW_COUNT - 1) {
                            itemView.setAlpha(0);
                            itemView.setVisibility(View.VISIBLE);
                        } else {
                            itemView.setVisibilityWithAnimation(View.VISIBLE, delay++);
                        }
                    } else {
                        itemView.setVisibility(View.INVISIBLE);
                    }
                }
            }
        });
    }

    @Override
    public void onViewPosChanged(View changedView) {
        // 调用 offsetLeftAndRight 导致 viewPosition 改变，会调到此处，所以此处对index做保护处理
        int index = mViewList.indexOf(changedView);
        if (index + 2 >= mViewList.size()) {
            // 已经快没有数据了
            return;
        }

        processLinkageView(changedView);
    }

    /**
     * 顶层卡片View位置改变，底层的位置需要调整
     *
     * @param changedView 顶层的卡片view
     */
    private void processLinkageView(View changedView) {
        int changeViewLeft = changedView.getLeft();
        int changeViewTop = changedView.getTop();
        int distance = Math.abs(changeViewTop - mInitCenterViewY)
            + Math.abs(changeViewLeft - mInitCenterViewX);
        float rate = distance / (float) MAX_SLIDE_DISTANCE_LINKAGE;

        float rate1 = rate;
        float rate2 = rate - 0.1f;

        if (rate > 1) {
            rate1 = 1;
        }

        if (rate2 < 0) {
            rate2 = 0;
        } else if (rate2 > 1) {
            rate2 = 1;
        }

        adjustLinkageViewItem(changedView, rate1, 1);
        adjustLinkageViewItem(changedView, rate2, 2);

        CardItemView bottomCardView = mViewList.get(mViewList.size() - 1);
        bottomCardView.setAlpha(rate2);
    }

    /**
     * 由 index 对应 view 变成 index - 1 对应的 view
     */
    private void adjustLinkageViewItem(View changedView, float rate, int index) {
        int changeIndex = mViewList.indexOf(changedView);
        int initPosY = mYOffset * index;
        float initScale = 1 - SCALE_STEP * index;

        int nextPosY = mYOffset * (index - 1);
        float nextScale = 1 - SCALE_STEP * (index - 1);

        int offset = (int) (initPosY + (nextPosY - initPosY) * rate);
        float scale = initScale + (nextScale - initScale) * rate;

        View adjustView = mViewList.get(changeIndex + index);
        adjustView.offsetTopAndBottom(offset - adjustView.getTop() + mInitCenterViewY);
        adjustView.setScaleX(scale);
        adjustView.setScaleY(scale);
    }

    @Override
    public CardAdapter getAdapter() {
        return mAdapter;
    }

    @Override
    public void onViewReleased(View releasedChild, float xvel, float yvel) {
        // 松手时处理滑动到边缘的动画
        int finalX = mInitCenterViewX;
        int finalY = mInitCenterViewY;
        int flyType = -1;

        // 1. 下面这一坨计算finalX和finalY，要读懂代码需要建立一个比较清晰的数学模型才能理解，不信拉倒
        int dx = releasedChild.getLeft() - mInitCenterViewX;
        int dy = releasedChild.getTop() - mInitCenterViewY;

        // yvel < xvel * xyRate则允许以速度计算偏移
        final float xyRate = 3f;
        if (xvel > X_VEL_THRESHOLD && Math.abs(yvel) < xvel * xyRate) {
            // x正方向的速度足够大，向右滑动消失
            finalX = mAllWidth;
            finalY = (int) (yvel * (mChildWith + releasedChild.getLeft()) / xvel + releasedChild.getTop());
            flyType = VANISH_TYPE_RIGHT;
        } else if (xvel < -X_VEL_THRESHOLD && Math.abs(yvel) < -xvel * xyRate) {
            // x负方向的速度足够大，向左滑动消失
            finalX = -mChildWith;
            finalY = (int) (yvel * (mChildWith + releasedChild.getLeft()) / (-xvel) + releasedChild.getTop());
            flyType = VANISH_TYPE_LEFT;
        } else if (dx > X_DISTANCE_THRESHOLD && Math.abs(dy) < dx * xyRate) {
            // x正方向的位移足够大，向右滑动消失
            finalX = mAllWidth;
            finalY = dy * (mChildWith + mInitCenterViewX) / dx + mInitCenterViewY;
            flyType = VANISH_TYPE_RIGHT;
        } else if (dx < -X_DISTANCE_THRESHOLD && Math.abs(dy) < -dx * xyRate) {
            // x负方向的位移足够大，向左滑动消失
            finalX = -mChildWith;
            finalY = dy * (mChildWith + mInitCenterViewX) / (-dx) + mInitCenterViewY;
            flyType = VANISH_TYPE_LEFT;
        }
        // 如果斜率太高，就折中处理
        if (finalY > mAllHeight) {
            finalY = mAllHeight;
        } else if (finalY < -mAllHeight / 2) {
            finalY = -mAllHeight / 2;
        }

        // 如果没有飞向两侧，而是回到了中间，需要谨慎处理
        if (finalX == mInitCenterViewX) {
            ((CardItemView) releasedChild).animTo(mInitCenterViewX, mInitCenterViewY);
        } else {
            // 2. 向两边消失的动画
            mReleasedViewList.add(releasedChild);
            if (mDragHelper.smoothSlideViewTo(releasedChild, finalX, finalY)) {
                ViewCompat.postInvalidateOnAnimation(this);
            }
            // 3. 消失动画即将进行，listener回调
            if (flyType >= 0 && mCardSwitchListener != null) {
                mCardSwitchListener.onCardVanish(isShowing, flyType);
            }
        }
    }

    @Override
    public boolean isBtnLocked() {
        return isBtnLocked;
    }

    @Override
    public int getViewIndex(View view) {
        if (mViewList != null && mViewList.size() > 0) {
            return mViewList.indexOf(view);
        } else {
            return 0;
        }
    }

    @Override
    public Rect getDraggableArea() {
        return mDraggableArea;
    }

    @Override
    public void setDraggableArea(Rect area) {
        mDraggableArea = area;
    }

    @Override
    public ViewParent getItemViewParent() {
        return this.getParent();
    }


    @Override
    public Point getClickDownPoint() {
        return mClickDownPoint;
    }

    /**
     * 设置卡片操作回调
     */
    public void setCardSwitchListener(CardSwitchListener cardSwitchListener) {
        this.mCardSwitchListener = cardSwitchListener;
    }

    /**
     * 卡片回调接口
     */
    public interface CardSwitchListener {
        /**
         * 新卡片显示回调
         *
         * @param index 最顶层显示的卡片的index
         */
        void onShow(int index);

        /**
         * 卡片飞向两侧回调
         *
         * @param index 飞向两侧的卡片数据index
         * @param type  飞向哪一侧{@link #VANISH_TYPE_LEFT}或{@link #VANISH_TYPE_RIGHT}
         */
        void onCardVanish(int index, int type);
    }
}