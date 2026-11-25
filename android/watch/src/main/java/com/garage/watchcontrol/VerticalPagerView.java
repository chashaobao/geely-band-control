package com.garage.watchcontrol;

import android.content.Context;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.OverScroller;

final class VerticalPagerView extends ViewGroup {
    interface OnPageChangeListener {
        void onPageChanged(int page);
    }

    private static final long CROWN_DEBOUNCE_MS = 220;

    private final OverScroller scroller;
    private final int touchSlop;
    private final int minFlingVelocity;

    private VelocityTracker velocityTracker;
    private OnPageChangeListener pageChangeListener;
    private float lastY;
    private float startX;
    private float startY;
    private boolean dragging;
    private int currentPage;
    private long lastCrownSwitchAtMs;

    VerticalPagerView(Context context) {
        super(context);
        scroller = new OverScroller(context);
        ViewConfiguration config = ViewConfiguration.get(context);
        touchSlop = config.getScaledTouchSlop();
        minFlingVelocity = config.getScaledMinimumFlingVelocity();
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    void setOnPageChangeListener(OnPageChangeListener listener) {
        this.pageChangeListener = listener;
    }

    int currentPage() {
        return currentPage;
    }

    void setCurrentPage(int page, boolean animate) {
        int maxPage = Math.max(0, getChildCount() - 1);
        int target = Math.max(0, Math.min(page, maxPage));
        int height = getHeight();
        if (height <= 0) {
            currentPage = target;
            notifyPageChanged();
            return;
        }
        int targetY = target * height;
        if (target != currentPage) {
            currentPage = target;
            notifyPageChanged();
        }
        if (animate) {
            scroller.abortAnimation();
            scroller.startScroll(0, getScrollY(), 0, targetY - getScrollY(), 260);
            invalidate();
        } else {
            scroller.abortAnimation();
            scrollTo(0, targetY);
        }
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
        int childWidthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        int childHeightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(childWidthSpec, childHeightSpec);
        }
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            int childTop = i * height;
            child.layout(0, childTop, width, childTop + height);
        }
        if (!dragging && scroller.isFinished()) {
            scrollTo(0, currentPage * height);
        }
    }

    @Override public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.getCurrX(), scroller.getCurrY());
            postInvalidateOnAnimation();
        }
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            startX = event.getX();
            startY = event.getY();
            lastY = startY;
            dragging = !scroller.isFinished();
            if (dragging) scroller.abortAnimation();
            ensureTracker(event);
            return dragging;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            float dx = Math.abs(event.getX() - startX);
            float dy = Math.abs(event.getY() - startY);
            if (dy > touchSlop && dy > dx) {
                dragging = true;
                lastY = event.getY();
                ensureTracker(event);
                return true;
            }
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            dragging = false;
            recycleTracker();
        }
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (getChildCount() <= 1 || getHeight() <= 0) return super.onTouchEvent(event);
        ensureTracker(event);
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN -> {
                if (!scroller.isFinished()) scroller.abortAnimation();
                startY = event.getY();
                lastY = startY;
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                float y = event.getY();
                float deltaY = lastY - y;
                lastY = y;
                int maxScroll = (getChildCount() - 1) * getHeight();
                int nextY = Math.max(0, Math.min(maxScroll, Math.round(getScrollY() + deltaY)));
                scrollTo(0, nextY);
                dragging = true;
                return true;
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false;
                velocityTracker.computeCurrentVelocity(1000);
                float vy = velocityTracker.getYVelocity();
                recycleTracker();
                int height = getHeight();
                int target;
                if (Math.abs(vy) >= minFlingVelocity * 2) {
                    target = vy < 0 ? currentPage + 1 : currentPage - 1;
                } else {
                    target = (getScrollY() + height / 2) / height;
                }
                setCurrentPage(target, true);
                return true;
            }
            default -> {
                return super.onTouchEvent(event);
            }
        }
    }

    @Override public boolean onGenericMotionEvent(MotionEvent event) {
        if (handleCrownMotion(event)) return true;
        return super.onGenericMotionEvent(event);
    }

    boolean handleCrownMotion(MotionEvent event) {
        if (event == null || event.getAction() != MotionEvent.ACTION_SCROLL) return false;
        if (!event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)
                && !event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) {
            return false;
        }
        float scroll = event.getAxisValue(MotionEvent.AXIS_SCROLL);
        if (scroll == 0f) scroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
        long now = SystemClock.uptimeMillis();
        int target = nextPageFromCrown(currentPage, getChildCount(), scroll, now, lastCrownSwitchAtMs);
        if (target < 0) return scroll != 0f && getChildCount() > 1;
        lastCrownSwitchAtMs = now;
        setCurrentPage(target, true);
        return true;
    }

    static int nextPageFromCrown(int currentPage, int pageCount, float scroll, long nowMs, long lastSwitchMs) {
        if (scroll == 0f || pageCount <= 1) return -1;
        if (lastSwitchMs > 0 && nowMs - lastSwitchMs < CROWN_DEBOUNCE_MS) return -1;
        int target = Math.max(0, Math.min(pageCount - 1, scroll < 0f ? currentPage + 1 : currentPage - 1));
        return target == currentPage ? -1 : target;
    }

    private void ensureTracker(MotionEvent event) {
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
        velocityTracker.addMovement(event);
    }

    private void recycleTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    private void notifyPageChanged() {
        if (pageChangeListener != null) {
            pageChangeListener.onPageChanged(currentPage);
        }
    }
}
