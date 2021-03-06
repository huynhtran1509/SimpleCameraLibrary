package org.mariotaku.simplecamera.sample;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.mariotaku.simplecamera.CameraView;

import java.io.File;
import java.util.Collections;
import java.util.List;


public class MainActivity extends Activity implements CameraView.CameraListener, View.OnTouchListener, View.OnClickListener {

    private static final String LOGTAG = "SimpleCameraSample";
    private CameraView mCameraView;
    private FocusAreaView mFocusAreaView;
    private CameraView.VideoRecordTransaction mRecordVideoTransaction;

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        mCameraView = (CameraView) findViewById(R.id.camera_view);
        mFocusAreaView = (FocusAreaView) findViewById(R.id.focus_area);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.take_photo).setOnClickListener(this);
        findViewById(R.id.record_video).setOnClickListener(this);
        findViewById(R.id.front_camera).setOnClickListener(this);
        findViewById(R.id.back_camera).setOnClickListener(this);
        findViewById(R.id.layer_paint).setOnClickListener(this);
        mCameraView.setCameraListener(this);
        mCameraView.setOnTouchListener(this);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCameraInitialized(Camera camera) {

        findViewById(R.id.front_camera).setVisibility(Camera.getNumberOfCameras() > 1 ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onCameraOpeningError(Exception e) {
        Toast.makeText(this, "Failed to open camera", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void setParameterBeforeStartPreview(Camera camera, Camera.Parameters parameters) {
        final List<int[]> fpsRanges = parameters.getSupportedPreviewFpsRange();
        Collections.sort(fpsRanges, Utils.FPS_RANGE_COMPARATOR);
        if (!fpsRanges.isEmpty()) {
            final int[] fpsRange = fpsRanges.get(0);
            parameters.setPreviewFpsRange(fpsRange[0], fpsRange[1]);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mCameraView.isCameraAvailable()) {
            mCameraView.openCamera(0);
        }
    }

    @Override
    protected void onPause() {
        mCameraView.releaseCamera();
        super.onPause();
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_UP: {
                if (mRecordVideoTransaction != null) return true;
                if (mCameraView.touchFocus(event, new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean b, Camera camera) {
                        mFocusAreaView.endFocus();
                    }
                })) {
                    mFocusAreaView.startFocus(event.getX(), event.getY());
                }
                return true;
            }
        }
        return true;
    }

    @Override
    public void onClick(final View view) {
        switch (view.getId()) {
            case R.id.take_photo: {
                if (mRecordVideoTransaction != null || mCameraView.isAutoFocusing()) return;
                mCameraView.takePicture(null, new Camera.PictureCallback() {
                    @Override
                    public void onPictureTaken(byte[] data, Camera camera) {
                        final Context context = getApplicationContext();
                        final BitmapFactory.Options o = new BitmapFactory.Options();
                        o.inJustDecodeBounds = true;
                        BitmapFactory.decodeByteArray(data, 0, data.length, o);
                        final int requiredWidth = 320, requiredHeight = 320;
                        final float widthRatio = o.outWidth / (float) requiredWidth;
                        final float heightRatio = o.outHeight / (float) requiredHeight;
                        o.inJustDecodeBounds = false;
                        o.inSampleSize = (int) Math.max(1, Math.floor(Math.max(widthRatio, heightRatio)));
                        final Bitmap captured = BitmapFactory.decodeByteArray(data, 0, data.length, o);
                        if (captured == null) return;
                        final int capturedWidth = captured.getWidth(), capturedHeight = captured.getHeight();
                        final float requiredRatio = (float) requiredWidth / requiredHeight;
                        final float capturedRatio = (float) capturedWidth / capturedHeight;
                        final Matrix m = new Matrix();
                        final int x, y, width, height;
                        if (requiredRatio > capturedRatio) {
                            // fit width
                            /**
                             * +---------------------+
                             * |                     |
                             * |+-------------------+|
                             * ||                   ||
                             * ||      Required     ||
                             * ||                   ||
                             * |+-------------------+|
                             * |        Captured     |
                             * +---------------------+
                             * =OR=
                             *    +-------------+
                             *    |  Captured   |
                             * +--+-------------+--+
                             * |  |             |  |
                             * |  |   Required  |  |
                             * |  |             |  |
                             * +--+-------------+--+
                             *    |             |
                             *    +-------------+
                             */
                            width = capturedWidth;
                            height = Math.round(capturedWidth / requiredRatio);
                            x = 0;
                            y = (capturedHeight - height) / 2;
                            if (capturedWidth > requiredWidth) {
                                // Captured picture is larger than required
                                float scale = (float) requiredWidth / capturedWidth;
                                m.setScale(scale, scale);
                            }
                        } else {
                            width = Math.round(capturedHeight * requiredRatio);
                            height = capturedHeight;
                            x = (capturedWidth - width) / 2;
                            y = 0;
                            if (capturedHeight > requiredHeight) {
                                // Captured picture is larger than required
                                float scale = (float) requiredHeight / capturedHeight;
                                m.setScale(scale, scale);
                            }
                        }
                        m.postRotate(mCameraView.getPictureRotation());
                        final Bitmap transformed = Bitmap.createBitmap(captured, x, y, width, height, m, true);
                        final ImageView view = new ImageView(context);
                        view.setImageBitmap(transformed);
                        final Toast toast = new Toast(context);
                        toast.setView(view);
                        toast.show();
                    }
                });
                break;
            }
            case R.id.record_video: {
                if (mRecordVideoTransaction != null) {
                    mRecordVideoTransaction.stop();
                    mRecordVideoTransaction = null;
                    break;
                }
                final CameraView.VideoRecordConfig config = mCameraView.newVideoRecordConfig();
                if (config == null) return;
                final File extCacheDir = getExternalCacheDir();
                final String suffix;
                switch (config.getProfile().fileFormat) {
                    case MediaRecorder.OutputFormat.THREE_GPP: {
                        suffix = "3gp";
                        break;
                    }
                    default: {
                        suffix = "mp4";
                        break;
                    }
                }
                if (extCacheDir != null) {
                    config.setOutputPath(new File(extCacheDir, System.currentTimeMillis() + "." + suffix).getAbsolutePath());
                } else {
                    config.setOutputPath(new File(getCacheDir(), System.currentTimeMillis() + "." + suffix).getAbsolutePath());
                }
//                config.setMaxDuration(10000);
                mRecordVideoTransaction = mCameraView.recordVideo(config, new CameraView.VideoRecordCallback() {
                    @Override
                    public void onRecordStarted() {
                        Log.d(LOGTAG, "Record started");
                        ((TextView) view).setText("Stop recording");
                    }

                    @Override
                    public void onRecordError(Exception e) {
                        Log.e(LOGTAG, "Error recording video", e);
                        ((TextView) view).setText("Start recording");
                    }

                    @Override
                    public void onRecordStopped() {
                        Log.d(LOGTAG, "Record stopped");
                        ((TextView) view).setText("Start recording");
                    }

                    @Override
                    public void onInfo(MediaRecorder mr, int what, int extra) {
                        switch (what) {
                            case MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED: {
                                Log.d(LOGTAG, "Record stopped");
                                ((TextView) view).setText("Start recording");
                                break;
                            }
                        }
                    }
                });
                break;
            }
            case R.id.front_camera: {
                if (mRecordVideoTransaction != null) return;
                mCameraView.openCamera(1);
                break;
            }
            case R.id.back_camera: {
                if (mRecordVideoTransaction != null) return;
                mCameraView.openCamera(0);
                break;
            }
            case R.id.layer_paint: {
                startActivity(new Intent(this, LayerPaintActivity.class));
            }
        }
    }

    @Override
    public void onError(int error, Camera camera) {
        switch (error) {
            case Camera.CAMERA_ERROR_SERVER_DIED: {
                finish();
                break;
            }
        }
    }
}
