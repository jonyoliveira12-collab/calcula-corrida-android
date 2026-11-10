package br.com.calculacorrida.fixture;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Synthetic pixels with no accessible offer text, forcing the OCR fallback in CI. */
public class OfferFixtureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(new View(this) {
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                canvas.drawColor(Color.WHITE);
                paint.setColor(Color.BLACK);
                float d=getResources().getDisplayMetrics().density;
                paint.setTextSize(22*d);
                String[] lines={"R$ 28,81", "31 min • 19.1 km", "4,76 (164)", "6 min (2.2 km)", "25 minutos (16.9 km)", "Tenho interesse"};
                for(int i=0;i<lines.length;i++)canvas.drawText(lines[i],24*d,(45+i*38)*d,paint);
            }
        });
    }
}
