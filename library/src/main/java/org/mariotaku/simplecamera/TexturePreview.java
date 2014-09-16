package org.mariotaku.simplecamera;

import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import java.io.IOException;

/**
 * Created by mariotaku on 14-9-9.
 */
public class TexturePreview implements Preview, TextureView.SurfaceTextureListener {

    private final CameraView mCameraView;
    private final TextureView mTextureView;

    public TexturePreview(CameraView cameraView) {
        mCameraView = cameraView;
        mTextureView = new TextureView(cameraView.getContext());
        mTextureView.setSurfaceTextureListener(this);
    }

    @Override
    public TextureView getView() {
        return mTextureView;
    }

    @Override
    public void layoutPreview(boolean changed, int l, int t, int r, int b) {
        mTextureView.layout(0, 0, mTextureView.getMeasuredWidth(), mTextureView.getMeasuredHeight());
        final Camera camera = mCameraView.getOpeningCamera();
        updateSurface(camera, mTextureView.getMeasuredWidth(), mTextureView.getMeasuredHeight());
    }

    @Override
    public boolean isAttachedToCameraView() {
        return mTextureView.getParent() == mCameraView;
    }

    @Override
    public void onPreReleaseCamera(Camera camera) {
        camera.stopPreview();
        try {
            camera.setPreviewTexture(null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        final Camera camera = mCameraView.openCameraIfNeeded();
        if (camera == null) return;
        try {
            final Camera.Parameters parameters = camera.getParameters();
            camera.setParameters(parameters);
            camera.setPreviewTexture(surface);
            updateSurface(camera, width, height);
            camera.startPreview();
            mCameraView.requestLayout();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateSurface(final Camera camera, final int width, final int height) {
        if (camera == null) return;
        final Camera.Size size = camera.getParameters().getPreviewSize();
        final int rotation = CameraUtils.getDisplayRotation(mCameraView.getContext());
        final boolean isPortrait;
        if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
            isPortrait = true;
        } else {
            isPortrait = false;
        }
        final int cameraWidth = isPortrait ? size.height : size.width;
        final int cameraHeight = isPortrait ? size.width : size.height;
        final float viewRatio = (float) width / height, cameraRatio = (float) cameraWidth / cameraHeight;
        final Matrix transform = new Matrix();
        if (viewRatio > cameraRatio) {
            // fit width
            transform.setScale(1, width / cameraRatio / height);
        } else {
            // fit height
            transform.setScale(height * cameraRatio / width, 1);
        }
        final float translateX, translateY;
        if (viewRatio > cameraRatio) {
            translateX = 0;
            translateY = -(width / cameraRatio - height) / 2;
        } else {
            translateX = -(height * cameraRatio - width) / 2;
            translateY = 0;
        }
        transform.postTranslate(translateX, translateY);
        mTextureView.setTransform(transform);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        mCameraView.requestLayout();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        mCameraView.releaseCamera();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

}
