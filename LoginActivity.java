package com.chunyun.android;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.Face;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import com.chunyun.android.service.HttpHelper;
import com.chunyun.android.service.HttpService;
import com.chunyun.android.service.model.FaceMatchResponse;
import com.chunyun.android.service.model.GetHostBoxListResponse;
import com.chunyun.android.util.EasyPermissions;
import com.chunyun.android.util.ImageUtil;
import com.umeng.socialize.utils.UmengText;
import com.xialf.www.ui.LAYOUT;
import com.xialf.www.ui.LAYOUT_TYPE;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import c.org.android_inface.face_callback;
import c.org.android_inface.face_model;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class LoginActivity extends BasicActivity {

    Activity main;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        main=this;
        goMain();
    }

    void goMain() {
        initCamera();
    }

    void initCamera() {
        LAYOUT layout = new LAYOUT(this, LAYOUT_TYPE.top_bottom, false, null, new float[]{100, 0});

        mPreviewView = new TextureView(this);
        mPreviewView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                setupCamera();
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
        layout.findItem("bottom").setContent(mPreviewView);
        layout.render();

    }

    TextureView mPreviewView;
    Size mPreviewSize;
    String mCameraId;
    CameraDevice mCameraDevice;
    CaptureRequest.Builder mPreviewBuilder;
    CaptureRequest mCaptureRequest;
    ImageReader mImageReader;
    CameraCaptureSession mPreviewSession;
    Handler mhandler=new Handler();
    Handler mhandler2=new Handler();
    boolean is_match=false;
    int have_face=0;

    void setupCamera() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            //获取摄像头的管理者CameraManager
            CameraManager manager = null;

            manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            try {
                //遍历所有摄像头
                for (String id : manager.getCameraIdList()) {
                    CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
                    //默认打开后置摄像头
//                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT)
//                    continue;

                    //默认打开前置
                    if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK)
                        continue;




                    //获取StreamConfigurationMap，它是管理摄像头支持的所有输出格式和尺寸
                    StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                    // 对于静态图像捕获，我们使用最大的可用尺寸。
                    mPreviewSize = Collections.max(
                            Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                            new Comparator<Size>() {
                                @Override
                                public int compare(Size lhs, Size rhs) {
                                    return Long.signum(lhs.getWidth() * lhs.getHeight()
                                            - rhs.getHeight() * rhs.getWidth());
                                }
                            });
                    mCameraId = id;
                    break;
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        } else {
            //低版本

        }
    }

    void openCamera() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {

            //获取摄像头的管理者CameraManager
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            //检查权限
            try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                //打开相机，第一个参数指示打开哪个摄像头，第二个参数stateCallback为相机的状态回调接口，第三个参数用来确定Callback在哪个线程执行，为null的话就在当前线程执行
                manager.openCamera(mCameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        mCameraDevice = camera;

                        //开启预览
                        startPreview();
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {

                    }

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {

                    }
                }, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    void startPreview() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            SurfaceTexture mSurfaceTexture = mPreviewView.getSurfaceTexture();
            //设置TextureView的缓冲区大小
            mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            //获取Surface显示预览数据
            Surface mSurface = new Surface(mSurfaceTexture);

            setupImageReader();
            Surface mSurface2=mImageReader.getSurface();

            try {
                //创建CaptureRequestBuilder，TEMPLATE_PREVIEW比表示预览请求
                mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

                //设置Surface作为预览数据的显示界面
                mPreviewBuilder.addTarget(mSurface);
                mPreviewBuilder.addTarget(mSurface2);



//                mPreviewBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,
//                        CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE);

               // mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);


                //获取屏幕方向
                int rotation = getWindowManager().getDefaultDisplay().getRotation();
//                 //设置CaptureRequest输出到mImageReader
//
//                 //设置拍照方向
                mPreviewBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATION.get(rotation));
//                 //聚焦
//                 mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE,
//                         CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
//
//
//
                //创建相机捕获会话，第一个参数是捕获数据的输出Surface列表，第二个参数是CameraCaptureSession的状态回调接口，当它创建好后会回调onConfigured方法，第三个参数用来确定Callback在哪个线程执行，为null的话就在当前线程执行
                mCameraDevice.createCaptureSession(Arrays.asList(mSurface,mSurface2),
                        new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession session) {
                                try {
                                    //创建捕获请求
                                    mCaptureRequest = mPreviewBuilder.build();
                                    mPreviewSession = session;

                                    //设置反复捕获数据的请求，这样预览界面就会一直有数据显示
                                    mPreviewSession.setRepeatingRequest(mCaptureRequest, new CameraCaptureSession.CaptureCallback() {
                                        @Override
                                        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                           onCameraImagePreviewed(result);
                                        }
                                    }, mhandler);

                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                            }
                        }, mhandler);

            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        } else {
            //
        }
    }

    /**
     * 处理相机画面处理完成事件，获取检测到的人脸坐标，换算并绘制方框
     *
     * @param result
     */
    private void onCameraImagePreviewed(CaptureResult result)
    {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {

//            Face faces[] = new Face[0];
//            faces = result.get(CaptureResult.STATISTICS_FACES);
//
//            if (faces.length > 0) {
//                have_face = 10;
//            }

            //showMessage(false, "人脸个数:[" + faces.length + "]");
//
//        Canvas canvas = rView.lockCanvas();
//        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);//旧画面清理覆盖
//
//        if (faces.length > 0) {
//            for (int i = 0; i < faces.length; i++) {
//                Rect fRect = faces[i].getBounds();
//                //Log("[R" + i + "]:[left:" + fRect.left + ",top:" + fRect.top + ",right:" + fRect.right + ",bottom:" + fRect.bottom + "]");
//                //showMessage(true, "[R" + i + "]:[left:" + fRect.left + ",top:" + fRect.top + ",right:" + fRect.right + ",bottom:" + fRect.bottom + "]");
//
//                //人脸检测坐标基于相机成像画面尺寸以及坐标原点。此处进行比例换算
//                //成像画面与方框绘制画布长宽比比例（同画面角度情况下的长宽比例（此处前后摄像头成像画面相对预览画面倒置（±90°），计算比例时长宽互换））
//                float scaleWidth = canvas.getHeight() * 1.0f / cPixelSize.getWidth();
//                float scaleHeight = canvas.getWidth() * 1.0f / cPixelSize.getHeight();
//
//                //坐标缩放
//                int l = (int) (fRect.left * scaleWidth);
//                int t = (int) (fRect.top * scaleHeight);
//                int r = (int) (fRect.right * scaleWidth);
//                int b = (int) (fRect.bottom * scaleHeight);
//                //Log("[T" + i + "]:[left:" + l + ",top:" + t + ",right:" + r + ",bottom:" + b + "]");
//               // showMessage(true, "[T" + i + "]:[left:" + l + ",top:" + t + ",right:" + r + ",bottom:" + b + "]");
//
//                //人脸检测坐标基于相机成像画面尺寸以及坐标原点。此处进行坐标转换以及原点(0,0)换算
//                //人脸检测：坐标原点为相机成像画面的左上角，left、top、bottom、right以成像画面左上下右为基准
//                //画面旋转后：原点位置不一样，根据相机成像画面的旋转角度需要换算到画布的左上角，left、top、bottom、right基准也与原先不一样，
//                //如相对预览画面相机成像画面角度为90°那么成像画面坐标的top，在预览画面就为left。如果再翻转，那成像画面的top就为预览画面的right，且坐标起点为右，需要换算到左边
//                if (isFront) {
//                    //此处前置摄像头成像画面相对于预览画面顺时针90°+翻转。left、top、bottom、right变为bottom、right、top、left，并且由于坐标原点由左上角变为右下角，X,Y方向都要进行坐标换算
//                    canvas.drawRect(canvas.getWidth() - b, canvas.getHeight() - r, canvas.getWidth() - t, canvas.getHeight() - l, getPaint());
//                } else {
//                    //此处后置摄像头成像画面相对于预览画面顺时针270°，left、top、bottom、right变为bottom、left、top、right，并且由于坐标原点由左上角变为左下角，Y方向需要进行坐标换算
//                    canvas.drawRect(canvas.getWidth() - b, l, canvas.getWidth() - t, r, getPaint());
//                }
//            }
        }
    }

    void restartPreview() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            try {
                //执行setRepeatingRequest方法就行了，注意mCaptureRequest是之前开启预览设置的请求
                mPreviewSession.setRepeatingRequest(mCaptureRequest, null, mhandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        } else {

        }
    }

    void setupImageReader() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            //前三个参数分别是需要的尺寸和格式，最后一个参数代表每次最多获取几帧数据，本例的2代表ImageReader中最多可以获取两帧图像流
//
//            mImageReader = ImageReader.newInstance(mPreviewSize.getWidth() / 10, mPreviewSize.getHeight() / 10,
//                    ImageFormat.YUV_420_888, 1);


            int width=mPreviewSize.getWidth();
            int height=mPreviewSize.getHeight();
            int scale=10;
            mImageReader = ImageReader.newInstance(width/scale,height/scale,
                    ImageFormat.YUV_420_888, 1);



            //监听ImageReader的事件，当有图像流数据可用时会回调onImageAvailable方法，它的参数就是预览帧数据，可以对这帧数据进行处理
            mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(final ImageReader reader) {
                    final Image image = reader.acquireNextImage();
                    if (
                            //have_face == 0 ||
                            is_match == true) {
                        image.close();
                    } else {
                        //have_face -= 1;
                        is_match = true;

                        long mi1 = System.currentTimeMillis();
                        Bitmap map= c.org.android_inface.face.yuv_to_bitmap(image);
                        long mi12 = System.currentTimeMillis();
                        final long mi13 = mi12 - mi1;
                        image.close();

                        c.org.android_inface.face.init("remote","http://111.231.249.197:8885");
                        c.org.android_inface.face.match(map
                                , new face_callback() {
                            @Override
                            public void onSuccess(face_model.success success) {
                                is_match=false;
                                showToast("识别到["+"]");
                            }

                            @Override
                            public void onFailed(face_model.fail fail) {
                                is_match=false;
                            }
                        });
                    }
                }
            }, mhandler2);
        }
    }

    private static final SparseIntArray ORIENTATION = new SparseIntArray();
    static {
        //后置
//        ORIENTATION.append(Surface.ROTATION_0, 90);
//        ORIENTATION.append(Surface.ROTATION_90, 0);
//        ORIENTATION.append(Surface.ROTATION_180, 270);
//        ORIENTATION.append(Surface.ROTATION_270, 180);

        //前置
        ORIENTATION.append(Surface.ROTATION_0, 270);
        ORIENTATION.append(Surface.ROTATION_90, 0);
        ORIENTATION.append(Surface.ROTATION_180, 90);
        ORIENTATION.append(Surface.ROTATION_270, 180);
    }
}
