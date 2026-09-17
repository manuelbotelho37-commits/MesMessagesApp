package fr.manubotelho.pokermanu;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import java.util.Locale;

public final class StatsChartView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private String title="";
    private String[] labels=new String[0];
    private double[] values=new double[0];
    private final Locale fr=Locale.FRANCE;

    public StatsChartView(Context c){ super(c); setMinimumHeight(dp(230)); }

    public void setData(String title,String[] labels,double[] values){
        this.title=title==null?"":title;
        this.labels=labels==null?new String[0]:labels;
        this.values=values==null?new double[0]:values;
        invalidate();
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(), h=getHeight();
        p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(10,40,50));
        c.drawRoundRect(new RectF(0,0,w,h),dp(16),dp(16),p);
        p.setTextAlign(Paint.Align.CENTER); p.setColor(Color.WHITE); p.setTextSize(dp(16)); p.setFakeBoldText(false);
        c.drawText(title,w/2f,dp(28),p);
        if(values.length==0){ p.setColor(Color.rgb(145,169,178)); p.setTextSize(dp(13)); c.drawText("Pas encore assez de données",w/2f,h/2f,p); return; }

        double maxAbs=1;
        for(double v:values) maxAbs=Math.max(maxAbs,Math.abs(v));
        float left=dp(14), right=w-dp(14), top=dp(48), bottom=h-dp(34);
        boolean hasPos=false,hasNeg=false;
        for(double v:values){ if(v>0)hasPos=true; if(v<0)hasNeg=true; }
        float zero;
        if(hasPos&&hasNeg) zero=(top+bottom)/2f; else if(hasPos) zero=bottom; else zero=top;
        p.setColor(Color.rgb(49,83,98)); p.setStrokeWidth(dp(1));
        c.drawLine(left,zero,right,zero,p);
        int n=Math.min(labels.length,values.length);
        float slot=(right-left)/Math.max(1,n);
        float barW=Math.max(dp(8),slot*0.56f);
        for(int i=0;i<n;i++){
            double v=values[i];
            float magnitude=(float)(Math.abs(v)/maxAbs);
            float available=hasPos&&hasNeg?(bottom-top)/2f:(bottom-top);
            float bh=Math.max(v==0?0:dp(3),available*magnitude);
            float x=left+slot*i+slot/2f;
            float y1=v>=0?zero-bh:zero;
            float y2=v>=0?zero:zero+bh;
            p.setColor(v>=0?Color.rgb(73,210,138):Color.rgb(255,107,107));
            c.drawRoundRect(new RectF(x-barW/2f,y1,x+barW/2f,y2),dp(5),dp(5),p);
            p.setTextSize(dp(9)); p.setColor(Color.WHITE); p.setFakeBoldText(true);
            c.drawText(labels[i],x,h-dp(12),p);
            if(v!=0){
                p.setTextSize(dp(9)); p.setFakeBoldText(false);
                String amount=String.format(fr,"%+.0f€",v);
                float ty=v>=0?Math.max(top+dp(10),y1-dp(5)):Math.min(bottom-dp(4),y2+dp(12));
                c.drawText(amount,x,ty,p);
            }
        }
        p.setFakeBoldText(false);
    }

    private float dp(float v){ return v*getResources().getDisplayMetrics().density; }
}
