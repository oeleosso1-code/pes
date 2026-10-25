package io.sajad.chessauto;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Visible, consent-enabled accessibility overlay, used for OFFLINE computer chess only. */
public final class OverlayAccessibility extends AccessibilityService {
    private static final String TAG="ChessOverlay";
    static volatile OverlayAccessibility active;
    private WindowManager wm;
    private View boardMarkers, calibration;
    private LinearLayout panel;
    private TextView status,performance;
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile boolean attached, demo, guidesEnabled=true;
    private volatile String origin,destination;
    private volatile RectF bounds;
    private volatile boolean whiteBottom=true;

    public static String lastDiagnostic(android.content.Context c) {
        return c.getSharedPreferences("chess_diagnostic",MODE_PRIVATE)
            .getString("last","خدمة إمكانية الوصول لم تتصل بعد");
    }

    private void report(String msg) {
        getSharedPreferences("chess_diagnostic",MODE_PRIVATE)
            .edit().putString("last",msg).apply();
        Log.i(TAG,msg);
        if(status!=null)status.setText(msg);
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        active=this;
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        report("✓ خدمة إمكانية الوصول متصلة");
        if(ExternalController.active!=null)attach();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {report("توقفت خدمة إمكانية الوصول");}

    private int dp(float n){return Math.round(getResources().getDisplayMetrics().density*n);}

    private WindowManager.LayoutParams params(int w,int h,boolean touch) {
        int flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            |WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            |(touch?0:WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(w,h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.LEFT;
        return p;
    }

    private Button action(String label,LinearLayout row) {
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);
        b.setTextSize(10);b.setMinWidth(0);b.setMinimumWidth(0);
        b.setPadding(0,0,0,0);
        row.addView(b,new LinearLayout.LayoutParams(0,dp(43),1f));
        return b;
    }

    void attach() {
        main.post(()->{
            if(attached || wm==null)return;
            try {
                boardMarkers=new View(this) {
                    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
                    @Override protected void onDraw(Canvas canvas){
                        super.onDraw(canvas);
                        RectF r=bounds;
                        if(!guidesEnabled || r==null || origin==null || destination==null)return;
                        paintSquare(canvas,origin,Color.BLACK,r);
                        paintSquare(canvas,destination,Color.rgb(0,215,75),r);
                    }
                    private void paintSquare(Canvas canvas,String square,int color,RectF r) {
                        if(square==null||square.length()!=2)return;
                        int file=square.charAt(0)-'a',rank=square.charAt(1)-'1';
                        if(file<0||file>7||rank<0||rank>7)return;
                        int col=whiteBottom?file:7-file;
                        int row=whiteBottom?7-rank:rank;
                        float d=r.width()/8f;
                        float x=r.left+col*d,y=r.top+row*d;
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(Math.max(dp(3),d*.075f));
                        p.setColor(color);
                        canvas.drawRect(x+3,y+3,x+d-3,y+d-3,p);
                        p.setStrokeWidth(Math.max(dp(1),d*.018f));
                        p.setColor(color==Color.BLACK?Color.WHITE:Color.BLACK);
                        canvas.drawRect(x+7,y+7,x+d-7,y+d-7,p);
                    }
                };
                wm.addView(boardMarkers,params(-1,-1,false));
                panel=new LinearLayout(this);
                panel.setOrientation(LinearLayout.VERTICAL);
                panel.setPadding(dp(4),dp(4),dp(4),dp(4));
                panel.setBackgroundColor(Color.argb(236,9,26,36));
                status=new TextView(this);
                status.setTextSize(12);status.setTextColor(Color.WHITE);
                status.setText("✓ خدمة التأشير متصلة. اضغط اختبار قبل المعايرة");
                panel.addView(status);
                performance=new TextView(this);
                performance.setTextSize(10);performance.setTextColor(Color.rgb(75,235,148));
                performance.setText("التقاط: بانتظار الإطارات");
                panel.addView(performance);
                LinearLayout row=new LinearLayout(this);panel.addView(row);
                Button calibrate=action("معايرة",row);
                calibrate.setOnClickListener(v->beginCalibration());
                Button test=action("اختبار التأشير",row);
                test.setOnClickListener(v->showDemoMarkers());
                Button pause=action("⏸",row);
                pause.setOnClickListener(v->{
                    ExternalController c=ExternalController.active;
                    if(c!=null){
                        boolean next=!c.isPaused();c.setPaused(next);
                        pause.setText(next?"▶":"⏸");
                    }else report("لم تبدأ جلسة مشاركة الشاشة");
                });
                Button stop=action("إنهاء",row);
                stop.setOnClickListener(v->{
                    if(ExternalController.active!=null)ExternalController.active.stop();
                    stopService(new android.content.Intent(this,ExternalCaptureService.class));
                    report("تم إيقاف مشاركة الشاشة");
                });
                WindowManager.LayoutParams panelParams=params(-1,-2,true);
                panelParams.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;
                panelParams.y=dp(22);
                wm.addView(panel,panelParams);
                attached=true;report("✓ الشريط العائم ظهر بنجاح. اختبر التأشير ثم حدد الرقعة");
            }catch(Exception e){
                Log.e(TAG,"Failed to install accessibility overlay",e);
                // A half-created overlay must never make future attempts silently fail.
                safeRemove(calibration);calibration=null;
                safeRemove(panel);panel=null;status=null;performance=null;
                safeRemove(boardMarkers);boardMarkers=null;
                attached=false;
                report("فشل عرض الشريط: "+e.getClass().getSimpleName()+" "+e.getMessage());
                Toast.makeText(this,"فشل الشريط العائم: "+e.getMessage(),Toast.LENGTH_LONG).show();
            }
        });
    }

    public void setStatus(String message) {
        main.post(()->{
            if(status!=null)status.setText(message);
            getSharedPreferences("chess_diagnostic",MODE_PRIVATE).edit()
                .putString("capture",message).apply();
        });
    }

    public void setPerformanceInfo(String message) {
        main.post(()->{if(performance!=null)performance.setText(message);});
    }

    public void showMove(String from,String to,RectF r,boolean whiteDown) {
        main.post(()->{
            demo=false;origin=from;destination=to;
            bounds=r==null?null:new RectF(r);
            whiteBottom=whiteDown;
            if(boardMarkers!=null)boardMarkers.invalidate();
        });
    }

    /** Explicit visual self-test, independent of MediaProjection and piece recognition. */
    public void showDemoMarkers() {
        attach();
        main.postDelayed(()->{
            if(!attached||wm==null)return;
            android.graphics.Rect frame;
            if(android.os.Build.VERSION.SDK_INT>=30)
                frame=wm.getMaximumWindowMetrics().getBounds();
            else{
                android.graphics.Point p=new android.graphics.Point();
                wm.getDefaultDisplay().getRealSize(p);
                frame=new android.graphics.Rect(0,0,p.x,p.y);
            }
            float d=Math.min(frame.width()*.85f,frame.height()*.56f);
            float left=(frame.width()-d)/2f;
            float top=(frame.height()-d)/2f;
            demo=true;guidesEnabled=true;
            origin="e2";destination="e4";whiteBottom=true;
            bounds=new RectF(left,top,left+d,top+d);
            if(boardMarkers!=null)boardMarkers.invalidate();
            report("اختبار ناجح للشريط: يجب رؤية إطار أسود وأخضر في وسط الشاشة. المعايرة تلغي الاختبار");
        },160);
    }

    private void beginCalibration() {
        if(calibration!=null||wm==null)return;
        demo=false;origin=null;destination=null;bounds=null;
        if(boardMarkers!=null)boardMarkers.invalidate();
        final float[] start=new float[2];final int[] touches={0};
        setStatus("المعايرة: الزاوية العليا اليسرى، ثم السفلى اليمنى");
        calibration=new View(this){
            final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c) {
                c.drawColor(Color.argb(48,15,155,80));
                p.setColor(Color.WHITE);p.setTextSize(dp(19));
                c.drawText(touches[0]==0?"1: أعلى يسار الرقعة":"2: أسفل يمين الرقعة",dp(14),dp(205),p);
            }
            @Override public boolean onTouchEvent(MotionEvent e){
                if(e.getAction()!=MotionEvent.ACTION_UP)return true;
                float x=e.getRawX(),y=e.getRawY();
                if(touches[0]++==0){start[0]=x;start[1]=y;invalidate();}
                else{
                    safeRemove(calibration);calibration=null;
                    if(ExternalController.active!=null)
                        ExternalController.active.calibrate(new RectF(start[0],start[1],x,y));
                    else setStatus("ابدأ مشاركة الشاشة قبل المعايرة");
                }
                return true;
            }
        };
        try{wm.addView(calibration,params(-1,-1,true));}
        catch(Exception e){calibration=null;report("فشل فتح المعايرة: "+e.getMessage());}
    }

    private float[] centerOf(String square,RectF r,boolean whiteDown){
        int file=square.charAt(0)-'a',rank=square.charAt(1)-'1';
        int x=whiteDown?file:7-file,y=whiteDown?7-rank:rank;
        float w=r.width()/8f,h=r.height()/8f;
        return new float[]{r.left+(x+.5f)*w,r.top+(y+.5f)*h};
    }

    private void tap(float x,float y,GestureResultCallback callback){
        Path p=new Path();p.moveTo(x,y);
        GestureDescription g=new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(p,0,70)).build();
        if(!dispatchGesture(g,callback,main))report("تعذر إرسال إيماءة لمس إلى Android");
    }

    /** Only used after the user taps 'test touch' and can observe a target inside this app. */
    public void testTouch(float x,float y) {
        main.post(()->{
            if(!attached)attach();
            tap(x,y,new GestureResultCallback(){
                @Override public void onCompleted(GestureDescription g){report("✓ أرسل Android لمسة اختبار، تحقق أن الزر استجاب");}
                @Override public void onCancelled(GestureDescription g){report("فشل لمس الاختبار: ألغاه النظام");}
            });
        });
    }

    public void executeMove(String uci,RectF r,boolean whiteDown,boolean drag){
        main.post(()->{
            if(!attached||uci==null||uci.length()<4||r==null){
                if(ExternalController.active!=null)ExternalController.active.gestureFailed();
                return;
            }
            float[] from=centerOf(uci.substring(0,2),r,whiteDown);
            float[] to=centerOf(uci.substring(2,4),r,whiteDown);
            GestureResultCallback callback=new GestureResultCallback(){
                @Override public void onCancelled(GestureDescription g){
                    if(ExternalController.active!=null)ExternalController.active.gestureFailed();
                    setStatus("ألغى النظام اللمسة. أوقف التشغيل وتحقق من الخدمة");
                }
            };
            if(drag){
                Path p=new Path();p.moveTo(from[0],from[1]);
                p.lineTo(to[0],to[1]);
                GestureDescription g=new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(p,0,370)).build();
                if(!dispatchGesture(g,callback,main)&&ExternalController.active!=null)
                    ExternalController.active.gestureFailed();
            }else{
                // Sequential callbacks: some chess apps ignore two strokes submitted at once.
                tap(from[0],from[1],new GestureResultCallback(){
                    @Override public void onCompleted(GestureDescription first) {
                        main.postDelayed(()->{
                            ExternalController c=ExternalController.active;
                            if(c==null||c.isPaused())return;
                            tap(to[0],to[1],callback);
                        },145);
                    }
                    @Override public void onCancelled(GestureDescription first){
                        if(ExternalController.active!=null)ExternalController.active.gestureFailed();
                    }
                });
            }
        });
    }

    private void safeRemove(View v) {
        if(v!=null && wm!=null)try{wm.removeView(v);}catch(Exception ignored){}
    }

    void detach(){
        main.post(()->{
            safeRemove(calibration);calibration=null;
            safeRemove(panel);panel=null;status=null;performance=null;
            safeRemove(boardMarkers);boardMarkers=null;
            attached=false;origin=null;destination=null;bounds=null;demo=false;
        });
    }

    @Override public void onDestroy(){
        detach();active=null;
        getSharedPreferences("chess_diagnostic",MODE_PRIVATE).edit()
            .putString("last","خدمة إمكانية الوصول توقفت").apply();
        super.onDestroy();
    }
}
