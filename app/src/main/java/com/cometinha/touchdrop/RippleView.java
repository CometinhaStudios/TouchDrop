package com.cometinha.touchdrop;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.animation.ValueAnimator;

public class RippleView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float pulse;
    private ValueAnimator animator;
    public RippleView(Context c) { super(c); setContentDescription("Animação do toque NFC"); }
    public void play() {
        if (animator != null) animator.cancel();
        animator=ValueAnimator.ofFloat(0f,1f);animator.setDuration(1100);
        animator.addUpdateListener(a->{pulse=(float)a.getAnimatedValue();invalidate();});animator.start();
    }
    public void stop() { if (animator != null) animator.cancel(); }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);float x=getWidth()/2f,y=getHeight()/2f;
        float max=Math.min(x,y)-8;
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.rgb(40,31,63));c.drawCircle(x,y,max*.54f,paint);
        for(int i=0;i<3;i++) {
            float p=(pulse+i/3f)%1f;
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);paint.setColor(Color.argb((int)((1-p)*180),181,155,255));
            c.drawCircle(x,y,max*(.5f+p*.5f),paint);
        }
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(5);paint.setColor(Color.rgb(211,195,255));
        c.drawRoundRect(x-23,y-37,x+23,y+37,8,8,paint);c.drawLine(x-8,y+25,x+8,y+25,paint);
    }
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
}
