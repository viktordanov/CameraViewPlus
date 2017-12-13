/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.cameraview;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.view.MotionEvent;
import android.view.View;

import java.util.Set;

public abstract class CameraViewImpl {

    /**
     * The distance between 2 fingers (in pixel) needed in order for zoom level to increase by 1x.
     */
    protected int pixelsPerOneZoomLevel = 80;

    protected OnPictureTakenListener pictureCallback;
    protected OnTurnCameraFailListener turnFailCallback;
    protected OnCameraErrorListener cameraErrorCallback;
    protected OnFocusLockedListener focusLockedCallback;
    protected CameraUtils.BitmapCallback bitmapDecodedCallback = new CameraUtils.BitmapCallback() {
        @Override
        public void onBitmapReady(Bitmap bitmap) {
            if (getFacing() == CameraView.FACING_FRONT) {
                if (pictureCallback != null) pictureCallback.onPictureTaken(mirrorBitmap(bitmap));
            } else {
                if (pictureCallback != null) pictureCallback.onPictureTaken(bitmap);
            }
        }
    };

    protected final PreviewImpl mPreview;

    CameraViewImpl(PreviewImpl preview) {
        mPreview = preview;
    }

    View getView() {
        return mPreview.getView();
    }

    private Bitmap mirrorBitmap (Bitmap bitmap) {
        Matrix matrix = new Matrix();
        matrix.preScale(-1.0f, 1.0f);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    public void setOnPictureTakenListener (OnPictureTakenListener pictureCallback) {
        this.pictureCallback = pictureCallback;
    }

    public void setOnFocusLockedListener (OnFocusLockedListener focusLockedListener) {
        this.focusLockedCallback = focusLockedListener;
    }

    public void setOnTurnCameraFailListener (OnTurnCameraFailListener turnCameraFailListener) {
        this.turnFailCallback = turnCameraFailListener;
    }

    public void setOnCameraErrorListener (OnCameraErrorListener onCameraErrorListener) {
        this.cameraErrorCallback = onCameraErrorListener;
    }

    /**
     * @return {@code true} if the implementation was able to start the camera session.
     */
    abstract boolean start();

    abstract void stop();

    abstract boolean isCameraOpened();

    abstract void setFacing(int facing);

    abstract int getFacing();

    abstract Set<AspectRatio> getSupportedAspectRatios();

    /**
     * @return {@code true} if the aspect ratio was changed.
     */
    abstract boolean setAspectRatio(AspectRatio ratio);

    abstract AspectRatio getAspectRatio();

    abstract void setAutoFocus(boolean autoFocus);

    abstract boolean getAutoFocus();

    abstract void setFlash(int flash);

    abstract int getFlash();

    abstract void takePicture();

    abstract void setDisplayOrientation(int displayOrientation);

    /**
     * @return {@code true} if the motionEvent is consumed.
     */
    abstract boolean zoom (MotionEvent event);

    abstract void onPinchFingerUp ();

    protected float getFingerSpacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    protected void byteArrayToBitmap (byte[] data) {
        CameraUtils.decodeBitmap(data, bitmapDecodedCallback);
    }

    public void setPixelsPerOneZoomLevel (int pixels) {
        if (pixels <= 0) return;
        pixelsPerOneZoomLevel = pixels;
    }

    public interface OnPictureTakenListener {
        void onPictureTaken (Bitmap bitmap);
    }

    public interface OnTurnCameraFailListener {
        void onTurnCameraFail (Exception e);
    }

    public interface OnCameraErrorListener {
        void onCameraError (Exception e);
    }

    public interface OnFocusLockedListener {
        void onFocusLocked ();
    }

}
