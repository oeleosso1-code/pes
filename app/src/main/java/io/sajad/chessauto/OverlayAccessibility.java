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
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Explicitly enabled accessibility service for user-approved offline games only. */
public final class OverlayAccessibility extends AccessibilityService {
    static OverlayAccessibility active;
    private WindowManager wm;
    private View boardMarkers, calibration;
    private LinearLayout panel;
    private TextView status;
    private TextView performance;
    private boolean showGuides = true;
    private final Handler main = new Handler(Looper.getMainLooper());
    private String origin, destination;
    private RectF bounds;
    private boolean whiteBottom = true;
    private boolean attached;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        active = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (ExternalController.active != null) attach();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}

    private int dp(float n) { return Math.round(getResources().getDisplayMetrics().density * n); }

    private WindowManager.LayoutParams params(int w, int h, boolean touch) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
            (touch ? 0 : WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(w, h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags, PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.LEFT;
        return p;
    }

    void attach() {
        main.post(() -> {
            if (attached || wm == null) return;
            attached = true;
            boardMarkers = new View(this) {
                final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override protected void onDraw(Canvas canvas) {
                    super.onDraw(canvas);
                    if (!showGuides || bounds == null || origin == null || destination == null) return;
                    paintSquare(canvas, origin, Color.BLACK);
                    paintSquare(canvas, destination, Color.rgb(21, 204, 78));
                }
                private void paintSquare(Canvas canvas, String sq, int color) {
                    int file = sq.charAt(0) - 'a', rank = sq.charAt(1) - '1';
                    int col = whiteBottom ? file : 7-file;
                    int row = whiteBottom ? 7-rank : rank;
                    float d = bounds.width()/8f;
                    float x = bounds.left + col*d, y = bounds.top + row*d;
                    // Thin borders preserve the piece and reduce overlay feedback into capture.
                    pen.setStyle(Paint.Style.STROKE);
                    pen.setStrokeWidth(Math.max(4,d*.085f));
                    pen.setColor(color==Color.BLACK ? Color.rgb(13,13,13) : Color.rgb(0,220,56));
                    canvas.drawRect(x+3,y+3,x+d-3,y+d-3,pen);
                    pen.setStrokeWidth(Math.max(1,d*.018f));
                    pen.setColor(color==Color.BLACK ? Color.WHITE : Color.BLACK);
                    canvas.drawRect(x+7,y+7,x+d-7,y+d-7,pen);
                }
            };
            wm.addView(boardMarkers, params(-1, -1, false));
            panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(7),dp(5),dp(7),dp(5));
            panel.setBackgroundColor(Color.argb(228,22,28,40));
            status = new TextView(this);
            status.setTextColor(Color.WHITE);
            status.setTextSize(12);
            status.setText("افتح لعبة شطرنج ضد الكمبيوتر، وابدأ مباراة جديدة");
            panel.addView(status);
            performance = new TextView(this);
            performance.setTextColor(Color.rgb(16,220,121));
            performance.setTextSize(10);
            performance.setText("مشاركة الشاشة: جاري الإعداد");
            panel.addView(performance);
            LinearLayout row = new LinearLayout(this);
            panel.addView(row);
            Button calibrate = new Button(this);
            calibrate.setText("تحديد الرقعة");
            calibrate.setTextSize(12);
            calibrate.setAllCaps(false);
            row.addView(calibrate, new LinearLayout.LayoutParams(0,dp(42),1));
            calibrate.setOnClickListener(v -> beginCalibration());
            Button reset = new Button(this);
            reset.setText("إعادة");
            reset.setTextSize(12);
            reset.setAllCaps(false);
            row.addView(reset, new LinearLayout.LayoutParams(0,dp(42),1));
            reset.setOnClickListener(v -> {
                if (ExternalController.active != null) ExternalController.active.resetPosition();
            });
            Button pause = new Button(this);
            pause.setText("⏸");
            pause.setTextSize(12);
            pause.setAllCaps(false);
            row.addView(pause,new LinearLayout.LayoutParams(0,dp(42),.7f));
            pause.setOnClickListener(v -> {
                ExternalController c=ExternalController.active;
                if(c!=null) {
                    boolean next=!c.isPaused();
                    c.setPaused(next);
                    pause.setText(next ? "▶" : "⏸");
                }
            });
            Button stop = new Button(this);
            stop.setText("إيقاف");
            stop.setTextSize(12);
            stop.setAllCaps(false);
            row.addView(stop, new LinearLayout.LayoutParams(0,dp(42),1));
            stop.setOnClickListener(v -> {
                if (ExternalController.active != null) ExternalController.active.stop();
                stopService(new android.content.Intent(this,ExternalCaptureService.class));
            });
            WindowManager.LayoutParams p = params(-1, -2, true);
            p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            p.y = dp(40);
            wm.addView(panel,p);
        });
    }

    public void setPerformanceInfo(String message) {
        main.post(() -> {if(performance!=null)performance.setText(message);});
    }

    public void setStatus(String message) {
        main.post(() -> { if (status != null) status.setText(message); });
    }

    public void showMove(String from, String to, RectF rect, boolean whiteDown) {
        main.post(() -> {
            origin = from; destination = to;
            bounds = rect == null ? null : new RectF(rect);
            whiteBottom = whiteDown;
            if (boardMarkers != null) boardMarkers.invalidate();
        });
    }

    private void beginCalibration() {
        if (calibration != null || wm == null) return;
        final float[] corner = new float[2];
        final int[] clicks = {0};
        setStatus("المعايرة: المس الزاوية العليا اليسرى من الرقعة ثم الزاوية السفلى اليمنى");
        calibration = new View(this) {
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c) {
                c.drawColor(Color.argb(43,0,170,110));
                paint.setColor(Color.WHITE); paint.setTextSize(dp(18)); paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                c.drawText(clicks[0]==0 ? "1: المس أعلى يسار الرقعة" : "2: المس أسفل يمين الرقعة", dp(24),dp(200),paint);
            }
            @Override public boolean onTouchEvent(MotionEvent e) {
                if (e.getAction()!=MotionEvent.ACTION_UP) return true;
                float x=e.getRawX(),y=e.getRawY();
                if (clicks[0]++ == 0) {
                    corner[0]=x; corner[1]=y;
                    invalidate();
                } else {
                    RectF rect = new RectF(corner[0],corner[1],x,y);
                    if (wm != null && calibration != null) {
                        wm.removeView(calibration); calibration=null;
                    }
                    if (ExternalController.active != null) ExternalController.active.calibrate(rect);
                }
                return true;
            }
        };
        wm.addView(calibration,params(-1,-1,true));
    }

    private float[] centerOf(String square, RectF rect, boolean whiteDown) {
        int file = square.charAt(0)-'a', rank=square.charAt(1)-'1';
        int x=whiteDown?file:7-file,y=whiteDown?7-rank:rank;
        float d=rect.width()/8f;
        return new float[]{rect.left+(x+.5f)*d,rect.top+(y+.5f)*d};
    }

    public void executeMove(String uci, RectF rect, boolean whiteDown, boolean drag) {
        main.post(() -> {
            if (!attached || uci.length()<4) return;
            float[] from=centerOf(uci.substring(0,2),rect,whiteDown);
            float[] to=centerOf(uci.substring(2,4),rect,whiteDown);
            GestureDescription gesture;
            if(drag) {
                Path route=new Path();
                route.moveTo(from[0],from[1]);
                route.lineTo(to[0],to[1]);
                gesture=new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(route,0,330)).build();
            } else {
                Path p1=new Path(); p1.moveTo(from[0],from[1]);
                Path p2=new Path(); p2.moveTo(to[0],to[1]);
                gesture=new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(p1,0,90))
                    .addStroke(new GestureDescription.StrokeDescription(p2,210,90)).build();
            }
            boolean submitted=dispatchGesture(gesture,new GestureResultCallback() {
                @Override public void onCancelled(GestureDescription d) {
                    if (ExternalController.active != null) ExternalController.active.gestureFailed();
                }
            },main);
            if (!submitted && ExternalController.active != null)
                ExternalController.active.gestureFailed();
        });
    }

    void detach() {
        main.post(() -> {
            if (wm == null) return;
            if (calibration != null) { wm.removeView(calibration);calibration=null; }
            if (panel != null) { wm.removeView(panel);panel=null;status=null;performance=null; }
            if (boardMarkers != null) { wm.removeView(boardMarkers);boardMarkers=null; }
            attached = false; origin = null; destination = null;
        });
    }

    @Override public void onDestroy() {
        detach();
        active = null;
        super.onDestroy();
    }
}
