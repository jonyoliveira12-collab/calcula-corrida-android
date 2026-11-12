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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/** CI-only synthetic offer pixels; no accounts, networking or accessible offer labels. */
public class OfferFixtureActivity extends Activity {
    private boolean offerVisible=true;
    private BroadcastReceiver offerController;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if(getPackageName().equals("com.nu.production")) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            android.widget.TextView bank = new android.widget.TextView(this);
            bank.setText("TEST FIXTURE - SECURE WINDOW");
            setContentView(bank);
            return;
        }
        if(getPackageName().endsWith(".backdrop")) {
            android.widget.TextView background=new android.widget.TextView(this);
            background.setText("Aplicativo de fundo");
            setContentView(background);
            return;
        }
        final String layout=getIntent().getStringExtra("layout");
        final boolean realistic="dual_realistic".equals(layout);
        final boolean dual="dual".equals(layout) || realistic;
        final boolean floating=dual || "floating".equals(layout);
        View offer=new View(this) {
            final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            boolean marked=false;
            @Override protected void onDraw(Canvas canvas) {
                if(!marked) {
                    marked=true;
                    try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(getFilesDir(),"offer-drawn-at"))) {out.write(Long.toString(System.currentTimeMillis()).getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(java.io.IOException e){throw new RuntimeException(e);}
                }
                boolean dark=(floating && !dual) || getPackageName().equals("com.app99.driver");
                canvas.drawColor(dark?0xff20242c:Color.WHITE);paint.setColor(dark?Color.WHITE:Color.BLACK);
                float d=getResources().getDisplayMetrics().density;paint.setTextSize(18*d);
                if(!offerVisible) {canvas.drawText("Mapa aberto sem chamada ativa",12*d,45*d,paint);return;}
                String[] lines={"R$ 28,81","31 min • 19.1 km","4,76 (164)","6 min (2.2 km)","25 minutos (16.9 km)","Tenho interesse"};
                if("partial".equals(layout))lines=new String[]{"R$ 12,77","19 min • 12.0 km","4,99 (1763)","8 min (5.0 km)","11 minutos (7.0 km)","Tenho interesse"};
                if("partial_dot".equals(layout))lines=new String[]{"R$ 24,00","30 min • 12.0 km • Est. de R$2,00/km","4,90 (100)","10 min (4.0 km)","20 minutos (8.0 km)","Tenho interesse"};
                if("small".equals(layout))lines=new String[]{"R$ 11,03","14 min • 10.8 km","4,92 (120)","4 min (2.9 km)","10 minutos (7.9 km)","Aceitar"};
                if(floating)lines=new String[]{"R$ 17,37","26 min • 16.4 km","4,98 (303)","12 min (9.3 km)","14 minutos (7.1 km)","Tenho interesse"};
                if(getPackageName().equals("com.app99.driver"))lines=new String[]{"Não afeta a TA","R$11,90","R$2,95 Tarifa base dinâmica inicial.","4,79 · +999 corridas · Perfil Premium","10min (4,4km)","12min (6km)"};
                if(getPackageName().equals("sinet.startup.inDriver"))lines=new String[]{"R$ 30,00","30 min • 12.0 km","Nota 4,90","5 min (2.0 km)","25 min (10.0 km)","Aceitar"};
                if(dual && getPackageName().equals("com.ubercab.driver"))lines=new String[]{"UberX Exclusivo","R$ 12,00","20 min • 8.0 km • Est. de R$1,50/km","4,80 (100)","4 min (1.0 km)","16 minutos (7.0 km)","Aceitar"};
                if(dual && getPackageName().equals("com.app99.driver"))lines=new String[]{"Negocia · Pgto. no app","R$30,00","4,90 · 110 corridas · Perfil Essencial","5min (3km)","25min (12km)","Aceitar por R$30,00","R$31,00   R$32,00   R$33,00"};
                if(realistic && getPackageName().equals("com.app99.driver")) {
                    lines=new String[]{"Negocia · Pgto. no app","R$ 16,80","4,89 · 120 corridas · Cartão verif.","7min (2,4km)","Rua Exemplo A","17min (8,8km)","Rua Exemplo B","Aceitar por R$ 16,80","R$17,50   R$18,00   R$18,50"};
                }
                float first=floating?35*d:("small".equals(layout)?getHeight()*0.42f:Math.max(35*d,getHeight()-210*d));
                if(realistic && getPackageName().equals("com.app99.driver"))first=getHeight()*0.40f;
                for(int i=0;i<lines.length;i++)canvas.drawText(lines[i],12*d,first+i*32*d,paint);
            }
        };
        offer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        offer.setContentDescription("partial".equals(layout)?"R$ 12,77 8 min (5.0 km) Nota 4,99 Tenho interesse":
            ("partial_dot".equals(layout)?"R$ 24,00 10 min • 4,0 km Nota 4,90 Tenho interesse":"Synthetic driver offer pixels"));
        offer.setClickable(true);
        offerController=new BroadcastReceiver() {
            @Override public void onReceive(Context context,Intent intent) {
                offerVisible="br.com.calculacorrida.fixture.SHOW_OFFER".equals(intent.getAction());
                offer.invalidate();
            }
        };
        IntentFilter filter=new IntentFilter();
        filter.addAction("br.com.calculacorrida.fixture.SHOW_OFFER");
        filter.addAction("br.com.calculacorrida.fixture.HIDE_OFFER");
        if(android.os.Build.VERSION.SDK_INT>=33)registerReceiver(offerController,filter,Context.RECEIVER_EXPORTED);
        else registerReceiver(offerController,filter);
        if(floating) {
            setContentView(new View(this));
            float d=getResources().getDisplayMetrics().density;
            int screenWidth=getResources().getDisplayMetrics().widthPixels;
            int screenHeight=getResources().getDisplayMetrics().heightPixels;
            int width=realistic?(int)(screenWidth/3-12*d):dual?(int)(screenWidth/2-12*d):(int)(screenWidth-16*d);
            int height=realistic?screenHeight-(int)(80*d):(int)((dual?300:240)*d);
            WindowManager.LayoutParams p=new WindowManager.LayoutParams(width,height,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
            if(dual) {
                p.gravity=Gravity.TOP|Gravity.LEFT;
                p.x=getPackageName().equals("com.app99.driver")?(realistic?screenWidth/3:screenWidth/2)+(int)(4*d):(int)(4*d);
                p.y=(int)((realistic?40:90)*d);
            } else p.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;
            WindowManager wm=(WindowManager)getApplicationContext().getSystemService(WINDOW_SERVICE);
            wm.addView(offer,p);
            if(realistic && getPackageName().equals("com.app99.driver")) {
                // A second, slightly larger 99 window contains only a map. It
                // must not starve the offer window when both are accessible.
                WindowManager.LayoutParams map=new WindowManager.LayoutParams(width,screenHeight-(int)(40*d),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
                map.gravity=Gravity.TOP|Gravity.LEFT;map.x=screenWidth*2/3+(int)(4*d);map.y=(int)(20*d);
                View mapOnly=new View(this);mapOnly.setBackgroundColor(0xff29333a);mapOnly.setContentDescription("Mapa sem oferta");wm.addView(mapOnly,map);
            }
            // A later tiny driver bubble must not be chosen instead of the offer window.
            if(!dual) {
                WindowManager.LayoutParams icon=new WindowManager.LayoutParams((int)(48*d),(int)(48*d),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
                icon.gravity=Gravity.TOP|Gravity.LEFT;
                View bubble=new View(this);bubble.setBackgroundColor(Color.GRAY);wm.addView(bubble,icon);
            }
            startActivity(new android.content.Intent().setClassName("br.com.calculacorrida.fixture.backdrop","br.com.calculacorrida.fixture.OfferFixtureActivity"));
        } else setContentView(offer);
    }

    @Override protected void onDestroy() {
        if(offerController!=null)unregisterReceiver(offerController);
        super.onDestroy();
    }
}
