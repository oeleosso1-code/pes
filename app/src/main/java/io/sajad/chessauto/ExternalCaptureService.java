package io.sajad.chessauto;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.nio.ByteBuffer;

/**
 * User-consented full-display capture. Image processing is off UI thread.
 * A single MediaProjection creates one VirtualDisplay, resized with setSurface.
 */
public final class ExternalCaptureService extends Service {
    public static final String EXTRA_RESULT = "capture.result";
    public static final String EXTRA_DATA = "capture.data";
    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader reader;
    private HandlerThread thread;
    private Handler processing;
    private int width, height, density;
    private boolean stopping;
    private long lastFrame;
    private long lastWarning;
    private long frameCounter, lastRateTime;
    private boolean firstImageReported;

    private void report(String message) {
        getSharedPreferences("chess_capture_diagnostics",MODE_PRIVATE).edit()
            .putString("status",message)
            .putLong("timestamp",SystemClock.elapsedRealtime())
            .apply();
        ExternalController.notifyStatus(message);
        android.util.Log.i("ChessCapture",message);
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("chess_capture_v3",
            "مشاركة شاشة الشطرنج", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = new Notification.Builder(this,"chess_capture_v3")
            .setContentTitle("Chess AI Auto Player 3")
            .setContentText("مشاركة الشاشة مفعلة — لإيقافها استخدم الشريط العائم")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true).build();
        try {
            if (Build.VERSION.SDK_INT >= 29)
                startForeground(17,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            else startForeground(17,n);
            if (intent == null || !intent.hasExtra(EXTRA_DATA))
                throw new IllegalStateException("لم تُمنح صلاحية مشاركة الشاشة");
            Intent token;
            if (Build.VERSION.SDK_INT >= 33)
                token = intent.getParcelableExtra(EXTRA_DATA,Intent.class);
            else token = intent.getParcelableExtra(EXTRA_DATA);
            if (token == null) throw new IllegalStateException("بيانات مشاركة الشاشة مفقودة");
            thread = new HandlerThread("ChessCaptureWorker");
            thread.start();
            processing = new Handler(thread.getLooper());
            WindowManager wm = (WindowManager)getSystemService(WINDOW_SERVICE);
            DisplayMetrics dm = getResources().getDisplayMetrics();
            density = dm.densityDpi;
            if (Build.VERSION.SDK_INT >= 30) {
                Rect b = wm.getMaximumWindowMetrics().getBounds();
                width = b.width(); height = b.height();
            } else {
                DisplayMetrics metrics = new DisplayMetrics();
                wm.getDefaultDisplay().getRealMetrics(metrics);
                width = metrics.widthPixels; height = metrics.heightPixels;
            }
            if (width <= 0 || height <= 0)
                throw new IllegalStateException("أبعاد الشاشة غير صالحة");
            MediaProjectionManager manager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection=manager.getMediaProjection(intent.getIntExtra(EXTRA_RESULT,0),token);
            if (projection == null) throw new IllegalStateException("رفض Android مشاركة الشاشة");
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() {
                    report("توقفت مشاركة الشاشة. أعد الموافقة لبدء جلسة جديدة");
                    main.post(() -> stopSelf());
                }
                @Override public void onCapturedContentResize(int w,int h) {
                    if (processing != null) processing.post(() -> resizeCapture(w,h));
                }
            },main);
            reader=newReader(width,height);
            // Exactly ONE createVirtualDisplay per user-consent token.
            virtualDisplay=projection.createVirtualDisplay("ChessOfflineFullDisplay",
                width,height,density,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(),null,processing);
            lastRateTime=SystemClock.elapsedRealtime();
            report("✓ خدمة مشاركة الشاشة بدأت. ننتظر وصول الصور...");
            return START_NOT_STICKY;
        } catch (Exception e) {
            report("فشل مشاركة الشاشة: "+e.getMessage());
            stopSelf();
            return START_NOT_STICKY;
        }
    }

    private ImageReader newReader(int w,int h) {
        ImageReader r=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);
        r.setOnImageAvailableListener(this::onImage,processing);
        return r;
    }

    private void resizeCapture(int w,int h) {
        if (stopping || virtualDisplay==null || w<100 || h<100) return;
        if (w==width && h==height) return;
        try {
            ImageReader replacement=newReader(w,h);
            // Do not request a second VirtualDisplay on Android 14+.
            virtualDisplay.resize(w,h,density);
            virtualDisplay.setSurface(replacement.getSurface());
            ImageReader old=reader;
            reader=replacement;
            width=w; height=h;
            if (old!=null) {
                old.setOnImageAvailableListener(null,null);
                old.close();
            }
            ExternalController c=ExternalController.active;
            if (c!=null) c.onCaptureResized(w,h);
            report("تغيّر حجم العرض. أعد تحديد الرقعة لضمان دقة اللمس");
        } catch (Exception e) {
            report("فشل إعادة قياس الشاشة: "+e.getMessage());
            main.post(() -> stopSelf());
        }
    }

    private void onImage(ImageReader source) {
        Image frame=null;
        Bitmap padded=null,cropped=null;
        try {
            frame=source.acquireLatestImage();
            if (frame==null || stopping) return;
            long now=SystemClock.elapsedRealtime();
            if(now-lastFrame<120) return; // Target up to ~8 fps without expensive constant 60 fps processing.
            lastFrame=now;
            Image.Plane plane=frame.getPlanes()[0];
            int w=frame.getWidth(),h=frame.getHeight();
            int pixelStride=plane.getPixelStride(),stride=plane.getRowStride();
            if (pixelStride!=4 || stride<4*w) return;
            ByteBuffer bytes=plane.getBuffer();
            padded=Bitmap.createBitmap(stride/pixelStride,h,Bitmap.Config.ARGB_8888);
            padded.copyPixelsFromBuffer(bytes);
            cropped=Bitmap.createBitmap(padded,0,0,w,h);
            // Avoid mistaking a dark chess theme for a protected window.
            int dark=0;
            for(int sy=1;sy<=4;sy++)for(int sx=1;sx<=4;sx++){
                int color=cropped.getPixel(w*sx/5,h*sy/5)&0xffffff;
                int max=Math.max((color>>16)&255,Math.max((color>>8)&255,color&255));
                if(max<7)dark++;
            }
            if (dark>=16) {
                if (now-lastWarning>3000) {
                    report("الصورة سوداء أو محمية؛ أوقف التحليل وافحص إذن المشاركة");
                    lastWarning=now;
                }
                return;
            }
            if(!firstImageReported){
                firstImageReported=true;
                report("✓ وصلت أول صورة من الشاشة ("+w+"×"+h+"). يمكنك معايرة الرقعة.");
            }
            ExternalController c=ExternalController.active;
            if(c!=null) {
                c.onFrame(cropped);
                frameCounter++;
                if(now-lastRateTime>4000) {
                    double fps=frameCounter*1000.0/(now-lastRateTime);
                    c.onFrameRate(fps);
                    frameCounter=0;lastRateTime=now;
                }
            }
        } catch (Exception e) {
            if(SystemClock.elapsedRealtime()-lastWarning>3000) {
                report("خطأ قراءة الصورة: "+e.getMessage());
                lastWarning=SystemClock.elapsedRealtime();
            }
        } finally {
            if(cropped!=null)cropped.recycle();
            if(padded!=null)padded.recycle();
            if(frame!=null)frame.close();
        }
    }

    @Override public void onDestroy() {
        stopping=true;
        if(reader!=null)reader.setOnImageAvailableListener(null,null);
        if(virtualDisplay!=null){virtualDisplay.release();virtualDisplay=null;}
        if(reader!=null){reader.close();reader=null;}
        if(projection!=null){projection.stop();projection=null;}
        if(thread!=null){thread.quitSafely();thread=null;processing=null;}
        if(ExternalController.active!=null)ExternalController.active.stop();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) {return null;}
}
