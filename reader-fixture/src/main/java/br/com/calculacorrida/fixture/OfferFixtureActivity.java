package br.com.calculacorrida.fixture;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.WindowManager;
import android.view.Gravity;
import android.graphics.PixelFormat;

/** CI-only synthetic offer pixels; no accounts, networking or accessible offer labels. */
public class OfferFixtureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        final String layout=getIntent().getStringExtra("layout");
        final boolean floating="floating".equals(layout);
        View offer=new View(this) {
            final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            boolean marked=false;
            @Override protected void onDraw(Canvas canvas) {
                if(!marked) {
                    marked=true;
                    try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(getFilesDir(),"offer-drawn-at"))) {out.write(Long.toString(System.currentTimeMillis()).getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(java.io.IOException e){throw new RuntimeException(e);}
                }
                boolean dark=floating || getPackageName().equals("com.taxis99");
                canvas.drawColor(dark?0xff20242c:Color.WHITE);paint.setColor(dark?Color.WHITE:Color.BLACK);
                float d=getResources().getDisplayMetrics().density;paint.setTextSize(18*d);
                String[] lines={"R$ 28,81","31 min • 19.1 km","4,76 (164)","6 min (2.2 km)","25 minutos (16.9 km)","Tenho interesse"};
                if("small".equals(layout))lines=new String[]{"R$ 11,03","14 min • 10.8 km","4,92 (120)","4 min (2.9 km)","10 minutos (7.9 km)","Aceitar"};
                if(floating)lines=new String[]{"R$ 17,37","26 min • 16.4 km","4,98 (303)","12 min (9.3 km)","14 minutos (7.1 km)","Tenho interesse"};
                if(getPackageName().equals("com.taxis99"))lines=new String[]{"Não afeta a TA","R$11,90","R$2,95 Tarifa base dinâmica inicial.","4,79 · +999 corridas · Perfil Premium","10min (4,4km)","12min (6km)"};
                if(getPackageName().equals("sinet.startup.inDriver"))lines=new String[]{"R$ 30,00","30 min • 12.0 km","Nota 4,90","5 min (2.0 km)","25 min (10.0 km)","Aceitar"};
                for(int i=0;i<lines.length;i++)canvas.drawText(lines[i],12*d,(35+i*32)*d,paint);
            }
        };
        if(floating) {
            setContentView(new View(this));
            float d=getResources().getDisplayMetrics().density;
            WindowManager.LayoutParams p=new WindowManager.LayoutParams((int)(getResources().getDisplayMetrics().widthPixels-16*d),(int)(240*d),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
            p.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;
            WindowManager wm=(WindowManager)getSystemService(WINDOW_SERVICE);
            wm.addView(offer,p);
            // A later tiny driver bubble must not be chosen instead of the offer window.
            WindowManager.LayoutParams icon=new WindowManager.LayoutParams((int)(48*d),(int)(48*d),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
            icon.gravity=Gravity.TOP|Gravity.LEFT;
            View bubble=new View(this);bubble.setBackgroundColor(Color.GRAY);wm.addView(bubble,icon);
            startActivity(new android.content.Intent(android.provider.Settings.ACTION_SETTINGS));
        } else setContentView(offer);
    }
}
