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

/** Foreground, consent-gated, local-only screen capture. Never silently starts or persists. */
public final class ExternalCaptureService extends Service {
    public static final String EXTRA_RESULT = "capture.result";
    public static final String EXTRA_DATA = "capture.data";
    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader reader;
    private HandlerThread thread;
    private Handler processing;
    private long lastFrame;
    private volatile boolean stopping;
    private int currentWidth, currentHeight, densityDpi;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel("chess_capture",
            "مشاركة الشاشة للشطرنج ضد الكمبيوتر", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !intent.hasExtra(EXTRA_DATA) || projection != null) {
            if (projection == null) stopSelf();
            return START_NOT_STICKY;
        }
        Notification n = new Notification.Builder(this, "chess_capture")
            .setContentTitle("Chess AI 3 — مشاركة الشاشة")
            .setContentText("تعمل فقط بإذنك. افتح مباراة ضد الكمبيوتر")
            .setSmallIcon(android.R.drawable.ic_menu_view).setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(17, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(17,n);
        try {
            Intent data = Build.VERSION.SDK_INT >= 33
                ? intent.getParcelableExtra(EXTRA_DATA, Intent.class)
                : intent.getParcelableExtra(EXTRA_DATA);
            if (data == null) throw new IllegalStateException("Capture consent missing");
            MediaProjectionManager mgr =
                (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = mgr.getMediaProjection(intent.getIntExtra(EXTRA_RESULT,0),data);
            if (projection == null) throw new IllegalStateException("MediaProjection null");
            thread = new HandlerThread("ChessVisionWorker");
            thread.start();
            processing = new Handler(thread.getLooper());

            DisplayMetrics screen = getResources().getDisplayMetrics();
            densityDpi = screen.densityDpi;
            WindowManager wm = (WindowManager)getSystemService(WINDOW_SERVICE);
            if (Build.VERSION.SDK_INT >= 30) {
                Rect r = wm.getMaximumWindowMetrics().getBounds();
                currentWidth = r.width();
                currentHeight = r.height();
            } else {
                android.graphics.Point p = new android.graphics.Point();
                wm.getDefaultDisplay().getRealSize(p);
                currentWidth = p.x; currentHeight = p.y;
            }
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() {
                    ExternalController.notifyStatus("توقفت مشاركة الشاشة. يلزم إعطاء إذن جديد");
                    main.post(() -> stopSelf());
                }
                @Override public void onCapturedContentResize(int width,int height) {
                    if (processing != null) processing.post(() -> resizeCapture(width,height));
                }
            }, main);
            reader = newReader(currentWidth,currentHeight);
            virtualDisplay = projection.createVirtualDisplay("ChessVisionFullDisplay",
                currentWidth,currentHeight,densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(),null,processing);
            ExternalController.notifyStatus("المشاركة مفعّلة. افتح لعبة ضد الكمبيوتر ثم عاير الرقعة");
        } catch (Exception ex) {
            ExternalController.notifyStatus("فشل تشغيل مشاركة الشاشة: " + ex.getMessage());
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private ImageReader newReader(int w,int h) {
        ImageReader r = ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);
        r.setOnImageAvailableListener(this::onImage,processing);
        return r;
    }

    private void resizeCapture(int w,int h) {
        if (stopping || w<=0 || h<=0 || virtualDisplay == null ||
            (w==currentWidth && h==currentHeight)) return;
        ImageReader previous = reader;
        try {
            ImageReader resized = newReader(w,h);
            virtualDisplay.resize(w,h,densityDpi);
            virtualDisplay.setSurface(resized.getSurface());
            reader = resized;
            currentWidth=w; currentHeight=h;
            if (previous != null) {
                previous.setOnImageAvailableListener(null,null);
                previous.close();
            }
            ExternalController ctl = ExternalController.active;
            if (ctl != null) ctl.onCaptureResize();
            ExternalController.notifyStatus("تغير حجم العرض. أعد تحديد الرقعة للتزامن");
        } catch (Exception ex) {
            ExternalController.notifyStatus("فشل ضبط حجم الشاشة: " + ex.getMessage());
            main.post(() -> stopSelf());
        }
    }

    private void onImage(ImageReader source) {
        Image frame = null;
        try {
            if (stopping || source != reader) return;
            frame = source.acquireLatestImage();
            if (frame == null) return;
            long now=SystemClock.elapsedRealtime();
            if (now-lastFrame < 125) return; // adaptive target up to ~8 fps
            lastFrame=now;
            Image.Plane plane=frame.getPlanes()[0];
            int w=frame.getWidth(),h=frame.getHeight();
            int stride=plane.getPixelStride(),row=plane.getRowStride();
            if (w<=0||h<=0||stride!=4||row<4*w) return;
            Bitmap padded=Bitmap.createBitmap(row/stride,h,Bitmap.Config.ARGB_8888);
            ByteBuffer buffer=plane.getBuffer();
            buffer.rewind();
            try {
                padded.copyPixelsFromBuffer(buffer);
                Bitmap bitmap=Bitmap.createBitmap(padded,0,0,w,h);
                try {
                    ExternalController ctl=ExternalController.active;
                    if (ctl!=null) ctl.onFrame(bitmap); // process on worker, not UI thread
                } finally { bitmap.recycle(); }
            } finally { padded.recycle(); }
        } catch (Exception ex) {
            ExternalController.notifyStatus("فشل التقاط الإطار: " + ex.getMessage());
        } finally {
            if (frame!=null) frame.close();
        }
    }

    @Override public void onDestroy() {
        if (stopping) { super.onDestroy(); return; }
        stopping = true;
        if (reader!=null) reader.setOnImageAvailableListener(null,null);
        if (virtualDisplay!=null) {virtualDisplay.release();virtualDisplay=null;}
        if (reader!=null) {reader.close();reader=null;}
        if (projection!=null) {projection.stop();projection=null;}
        if (thread!=null) {thread.quitSafely();thread=null;}
        if (ExternalController.active!=null) ExternalController.active.stop();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {return null;}
}
