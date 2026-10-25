package io.sajad.chessauto;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
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
import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.nio.ByteBuffer;

/** Consent-driven foreground service; no hidden capture and no network upload. */
public final class ExternalCaptureService extends Service {
    public static final String EXTRA_RESULT = "capture.result";
    public static final String EXTRA_DATA = "capture.data";
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader reader;
    private HandlerThread thread;
    private Handler processing;
    private final Handler main = new Handler(Looper.getMainLooper());
    private long lastFrame;
    private boolean stopping;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("chess_capture",
            "مشاركة شاشة الشطرنج",NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int id) {
        Notification notification = new Notification.Builder(this,"chess_capture")
            .setContentTitle("Chess AI Auto Player")
            .setContentText("مشاركة الشاشة مفعلة لمباراة الكمبيوتر")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build();
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(17,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(17,notification);

        if (intent == null || !intent.hasExtra(EXTRA_DATA)) {
            stopSelf(); return START_NOT_STICKY;
        }
        try {
            Intent data;
            if (Build.VERSION.SDK_INT >= 33)
                data = intent.getParcelableExtra(EXTRA_DATA,Intent.class);
            else data = intent.getParcelableExtra(EXTRA_DATA);
            MediaProjectionManager mpm = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = mpm.getMediaProjection(intent.getIntExtra(EXTRA_RESULT,0),data);
            if (projection == null) throw new IllegalStateException("Screen capture not granted");
            thread = new HandlerThread("Chess-Screen-Capture");
            thread.start();
            processing = new Handler(thread.getLooper());
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() {
                    main.post(() -> stopSelf());
                }
            },main);
            DisplayMetrics metrics = new DisplayMetrics();
            ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(metrics);
            int width=metrics.widthPixels,height=metrics.heightPixels;
            reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2);
            reader.setOnImageAvailableListener(this::onImage,processing);
            virtualDisplay=projection.createVirtualDisplay("ChessOfflineCapture",
                width,height,metrics.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(),null,processing);
            ExternalController.notifyStatus("افتح مباراة كمبيوتر جديدة واضغط تحديد الرقعة");
        } catch (Exception e) {
            ExternalController.notifyStatus("فشل مشاركة الشاشة: "+e.getMessage());
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void onImage(ImageReader src) {
        Image image=null;
        try {
            image=src.acquireLatestImage();
            if (image==null) return;
            long now=android.os.SystemClock.elapsedRealtime();
            if (now-lastFrame < 360) return;
            lastFrame=now;
            Image.Plane plane=image.getPlanes()[0];
            int width=image.getWidth(),height=image.getHeight();
            int pixelStride=plane.getPixelStride(),rowStride=plane.getRowStride();
            Bitmap padded=Bitmap.createBitmap(width+(rowStride-pixelStride*width)/pixelStride,
                height,Bitmap.Config.ARGB_8888);
            ByteBuffer buffer=plane.getBuffer();
            padded.copyPixelsFromBuffer(buffer);
            Bitmap cropped=Bitmap.createBitmap(padded,0,0,width,height);
            padded.recycle();
            main.post(() -> {
                if (ExternalController.active != null) ExternalController.active.onFrame(cropped);
                cropped.recycle();
            });
        } catch (Exception e) {
            ExternalController.notifyStatus("خطأ التقاط الشاشة: "+e.getMessage());
        } finally { if (image!=null) image.close(); }
    }

    @Override public void onDestroy() {
        if (stopping) return;
        stopping=true;
        if (reader!=null) {reader.setOnImageAvailableListener(null,null);}
        if (virtualDisplay!=null) {virtualDisplay.release();virtualDisplay=null;}
        if (reader!=null) {reader.close();reader=null;}
        if (projection!=null) {projection.stop();projection=null;}
        if (thread!=null) {thread.quitSafely();thread=null;}
        if (ExternalController.active!=null) ExternalController.active.stop();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
