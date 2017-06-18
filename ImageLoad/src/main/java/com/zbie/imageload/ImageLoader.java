package com.zbie.imageload;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.util.LruCache;
import android.util.Log;
import android.widget.ImageView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by 涛 on 2017/6/18 0018.
 * 项目名           ImageLoadZzzz
 * 包名             com.zbie.imageloadzzzz.zzzz
 * 创建时间         2017/06/18 16:22
 * 创建者           zbie
 * 邮箱             hyxt2011@163.com
 * Github:         https://github.com/zbiext
 * 简书:           http://www.jianshu.com/
 * QQ&WX：         1677208059
 * 描述            三级缓存图片加载(在listview加载过程中，存在卡顿和FC, 因为图片大小过大，导致)
 */
public class ImageLoader {

    private static final String TAG = "ImageLoader";

    private static ImageLoader mInstance;

    private Context mContext;

    //全局的内存缓存图片
    //private static Map<String, Bitmap> mCaches = new HashMap<>();
    //private static Map<String ,SoftReference<Bitmap>> map;
    //private WeakReference<Bitmap> mWeakReference;
    //private PhantomReference<Bitmap> mPhantomReference;
    private static LruCache<String, Bitmap> mBmpCaches;

    private static Handler mHandler;

    private static ExecutorService mPool;

    private ImageLoader(Context context) {
        if (context instanceof Activity || context instanceof Service) {
            mContext = context.getApplicationContext();
        } else {
            mContext = context;
        }

        //mPhantomReference = new;
        //Bitmap bitmap = mPhantomReference.get();
        //mPhantomReference.get();

        mPool = Executors.newFixedThreadPool(3);
        int maxSize = (int) (Runtime.getRuntime().freeMemory() / 4 + 0.5f); // 向系统申请的内存空间
        Log.d(TAG, "ImageLoader: maxSize = " + maxSize);
        mBmpCaches = new LruCache<String, Bitmap>(maxSize) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                // 每当调用put(key,Bitmap)
                // 记录放进来的图片的大小
                return value.getByteCount();
            }
        };
        mHandler = new Handler(Looper.getMainLooper());
    }

    public static ImageLoader with(Context context) {
        // 单例
        if (mInstance == null) {
            synchronized (ImageLoader.class) {
                if (mInstance == null) {
                    return new ImageLoader(context);
                }
            }
        }
        return mInstance;
    }

    public ImageRequest load(String url) {
        return new ImageRequest(url);
    }

    public class ImageRequest {

        private final String    mUrl;
        private       ImageView mIv;

        public ImageRequest(String url) {
            mUrl = url;
        }

        public void into(ImageView iv) {
            mIv = iv;
            loadImage();
        }

        public void loadImage() {
            // 1.去内存中获取图片
            Bitmap bitmap = mBmpCaches.get(mUrl);
            // 判断 内存中是否有bitmap
            if (bitmap != null) {
                mIv.setImageBitmap(bitmap);
                return;
            }

            // 没有, 2.到disk上去读取
            bitmap = getBitmapFromDisk();
            // 判断 disk中是否有bitmap
            if (bitmap != null) {
                // disk上有,存到内存
                mBmpCaches.put(mUrl, bitmap);
                mIv.setImageBitmap(bitmap);
                return;
            }

            // 没有, 3.去网络请求图片
            doBitmapFromNetWork();
        }

        private Bitmap getBitmapFromDisk() {
            File bmpFile = getBitmapFileFromDisk();
            return !bmpFile.exists() ? null : BitmapFactory.decodeFile(bmpFile.getAbsolutePath());
        }

        @NonNull
        public File getBitmapFileFromDisk() {
            File dir;
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) { //外部SD卡存在
                dir = new File(mContext.getExternalCacheDir(), "Zzzz");
            } else {
                dir = new File(mContext.getCacheDir(), "Zzzz");
            }
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String name = MD5enCode(mUrl) + ".png";
            return new File(dir, name);
        }

        public String MD5enCode(String password) {
            try {
                MessageDigest digester = MessageDigest.getInstance("MD5");
                byte[]        digest   = digester.digest(password.getBytes());
                StringBuilder sb       = new StringBuilder();
                for (byte b : digest) {
                    int    c = b & 0xff;
                    String s = Integer.toHexString(c);
                    if (s.length() == 1) {
                        s = 0 + s;
                    }
                    sb.append(s);
                }
                return sb.toString();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return "null";
        }

        private Bitmap doBitmapFromNetWork() {
            //线程池：
            mPool.submit(new DownloadImageTask(mUrl));
            return null;
        }

        /**
         * bitmap --> file
         *
         * @param bitmap
         */
        private void saveBitmap(Bitmap bitmap) {
            File file = getBitmapFileFromDisk();
            try {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, new FileOutputStream(file));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        private class DownloadImageTask implements Runnable {
            private String mUrl;

            public DownloadImageTask(String url) {
                mUrl = url;
            }

            @Override
            public void run() {
                try {
                    // OkHttp 网络请求
                    OkHttpClient client = new OkHttpClient();
                    Request request = new Request
                            .Builder()
                            .get()
                            .url(mUrl)
                            .build();
                    Response response = client.newCall(request).execute();
                    InputStream is = response.body().byteStream();
                    Bitmap bitmap = BitmapFactory.decodeStream(is);

                    // 网络上有,存到内存
                    mBmpCaches.put(mUrl, bitmap);
                    // 网络上有,存到disk
                    saveBitmap(bitmap);

                    // 到主线程中显示图片
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            loadImage();
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
