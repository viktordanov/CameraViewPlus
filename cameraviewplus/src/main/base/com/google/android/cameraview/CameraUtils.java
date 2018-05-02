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

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.Image;
import android.os.Handler;
import android.support.annotation.UiThread;
import android.support.media.ExifInterface;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Common utils for both Camera 1 and 2.
 */

public class CameraUtils {

    /**
     * Decodes an input byte array and outputs a Bitmap that is ready to be displayed.
     * The difference with {@link BitmapFactory#decodeByteArray(byte[], int, int)}
     * is that this cares about orientation, reading it from the EXIF header.
     * This is executed in a background thread, and returns the result to the original thread.
     *
     * @param source a JPEG byte array
     * @param callback a callback to be notified
     */
    @SuppressWarnings("WeakerAccess")
    public static void decodeBitmap(final byte[] source, final BitmapCallback callback) {
        decodeBitmap(source, Integer.MAX_VALUE, Integer.MAX_VALUE, callback);
    }

    /**
     * Decodes an input byte array and outputs a Bitmap that is ready to be displayed.
     * The difference with {@link BitmapFactory#decodeByteArray(byte[], int, int)}
     * is that this cares about orientation, reading it from the EXIF header.
     * This is executed in a background thread, and returns the result to the original thread.
     *
     * The image is also downscaled taking care of the maxWidth and maxHeight arguments.
     *
     * @param source a JPEG byte array
     * @param maxWidth the max allowed width
     * @param maxHeight the max allowed height
     * @param callback a callback to be notified
     */
    @SuppressWarnings("WeakerAccess")
    public static void decodeBitmap(final byte[] source, final int maxWidth, final int maxHeight, final BitmapCallback callback) {
        final Handler ui = new Handler();
        WorkerHandler.run(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = decodeBitmap(source, maxWidth, maxHeight);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onBitmapReady(bitmap);
                    }
                });
            }
        });
    }

    // TODO ignores flipping
    @SuppressWarnings({"SuspiciousNameCombination", "WeakerAccess"})
    /* for tests */ static Bitmap decodeBitmap(byte[] source, int maxWidth, int maxHeight) {
        if (maxWidth <= 0) maxWidth = Integer.MAX_VALUE;
        if (maxHeight <= 0) maxHeight = Integer.MAX_VALUE;
        int orientation;
        boolean flip;
        InputStream stream = null;
        try {
            // http://sylvana.net/jpegcrop/exif_orientation.html
            stream = new ByteArrayInputStream(source);
            ExifInterface exif = new ExifInterface(stream);
            Integer exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (exifOrientation) {
                case ExifInterface.ORIENTATION_NORMAL:
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    orientation = 0; break;

                case ExifInterface.ORIENTATION_ROTATE_180:
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    orientation = 180; break;

                case ExifInterface.ORIENTATION_ROTATE_90:
                case ExifInterface.ORIENTATION_TRANSPOSE:
                    orientation = 90; break;

                case ExifInterface.ORIENTATION_ROTATE_270:
                case ExifInterface.ORIENTATION_TRANSVERSE:
                    orientation = 270; break;

                default: orientation = 0;
            }

            flip = exifOrientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL ||
                    exifOrientation == ExifInterface.ORIENTATION_FLIP_VERTICAL ||
                    exifOrientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                    exifOrientation == ExifInterface.ORIENTATION_TRANSVERSE;

        } catch (IOException e) {
            e.printStackTrace();
            orientation = 0;
            flip = false;
        } finally {
            if (stream != null) {
                try { stream.close(); } catch (Exception ignored) {}
            }
        }

        Bitmap bitmap;
        if (maxWidth < Integer.MAX_VALUE || maxHeight < Integer.MAX_VALUE) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(source, 0, source.length, options);

            int outHeight = options.outHeight;
            int outWidth = options.outWidth;
            if (orientation % 180 != 0) {
                outHeight = options.outWidth;
                outWidth = options.outHeight;
            }

            options.inSampleSize = computeSampleSize(outWidth, outHeight, maxWidth, maxHeight);
            options.inJustDecodeBounds = false;
            bitmap = BitmapFactory.decodeByteArray(source, 0, source.length, options);
        } else {
            bitmap = BitmapFactory.decodeByteArray(source, 0, source.length);
        }

        if (orientation != 0 || flip) {
            Matrix matrix = new Matrix();
            matrix.setRotate(orientation);
            // matrix.postScale(1, -1) Flip... needs testing.
            Bitmap temp = bitmap;
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            temp.recycle();
        }
        return bitmap;
    }


    private static int computeSampleSize(int width, int height, int maxWidth, int maxHeight) {
        // https://developer.android.com/topic/performance/graphics/load-bitmap.html
        int inSampleSize = 1;
        if (height > maxHeight || width > maxWidth) {
            while ((height / inSampleSize) >= maxHeight
                    || (width / inSampleSize) >= maxWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    /**
     * Receives callbacks about a bitmap decoding operation.
     */
    public interface BitmapCallback {

        /**
         * Notifies that the bitmap was succesfully decoded.
         * This is run on the UI thread.
         *
         * @param bitmap decoded bitmap
         */
        @UiThread
        void onBitmapReady(Bitmap bitmap);
    }

    @SuppressLint("NewApi")
    public static byte[] YUV420toNV21(Image image) {
        Rect crop = image.getCropRect();
        int format = image.getFormat();
        int width = crop.width();
        int height = crop.height();
        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];

        int channelOffset = 0;
        int outputStride = 1;
        for (int i = 0; i < planes.length; i++) {
            switch (i) {
                case 0:
                    channelOffset = 0;
                    outputStride = 1;
                    break;
                case 1:
                    channelOffset = width * height + 1;
                    outputStride = 2;
                    break;
                case 2:
                    channelOffset = width * height;
                    outputStride = 2;
                    break;
            }

            ByteBuffer buffer = planes[i].getBuffer();
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();

            int shift = (i == 0) ? 0 : 1;
            int w = width >> shift;
            int h = height >> shift;
            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
            for (int row = 0; row < h; row++) {
                int length;
                if (pixelStride == 1 && outputStride == 1) {
                    length = w;
                    buffer.get(data, channelOffset, length);
                    channelOffset += length;
                } else {
                    length = (w - 1) * pixelStride + 1;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < w; col++) {
                        data[channelOffset] = rowData[col * pixelStride];
                        channelOffset += outputStride;
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }
        }
        return data;
    }

}
