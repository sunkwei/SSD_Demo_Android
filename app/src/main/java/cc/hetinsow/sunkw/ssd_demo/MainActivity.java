package cc.hetinsow.sunkw.ssd_demo;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;


import com.hzitoun.camera2SecretPictureTaker.listeners.PictureCapturingListener;
import com.hzitoun.camera2SecretPictureTaker.services.APictureCapturingService;
import com.hzitoun.camera2SecretPictureTaker.services.PictureCapturingServiceImpl;

import org.dmlc.mxnet.Predictor;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class MainActivity extends AppCompatActivity implements PictureCapturingListener {

    private static final int MY_CAMERA_PERMISSION_CODE = 100;
    private static final int CAMERA_REQUEST = 1888;
    private ShowResultView image_view_ = null;
    private Predictor predictor_ = null;
    private Bitmap curr_img_ = null;
    private float threshold_ = 0.2f;     // SSD mobilenet1.0 voc
    private String curr_photo_path_ = null;
    private ArrayList<String> labels_ = new ArrayList<>();
    private APictureCapturingService take_photo_ = null;

//    private int WIDTH = 512;
//    private int HEIGHT = 512;
//    private String sym_fname = "ssd_inceptionv3_512-symbol.json";
//    private String par_fname = "ssd_inceptionv3_512-0001.params";

//    private int WIDTH = 416;
//    private int HEIGHT = 416;
//    private String sym_fname = "yolo3_dardnet53_voc-symbol.json";
//    private String par_fname = "yolo3_dardnet53_voc-0000.params";

    private int WIDTH = 512;
    private int HEIGHT = 512;
    private String sym_fname = "ssd-mobile-512-symbol.json";
    private String par_fname = "ssd-mobile-512-0000.params";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        predictor_ = init_predictor();

        this.image_view_ = (ShowResultView) this.findViewById(R.id.image_view);

        // 需要的权限
        ArrayList<String> permissions = new ArrayList<>();

        if (!check_permission(this, Manifest.permission.CAMERA)) {
            permissions.add(Manifest.permission.CAMERA);
        }

        if (!check_permission(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        if (!check_permission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        if (permissions.size() > 0) {
            open_permission(this, permissions, 101);
        }

        take_photo_ = PictureCapturingServiceImpl.getInstance(this);
    }

    private boolean check_permission(Context context, String permission)
    {
        try {
            if (PermissionChecker.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        catch (Exception ignored) {}
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 101:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "permission granted", Toast.LENGTH_LONG).show();
                }
                else {
                    Toast.makeText(this, "permission denied", Toast.LENGTH_LONG).show();
                }
        }
    }

    private void open_permission(Activity activity, List<String> ps, int requestcode) {
        try {
            String []permissions = new String[ps.size()];
            permissions = ps.toArray(permissions);
            requestPermissions(permissions, requestcode);
        }
        catch (Exception ignored) {}
    }

    void btnCaptureClick(View v)
    {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        File imagePath = new File(Environment.getExternalStorageDirectory(), "images");
        if (!imagePath.exists()) {
            imagePath.mkdirs();
        }

        File newFile = new File(imagePath, "default_image.jpg");
        final Uri contentUri = FileProvider.getUriForFile(getBaseContext(), getPackageName()+".fileprovider", newFile);
        curr_photo_path_ = newFile.getAbsolutePath();

        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, contentUri);
        startActivityForResult(cameraIntent, CAMERA_REQUEST);
    }

    void btnDetectClick(View v)
    {
        Toast.makeText(getBaseContext(), "begin detecting ...", Toast.LENGTH_LONG).show();
        if (curr_img_ == null) {
            Toast.makeText(getBaseContext(), "to capture first!!", Toast.LENGTH_LONG).show();
            return;
        }
        ArrayList<ActionBox> acts = do_detect(curr_img_);
        Toast.makeText(getBaseContext(), "OK", Toast.LENGTH_LONG).show();

        image_view_.set_action_boxes(acts);
    }


    @Override
    public void onCaptureDone(String pictureUrl, byte[] pictureData) {

    }

    @Override
    public void onDoneCapturingAllPhotos(TreeMap<String, byte[]> picturesTaken) {
        Button me = findViewById(R.id.button_auto);
        me.setEnabled(true);
    }


    void btnAutoClick(View v)
    {
        take_photo_.startCapturing(this);
        Button me = findViewById(R.id.button_auto);
        me.setEnabled(false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK) {
            Bitmap photo = BitmapFactory.decodeFile(curr_photo_path_);

            // FIXME: 照理说, 这部分应该从 ShowResultView 获取目标大小 ...
            // scale img
            int view_width = this.image_view_.getWidth();
            int view_height = this.image_view_.getHeight();
            float view_aspect = 1.0f * view_width / view_height;

            int bmp_width = photo.getWidth();
            int bmp_height = photo.getHeight();
            float bmp_aspect = 1.0f * bmp_width / bmp_height;

            float scale;
            if (bmp_aspect >= view_aspect) {
                scale = 1.0f * bmp_width / view_width;
            }
            else {
                scale = 1.0f * bmp_height / view_height;
            }

            bmp_width = (int)(bmp_width / scale);
            bmp_height = (int)(bmp_height / scale);

            // 根据 image_view_ 大小, 设置目标
            photo = Bitmap.createScaledBitmap(photo, bmp_width, bmp_height, false);
            this.curr_img_ = photo.copy(Bitmap.Config.ARGB_8888, true);
            this.image_view_.set_bitmap(this.curr_img_);
        }
    }

    /**
     * 将 BMP 图像转换为 plane 格式的 RRRR...GGGG...BBBB... 并作标准化
     * @param bmp
     * @param meanR
     * @param meanG
     * @param meanB
     * @return
     */
    private float[] inputFromImage(Bitmap bmp, float meanR, float meanG, float meanB,
                                   float stdR, float stdG, float stdB)
    {
        int width = bmp.getWidth();
        int height = bmp.getHeight();
//        int stride = bmp.getRowBytes();
        float[] buf = new float[height * width * 3];
        int[] pixels = new int[height * width];
        bmp.getPixels(pixels, 0, width, 0, 0, width, height);   // 这里的 stride 难懂不应该是 bmp.getRowBytes()么?

        /// 保存为 RRRR...GGGG...BBBB... 的 plane 格式  shape: (1, 3, height, width)
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int pos = i * width + j;

                int pos_r = pos;
                int pos_g = width * height + pos;
                int pos_b = 2 * width * height + pos;

                int pixel = pixels[pos];

                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);

                buf[pos_r] = (r / 255.0f - meanR) / stdR;
                buf[pos_g] = (g / 255.0f - meanG) / stdG;
                buf[pos_b] = (b / 255.0f - meanB) / stdB;
            }
        }

        return buf;
    }

    private float[] prepare_input(Bitmap img0) {
        // 首先将 img resize 到 (WIDTH, HEIGHT) 大小
        Bitmap img = Bitmap.createScaledBitmap(img0, WIDTH, HEIGHT, false);
        return inputFromImage(img,
                0.485f, 0.456f, 0.406f,
                0.229f, 0.224f, 0.225f);
    }


    private ArrayList<String> load_voc_labels()
    {
        labels_.clear();
        try {
            InputStream f = getAssets().open("voc.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(f));

            String line;
            int n = 0;
            while ((line = reader.readLine()) != null) {
                labels_.add(line);
                n += 1;
            }

            reader.close();
            f.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        return labels_;
    }

    private ArrayList<ActionBox> do_detect(Bitmap img) {
        // FIXME: 应该放到独立的工作线程中执行吧? 否则会导致GUI线程阻塞
        float []buf = prepare_input(img);
        predictor_.forward("data", buf);

        float []catalogs = predictor_.getOutput(0);
        float []scores = predictor_.getOutput(1);
        float []boxes = predictor_.getOutput(2);

        ArrayList<ActionBox> acts = new ArrayList<>();

        for (int i = 0; i < catalogs.length; i ++) {
            // catalog
            if (catalogs[i] < 0) continue;

            int catalog = (int)(catalogs[i]);

            // score
            if (scores[i] < threshold_) continue;

            String title = "unk";
            if (catalog < this.labels_.size()) {
                title = this.labels_.get(catalog);
            }

            ActionBox act = new ActionBox(catalog, title,
                    scores[i],
                    boxes[4*i]/WIDTH, boxes[4*i+1]/HEIGHT,
                    boxes[4*i+2]/WIDTH, boxes[4*i+3]/HEIGHT);

            acts.add(act);
        }

        return acts;
    }


    private byte[] load_file_content(String fname)
    {
        byte []buf = null;
        try (InputStream is = getAssets().open(fname)) {
            int len = is.available();
            buf = new byte[len];
            is.read(buf);
            Log.d("MMM", "load_file_content: " + Integer.toString(len));
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        return buf;
    }

    private Predictor init_predictor() {
        this.labels_ = load_voc_labels();

        byte []sym_buf = this.load_file_content(sym_fname);
        byte []par_buf = this.load_file_content(par_fname);

        int []input_shape = new int[4];
        input_shape[0] = 1; // batch size
        input_shape[1] = 3; // channels
        input_shape[2] = HEIGHT;
        input_shape[3] = WIDTH;

        Predictor.InputNode []input_nodes = new Predictor.InputNode[1];
        input_nodes[0] = new Predictor.InputNode("data", input_shape);

        return new Predictor(sym_buf, par_buf, new Predictor.Device(Predictor.Device.Type.CPU, 0), input_nodes);
    }
}
