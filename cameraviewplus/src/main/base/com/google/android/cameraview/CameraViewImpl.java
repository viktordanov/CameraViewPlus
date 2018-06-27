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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.view.MotionEvent;
import android.view.View;

import java.util.Set;

public abstract class CameraViewImpl implements SensorEventListener {

    /**
     * The distance between 2 fingers (in pixel) needed in order for zoom level to increase by 1x.
     */
    protected int pixelsPerOneZoomLevel = 80;

    protected OnPictureTakenListener pictureCallback;
    protected OnTurnCameraFailListener turnFailCallback;
    protected OnCameraErrorListener cameraErrorCallback;
    protected OnFocusLockedListener focusLockedCallback;
    protected OnFrameListener onFrameCallback;

    protected final PreviewImpl mPreview;

    protected int maximumWidth = 0;
    protected int maximumPreviewWidth = 0;

    //Orientation Sensor
    protected SensorManager sensorManager;
    protected Sensor accelerometerSensor;
    protected Sensor magnetometerSensor;
    protected float[] accelerometerReading = new float[3];
    protected float[] magnetometerReading = new float[3];
    protected float[] rotationMatrix = new float[9];
    protected float[] orientationAngles = new float[3];
    protected OrientationCalculator orientationCalculator;


    CameraViewImpl(PreviewImpl preview, Context context) {
        mPreview = preview;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magnetometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }
        orientationCalculator = new OrientationCalculator();
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

    public void setOnFrameListener (OnFrameListener onFrameListener) {
        this.onFrameCallback = onFrameListener;
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
    abstract boolean setAspectRatio(AspectRatio ratio, boolean isInitializing);

    abstract AspectRatio getAspectRatio();

    abstract void setAutoFocus(boolean autoFocus);

    abstract boolean getAutoFocus();

    abstract void setFlash(int flash);

    abstract int getFlash();

    abstract void takePicture();

    abstract void setDisplayOrientation(int displayOrientation);

    /**
     * Different devices have different default orientations,
     * Therefore we need to take into account this value before passing the rotation
     * in the callback.
     */
    abstract int getCameraDefaultOrientation ();

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

    protected void byteArrayToBitmap (final byte[] data) {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inMutable = true;
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                if (getFacing() == CameraView.FACING_FRONT) {
                    if (pictureCallback != null) pictureCallback.onPictureTaken(mirrorBitmap(bitmap),
                            -(orientationCalculator.getOrientation() + getCameraDefaultOrientation()));
                } else {
                    if (pictureCallback != null) pictureCallback.onPictureTaken(bitmap,
                            -(orientationCalculator.getOrientation() + getCameraDefaultOrientation()));
                }
            }
        });
    }

    public void setPixelsPerOneZoomLevel (int pixels) {
        if (pixels <= 0) return;
        pixelsPerOneZoomLevel = pixels;
    }

    public int getMaximumWidth() {
        return maximumWidth;
    }

    public void setMaximumWidth(int mMaximumWidth) {
        this.maximumWidth = mMaximumWidth;
    }

    public int getMaximumPreviewWidth() {
        return maximumPreviewWidth;
    }

    public void setMaximumPreviewWidth(int maximumPreviewWidth) {
        this.maximumPreviewWidth = maximumPreviewWidth;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == accelerometerSensor) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.length);
            updateOrientation();
        } else if (event.sensor == magnetometerSensor) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.length);
            updateOrientation();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    protected void updateOrientation () {
        SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading);
        SensorManager.getOrientation(rotationMatrix, orientationAngles);
        orientationCalculator.update(orientationAngles[1], orientationAngles[2]);
    }

    public interface OnPictureTakenListener {
        void onPictureTaken (Bitmap bitmap, int rotationDegrees);
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

    public interface OnFrameListener {
        void onFrame (byte[] data, int width, int height, int rotationDegrees);
    }

}
