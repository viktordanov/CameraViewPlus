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

package com.google.android.cameraview.demo;


import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v8.renderscript.RenderScript;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.cameraview.CameraView;
import com.google.android.cameraview.CameraViewImpl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.Callable;

import io.github.silvaren.easyrs.tools.Nv21Image;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class CameraActivity extends AppCompatActivity {

    private static final int PERMISSION_CODE_STORAGE = 3001;
    private static final int PERMISSION_CODE_CAMERA = 3002;

    CameraView cameraView;

    View shutterEffect;
    View captureButton;
    View turnButton;

    private RenderScript rs;

    private boolean frameIsProcessing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        cameraView = findViewById(R.id.camera_view);
        shutterEffect = findViewById(R.id.shutter_effect);
        captureButton = findViewById(R.id.shutter);
        turnButton = findViewById(R.id.turn);

        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraView.takePicture();
            }
        });

        turnButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraView.switchCamera();
            }
        });

        rs = RenderScript.create(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (PermissionUtils.isStorageGranted(this) && PermissionUtils.isCameraGranted(this)) {
            cameraView.start();
            setupCameraCallbacks();
        } else {
            if (!PermissionUtils.isCameraGranted(this)) {
                PermissionUtils.checkPermission(this, Manifest.permission.CAMERA,
                        PERMISSION_CODE_CAMERA);
            } else {
                PermissionUtils.checkPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        PERMISSION_CODE_STORAGE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_CODE_STORAGE:
            case PERMISSION_CODE_CAMERA:
                if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Toast.makeText(this, "Permission not granted", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
        }
        if (requestCode != PERMISSION_CODE_STORAGE && requestCode != PERMISSION_CODE_CAMERA) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onPause() {
        cameraView.stop();
        super.onPause();
    }

    private void setupCameraCallbacks () {
        cameraView.setOnPictureTakenListener(new CameraViewImpl.OnPictureTakenListener() {
            @Override
            public void onPictureTaken(Bitmap bitmap) {
                startSavingPhoto(bitmap);
            }
        });
        cameraView.setOnFocusLockedListener(new CameraViewImpl.OnFocusLockedListener() {
            @Override
            public void onFocusLocked() {
                playShutterAnimation();
            }
        });
        cameraView.setOnTurnCameraFailListener(new CameraViewImpl.OnTurnCameraFailListener() {
            @Override
            public void onTurnCameraFail(Exception e) {
                Toast.makeText(CameraActivity.this, "Switch Camera Failed. Does you device has a front camera?",
                        Toast.LENGTH_SHORT).show();
            }
        });
        cameraView.setOnCameraErrorListener(new CameraViewImpl.OnCameraErrorListener() {
            @Override
            public void onCameraError(Exception e) {
                Toast.makeText(CameraActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        cameraView.setOnFrameListener(new CameraViewImpl.OnFrameListener() {
            @Override
            public void onFrame(final byte[] data, final int width, final int height) {
                if (frameIsProcessing) return;
                frameIsProcessing = true;
                Observable.fromCallable(new Callable<Bitmap>() {
                    @Override
                    public Bitmap call() throws Exception {
                        return Nv21Image.nv21ToBitmap(rs, data, width, height);
                    }
                }).subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Observer<Bitmap>() {
                            @Override
                            public void onSubscribe(Disposable d) {

                            }

                            @Override
                            public void onNext(Bitmap frameBitmap) {
                                if (frameBitmap != null) {
                                    Log.i("onFrame", frameBitmap.getWidth() + ", " + frameBitmap.getHeight());
                                }
                            }

                            @Override
                            public void onError(Throwable e) {

                            }

                            @Override
                            public void onComplete() {
                                frameIsProcessing = false;
                            }
                        });
            }
        });
    }

    private void playShutterAnimation () {
        shutterEffect.setVisibility(View.VISIBLE);
        shutterEffect.animate().alpha(0f).setDuration(300).setListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        shutterEffect.setVisibility(View.GONE);
                        shutterEffect.setAlpha(0.8f);
                    }
                });
    }

    private String bitmapToFile(Bitmap bitmap) {
        //create a file to write bitmap data
        Date currentTime = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String fileName = getString(R.string.app_name) + sdf.format(currentTime) + ".jpg";
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), fileName);
        try {
            file.createNewFile();
        } catch (Exception e) {
            if (BuildConfig.DEBUG) e.printStackTrace();
            return "";
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, bos);
        byte[] bitmapData = bos.toByteArray();

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            fos.write(bitmapData);
            fos.flush();
            fos.close();
        } catch (Exception e) {
            if (BuildConfig.DEBUG) e.printStackTrace();
            return "";
        } finally {
            try {
                fos.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return file.getAbsolutePath();
    }

    private void startSavingPhoto(final Bitmap bitmap) {
        Observable.fromCallable(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return bitmapToFile(bitmap);
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(String filePath) throws Exception {
                        if (filePath.isEmpty()) {
                            Toast.makeText(CameraActivity.this, "Save image file failed :(", Toast.LENGTH_SHORT).show();
                        } else {
                            notifyGallery(filePath);
                        }
                    }
                });
    }

    private void notifyGallery(String filePath) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File file = new File(filePath);
        Uri contentUri = Uri.fromFile(file);
        mediaScanIntent.setData(contentUri);
        sendBroadcast(mediaScanIntent);
    }

}
