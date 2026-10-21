package com.mixax.speedreader;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.SeekBar;

/** Native SeekBar semantics with a vertical track: 500 at the top, 30 at the bottom. */
public final class VerticalSpeedBar extends androidx.appcompat.widget.AppCompatSeekBar {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int primary = Color.rgb(55,104,89);
    private final int track = Color.rgb(193,208,198);
    public VerticalSpeedBar(Context context) {
        super(context);
        setMin(30); setMax(500); setKeyProgressIncrement(1);
        setFocusable(true); setBackground(null);
    }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    @Override protected synchronized void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSizeAndState(Math.round(dp(64)),widthSpec,0),
            resolveSizeAndState(Math.round(dp(420)),heightSpec,0));
    }
    @Override protected synchronized void onDraw(Canvas canvas) {
        float x=getWidth()/2f, top=dp(20), bottom=Math.max(top+1,getHeight()-dp(20));
        float y=bottom-(getProgress()-getMin())/(float)(getMax()-getMin())*(bottom-top);
        paint.setColor(track); canvas.drawRoundRect(x-dp(3),top,x+dp(3),bottom,dp(3),dp(3),paint);
        paint.setColor(primary); canvas.drawRoundRect(x-dp(3),y,x+dp(3),bottom,dp(3),dp(3),paint);
        if(isPressed()||isFocused()) {
            paint.setColor(0x25376859);canvas.drawCircle(x,y,dp(20),paint);
        }
        paint.setColor(primary);canvas.drawCircle(x,y,dp(10),paint);
    }
    private void trackTouch(float y) {
        float top=dp(20), length=Math.max(1,getHeight()-2*top);
        int value=Math.round(getMax()-(y-top)/length*(getMax()-getMin()));
        setProgress(Math.max(getMin(),Math.min(getMax(),value))); invalidate();
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if(!isEnabled())return false;
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);setPressed(true);trackTouch(event.getY());return true;
            case MotionEvent.ACTION_MOVE:
                trackTouch(event.getY());return true;
            case MotionEvent.ACTION_UP:
                trackTouch(event.getY());setPressed(false);getParent().requestDisallowInterceptTouchEvent(false);performClick();return true;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);getParent().requestDisallowInterceptTouchEvent(false);invalidate();return true;
            default:return true;
        }
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override public boolean onKeyDown(int code,KeyEvent event) {
        if(isEnabled()&&(code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_DOWN)) {
            setProgress(getProgress()+(code==KeyEvent.KEYCODE_DPAD_UP?1:-1));return true;
        }
        return super.onKeyDown(code,event);
    }
    // Inherited SeekBar accessibility supplies range 30–500 and ACTION_SET_PROGRESS.
    @Override public CharSequence getAccessibilityClassName() { return SeekBar.class.getName(); }
}
