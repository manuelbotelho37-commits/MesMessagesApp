package fr.manubotelho.pokermanu;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public final class PokerTableView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String[] seats={"UTG","UTG+1","MP","HJ","CO","BTN","SB","BB","UTG+2"};
    private String hero="A♠ K♠", board="— — — — —";

    public PokerTableView(Context context){ super(context); init(); }
    public PokerTableView(Context context, AttributeSet attrs){ super(context,attrs); init(); }
    private void init(){ setMinimumHeight(dp(320)); }

    public void setCards(String hero,String board){
        this.hero=(hero==null||hero.trim().isEmpty())?"— —":hero.trim();
        this.board=(board==null||board.trim().isEmpty())?"— — — — —":board.trim();
        invalidate();
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(), h=getHeight();
        float cx=w/2f, cy=h/2f;
        paint.setColor(Color.rgb(6,30,38));
        c.drawRoundRect(new RectF(dp(14),dp(16),w-dp(14),h-dp(16)),dp(26),dp(26),paint);
        paint.setColor(Color.rgb(20,74,91));
        c.drawOval(new RectF(w*0.16f,h*0.22f,w*0.84f,h*0.78f),paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(3)); paint.setColor(Color.rgb(71,201,255));
        c.drawOval(new RectF(w*0.16f,h*0.22f,w*0.84f,h*0.78f),paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setTextAlign(Paint.Align.CENTER); paint.setColor(Color.WHITE); paint.setTextSize(dp(16)); paint.setFakeBoldText(true);
        c.drawText("Board",cx,cy-dp(26),paint);
        paint.setTextSize(dp(18)); c.drawText(board,cx,cy+dp(4),paint);
        paint.setTextSize(dp(15)); c.drawText("Hero  "+hero,cx,cy+dp(38),paint);

        double[] a={-90,-55,-20,20,55,90,140,180,220};
        float rx=w*0.42f, ry=h*0.37f;
        paint.setTextSize(dp(12));
        for(int i=0;i<seats.length;i++){
            double rad=Math.toRadians(a[i]);
            float x=cx+(float)Math.cos(rad)*rx;
            float y=cy+(float)Math.sin(rad)*ry;
            paint.setColor(Color.rgb(10,48,60));
            c.drawCircle(x,y,dp(24),paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2)); paint.setColor(Color.rgb(71,201,255)); c.drawCircle(x,y,dp(24),paint); paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE); c.drawText(seats[i],x,y+dp(4),paint);
        }
        paint.setFakeBoldText(false);
    }

    private float dp(float v){ return v*getResources().getDisplayMetrics().density; }
}
