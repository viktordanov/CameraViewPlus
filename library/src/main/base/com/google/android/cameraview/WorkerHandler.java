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

import android.os.Handler;
import android.os.HandlerThread;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

public class WorkerHandler {

    private final static ConcurrentHashMap<String, WeakReference<WorkerHandler>> sCache = new ConcurrentHashMap<>(4);

    public static WorkerHandler get(String name) {
        if (sCache.containsKey(name)) {
            WorkerHandler cached = sCache.get(name).get();
            if (cached != null) {
                HandlerThread thread = cached.mThread;
                if (thread.isAlive() && !thread.isInterrupted()) {
                    return cached;
                }
            }
            sCache.remove(name);
        }

        WorkerHandler handler = new WorkerHandler(name);
        sCache.put(name, new WeakReference<>(handler));
        return handler;
    }

    // Handy util to perform action in a fallback thread.
    // Not to be used for long-running operations since they will
    // block the fallback thread.
    public static void run(Runnable action) {
        get("FallbackCameraThread").post(action);
    }

    private HandlerThread mThread;
    private Handler mHandler;

    private WorkerHandler(String name) {
        mThread = new HandlerThread(name);
        mThread.setDaemon(true);
        mThread.start();
        mHandler = new Handler(mThread.getLooper());
    }

    public Handler get() {
        return mHandler;
    }

    public void post(Runnable runnable) {
        mHandler.post(runnable);
    }

    public Thread getThread() {
        return mThread;
    }

    public static void destroy() {
        for (String key : sCache.keySet()) {
            WeakReference<WorkerHandler> ref = sCache.get(key);
            WorkerHandler handler = ref.get();
            if (handler != null && handler.getThread().isAlive()) {
                handler.getThread().interrupt();
            }
            ref.clear();
        }
        sCache.clear();
    }

}
