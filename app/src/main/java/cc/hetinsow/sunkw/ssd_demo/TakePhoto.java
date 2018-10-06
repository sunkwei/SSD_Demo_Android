package cc.hetinsow.sunkw.ssd_demo;

import android.app.Service;
import android.content.Intent;
import android.graphics.Camera;
import android.os.IBinder;
import android.os.StrictMode;

/**
 * 周期获取摄像头图片, 并且进行分析, 将分析结果和图片在 Activity 中显示 ...
 */
public class TakePhoto extends Service {

    public TakePhoto() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        Thread mThread = null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        return super.onStartCommand(intent, flags, startId);
    }
}
