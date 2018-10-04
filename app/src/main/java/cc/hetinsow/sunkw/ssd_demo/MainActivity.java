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
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import org.dmlc.mxnet.Predictor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final int MY_CAMERA_PERMISSION_CODE = 100;
    private static final int CAMERA_REQUEST = 1888;
    private ImageView image_view;
    private Predictor predictor_;
    private Bitmap curr_img_ = null;
    private float threshold_ = 0.2f;     // SSD mobilenet1.0 voc
    private String curr_photo_path = null;
    private ArrayList<String> labels = null;

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

        this.image_view = (ImageView)this.findViewById(R.id.image_view);


        Button button_capture = (Button)this.findViewById(R.id.button_capture);
        button_capture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, MY_CAMERA_PERMISSION_CODE);
                }
                else {
                    Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                    //ClipData clip;
                    //clip = ClipData.newUri(getContentResolver(), "A Photo", contentUri);
                    //cameraIntent.setClipData(clip);
                    //cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                    File imagePath = new File(Environment.getExternalStorageDirectory(), "images");
                    if (!imagePath.exists()) {
                        imagePath.mkdirs();
                    }
                    File newFile = new File(imagePath, "default_image.jpg");
                    final Uri contentUri = FileProvider.getUriForFile(getBaseContext(), getPackageName()+".fileprovider", newFile);
                    curr_photo_path = newFile.getAbsolutePath();

                    cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, contentUri);
                    startActivityForResult(cameraIntent, CAMERA_REQUEST);
                }
            }
        });

        final Activity act = this;
        Button button_detect = (Button)this.findViewById(R.id.button_Detect);
        button_detect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(act, "begin detecting ...", Toast.LENGTH_LONG).show();
                Bitmap img = curr_img_;
                ArrayList<ActionBox> acts = do_detect(img);
                Toast.makeText(act, "OK", Toast.LENGTH_LONG).show();

                // 将框框画到 img 上
                Bitmap show_img = img.copy(Bitmap.Config.ARGB_8888, true);
                Canvas canvas = new Canvas(show_img);

                Paint p = new Paint();
                p.setColor(Color.RED);
                p.setStrokeWidth(10.0f);
                p.setStyle(Paint.Style.STROKE);
                p.setTextSize(100.f);

                int width = img.getWidth();
                int height = img.getHeight();

                for (ActionBox act: acts) {
                    int x1 = (int)(act.x1 * width);
                    int y1 = (int)(act.y1 * height);
                    int x2 = (int)(act.x2 * width);
                    int y2 = (int)(act.y2 * height);

                    Rect r = new Rect();
                    r.set(x1, y1, x2, y2);

                    canvas.drawRect(r, p);
                    if (act.title != null) {
                        canvas.drawText(act.title, act.x1 * width, act.y1 * height, p);
                    }
                }

                image_view.setImageBitmap(show_img);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK) {
            Bitmap photo = BitmapFactory.decodeFile(curr_photo_path);
            this.image_view.setImageBitmap(photo);
            this.curr_img_ = photo;
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

    class ActionBox {
        public int catalog;
        public float score;
        public float x1, y1;
        public float x2, y2;
        public String title;

        public ActionBox(int catalog, String title, float score, float x1, float y1, float x2, float y2)
        {
            this.catalog = catalog;
            this.title = title;
            this.score = score;
            this.x1 = x1;
            this.x2 = x2;
            this.y1 = y1;
            this.y2 = y2;
        }
    };

    private ArrayList<String> load_voc_labels()
    {
        ArrayList<String> labels = new ArrayList<>();
        try {
            InputStream f = getAssets().open("voc.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(f));

            String line;
            while ((line = reader.readLine()) != null) {
                labels.add(line);
            }

            reader.close();
            f.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        return labels;
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
            if (catalog < this.labels.size()) {
                title = this.labels.get(catalog);
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
        this.labels = load_voc_labels();

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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_CAMERA_PERMISSION_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "camera permission granted", Toast.LENGTH_LONG).show();
                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(cameraIntent, CAMERA_REQUEST);
            }
            else {
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }
}
