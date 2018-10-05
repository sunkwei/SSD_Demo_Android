package cc.hetinsow.sunkw.ssd_demo;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;

import java.util.ArrayList;

/**
 * TODO: document your custom view class.
 */
public class ShowResultView extends android.support.v7.widget.AppCompatImageView {
    private Bitmap curr_img_;
    private ArrayList<ActionBox> acts_ = null;
    private int []colors_ = {
            Color.rgb(0xff, 0, 0), Color.rgb(0, 0xff, 0), Color.rgb(0, 0, 0xff),
            Color.rgb(0xc0, 0, 0), Color.rgb(0, 0xc0, 0), Color.rgb(0, 0, 0xc0),
            Color.rgb(0x80, 0, 0), Color.rgb(0, 0x80, 0), Color.rgb(0, 0, 0x80),
            Color.rgb(0x40, 0, 0), Color.rgb(0, 0x40, 0), Color.rgb(0, 0, 0x40),

            Color.rgb(0xff, 0, 0xff), Color.rgb(0x80, 0xff, 0), Color.rgb(0xff, 0, 0xff),
            Color.rgb(0xc0, 0, 0xc0), Color.rgb(0x80, 0xc0, 0), Color.rgb(0xc0, 0, 0xc0),
            Color.rgb(0x80, 0, 0x80), Color.rgb(0x80, 0x80, 0), Color.rgb(0x80, 0, 0x80),
            Color.rgb(0x40, 0, 0x40), Color.rgb(0x80, 0x40, 0), Color.rgb(0x40, 0, 0x40),
    };

    public ShowResultView(Context context) {
        super(context);
        curr_img_ = null;
    }


    public ShowResultView(Context context, AttributeSet as)
    {
        super(context, as);
        curr_img_ = null;
    }

    public ShowResultView(Context context, AttributeSet as, int defStyle)
    {
        super(context, as, defStyle);
        curr_img_ = null;
    }

    public void set_bitmap(Bitmap bmp)
    {
        curr_img_ = bmp;
        invalidate();
    }

    public void set_action_boxes(ArrayList<ActionBox> acts)
    {
        if (curr_img_ != null) {
            this.acts_ = acts;
            invalidate();
        }
    }


    @Override
    protected void onDraw(Canvas canvas) {
        if (curr_img_ != null) {
            canvas.drawBitmap(curr_img_,0.f, 0.f,null);

            if (acts_ != null) {
                Paint p = new Paint();
                //p.setColor(Color.RED);
                p.setStrokeWidth(5.0f);
                p.setTextSize(48.0f);

                int width = curr_img_.getWidth();
                int height = curr_img_.getHeight();

                for (ActionBox act: acts_) {
                    int color = colors_[act.catalog % colors_.length];
                    p.setColor(color);

                    RectF r = new RectF(act.x1 * width, act.y1*height, act.x2*width, act.y2*height);
                    p.setStyle(Paint.Style.STROKE);
                    canvas.drawRect(r, p);

                    p.setStyle(Paint.Style.FILL);
                    canvas.drawText(act.title, act.x1*width, act.y1*height, p);
                }

                acts_ = null;
            }
        }

        super.onDraw(canvas);
    }
}
