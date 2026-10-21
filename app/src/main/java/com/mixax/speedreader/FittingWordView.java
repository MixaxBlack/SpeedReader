package com.mixax.speedreader;

import android.content.Context;
import android.text.TextPaint;
import android.util.TypedValue;

/** Fits each word independently; the preferred size is never changed by fitting. */
public final class FittingWordView extends androidx.appcompat.widget.AppCompatTextView {
    private float preferredPx;
    public FittingWordView(Context context){super(context);setSingleLine(true);}
    public void showWord(CharSequence value,int preferredSp){
        preferredPx=TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,preferredSp,getResources().getDisplayMetrics());
        setText(value);fitWord();
    }
    private void fitWord(){
        if(preferredPx<=0)return;
        int available=getWidth()-getCompoundPaddingLeft()-getCompoundPaddingRight();
        TextPaint measure=new TextPaint(getPaint());measure.setTextSize(preferredPx);
        float width=measure.measureText(getText().toString());
        float size=available>0&&width>available?preferredPx*(available/width)*0.99f:preferredPx;
        setTextSize(TypedValue.COMPLEX_UNIT_PX,size);
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);fitWord();}
}
