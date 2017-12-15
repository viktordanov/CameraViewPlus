# CameraViewPlus
The aim of this library is to let you integrate camera features to your app, in case using `Intent` to launch default Camera cannot fulfill your requirements.

This is a fork from Google's [CameraView](https://github.com/google/cameraview).  
Based on the original one, the following has been done (As per Version 0.7.0):

- Resolved some bugs
- Implemented zoom feature
- Improved API friendliness
- Changed return image from `byte[]` to `Bitmap` (with correct orientation)
- Added listeners
- Allow force fallback to Camera1
- Write this documentation...

## When do I need this library?
Surprising, I found that there are not much CameraView library out there.  
I think it is because in most use-cases, we just need to launch the default camera by `Intent` and get the returned `Bitmap`.  
As far as I know, unless **you want to customize the layout of the "Camera Activity"** (e.g. overlaying a photoframe on the preview), it is rare that you need to implement your own CameraView.

## Why this library?
In fact, there is a pretty good library ([CameraView](https://github.com/natario1/CameraView)) out there. It has quite a number of features including video recording, together with well-written documentation.

However, I encountered 2 problems when trying to use it:

1. [Preview image is distorted in at least one pre-Lollipop device](https://github.com/natario1/CameraView/issues/116)
2. This library uses only Camera1 API.

You may wonder why 2 is a problem.  
In fact, if you use Camera1 on API level >= 21, the shutter sound becomes must play. This does not happen with Camera2 API.  
Another disadvantage is, Camera2 does provide better performance. For instance, more continuous zooming.

CameraViewPlus does not provide as much functionalities and flexibilities as this [CameraView](https://github.com/natario1/CameraView), but if what you want is a simple CameraView that is capable of showing a correct preview and taking a correct picture, then you are at the right place (I hope).

## Basic Functionalities

1. Using Camera2 API if the device's OS API level >= 21 && Camera Device is not [LEGACY](https://source.android.com/devices/camera/versioning#glossary).
2. Support different aspect ratios (But I strongly suggest you use either 4:3 or 16:9)
3. Able to switch between front and back camera
4. Auto-focus (Well of course)
5. Pinch zooming (Adjustable sensitivity)
6. Handle orientation for you (The `Bitmap` you get is already in correct orientation)
7. Flash light (**But I haven't changed any code about it, nor used it, nor tested it**)
8. Callback when focus is locked (i.e. You can play animation, if you like, when focus is locked)

## What this library does NOT offer but you are probably expecting

1. Handle runtime permission for you (You should not expect a `View` is responsible for this)
2. Tap to focus (Hard to implement... But in roadmap!)

## Usage

### Step 1: Add to your project

Add to application's build.gradle:

```
    implementation 'com.asksira.android:cameraviewplus:0.7.0'
```

### Step 2: Add CameraView to your layout

```xml
    <com.google.android.cameraview.CameraView
        android:id="@+id/camera_view"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:keepScreenOn="true"
        android:adjustViewBounds="true"
        app:autoFocus="true"
        app:cameraAspectRatio="4:3"
        app:facing="back"
        app:flash="off"/>
```
        
| Attribute Name   | Default    | Allowed Values                         |
|:-----------------|:-----------|:---------------------------------------|
| autoFocus        | true       | true / false                           |
| cameraAspectRatio| 4:3        | String (But I suggest only 4:3 or 16:9)|
| facing           | back       | back / front                           |
| flash            | auto       | auto / on / off / redEye / torch       |

`cameraAspectRatio` is width/height in LANDSCAPE mode. (Thus width is the LONGER side)  

**IMPORTANT: Please use your own ViewGroup to contain CameraView. Otherwise the preview might over-expand to out of what you may expect. I did not spend time on trying to fix this.**

### Step 3: Starting CameraView in your activity

This library does not help you to check runtime permission!  
Remember to check it by yourself before calling `cameraView.start()`.  

```java
    @Override
    protected void onResume() {
        super.onResume();
        if (isStoragePermissionGranted() && isCameraPermissionGranted()) {
            cameraView.start();
        } else {
            if (!isCameraPermissionGranted()) {
                checkCameraPermission();
            } else {
                checkStoragePermission();
            }
        }
    }

    @Override
    protected void onPause() {
        cameraView.stop();
        super.onPause();
    }
```

Warm reminder: `onReumse()` will be triggered after user has granted or denied a permission.

### Step 4: Setting up callbacks

**IMPORTANT: Add these callbacks after cameraView.start(). Otherwise no effect will take place if the device has API level >= 21 but using LEGACY camera.**

I have anchored 4 places where CameraView will callback your activity to let you do something. They are:

(1) When camera focus has locked (Play shutter animation or play some sounds, up to you)

```java
        cameraView.setOnFocusLockedListener(new CameraViewImpl.OnFocusLockedListener() {
            @Override
            public void onFocusLocked() {
                playShutterAnimation();
            }
        });
```

(2) When camera has successfully taken a picture (A `Bitmap` *with correct orientation* is returned to you)

```java
        cameraView.setOnPictureTakenListener(new CameraViewImpl.OnPictureTakenListener() {
            @Override
            public void onPictureTaken(Bitmap bitmap) {
                startSavingPhoto(bitmap);
            }
        });
```

(3) When camera catched an exception when trying to turn to front camera

```java
        cameraView.setOnTurnCameraFailListener(new CameraViewImpl.OnTurnCameraFailListener() {
            @Override
            public void onTurnCameraFail(Exception e) {
                Toast.makeText(CameraActivity.this, "Switch Camera Failed. Does you device has a front camera?",
                        Toast.LENGTH_SHORT).show();
            }
        });
```

(4) When camera encounters any other sorts of error (An exception is returned to you)

```java
        cameraView.setOnCameraErrorListener(new CameraViewImpl.OnCameraErrorListener() {
            @Override
            public void onCameraError(Exception e) {
                Toast.makeText(CameraActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
```

### Step 5: Taking picture

```java
cameraView.takePicture();
```

### Optional: Switching camera facing

```java
cameraView.switchCamera();
```

## Optional: Advanced usage

### Pinch zooming sensitivity

```java
cameraView.setPixelsPerOneZoomLevel(100) //Default value is 80
```

The number of pixels represents the distance (in pixels) between your fingers that need to change, in order for zoom level to increase or decrease by 1x.  
(With that said, Camera1 API does not expose such precise zoom ratio. So, if the device uses Camera1 API, the zoom level difference is discrete and hard to tell the exact ratio change.)

### Forcing to use Camera1

This feature is solely (at least solely for now) for solving [this issue](https://github.com/google/cameraview/issues/184).  
**Before the construction of CameraView**, (i.e. before `onCreate()` of an `Activity`):  

```java
CameraViewConfig.isForceCamera1 = true;
```

I made use of [this library](https://github.com/jaredrummler/AndroidDeviceNames) and do the following at app start (e.g. MainActivity):  

```java
            DeviceName.with(this).request(new DeviceName.Callback() {
                @Override
                public void onFinished(DeviceName.DeviceInfo info, Exception error) {
                    String deviceName = info.getName();
                    if (deviceName != null && deviceName.contains("Xperia") &&
                            (deviceName.contains("XZ")) || deviceName.contains("Compact")) {
                        CameraViewConfig.isForceCamera1 = true;
                    }
                }
            });
```

## Known Issues

As mentioned above, [the mysterious freezing when switching to front camera](https://github.com/google/cameraview/issues/184), so far reproduced only in some SONY Xperia models.

## Want to know more?

### How do you manage image's output orientation?

In fact, if your convert directly from `byte[]` to a `File`, the orientation of the image will be correct. But if you convert `byte[]` to `Bitmap` and then to `File`, you will find that the orientation is probably wrong (If you are taking an image in portrait mode).

The reaon is the combination of the below facts:  
(1) Most image viewer apps have the ability to examine EXIF attributes, thus showing correct orientation  
(2) The byte[] you get from camera contains EXIF attributes   
(3) Bitmap does not have EXIF attributes

So, thanks to this [CameraView](https://github.com/natario1/CameraView), I copied his method of solving this problem - reading EXIF attributes from byte[], and then rotate the Bitmap itself according to those EXIF attributes.

Therefore, you will find that the Bitmap produces by this library (taken in portrait mode) has a smaller width than height. This is because the image has already been rotated.

### Why a shutter sound is played in some devices?

As far as I know, this happens if your device has API Level >= 21, but still using Camera1 API.  
This happens if  
1. Your device has a [LEGACY](https://source.android.com/devices/camera/versioning#glossary) camera, or;
2. You have forced to use Camera1 on this device.  

And, again, as far as I know, you can do nothing about it.  

Please let me know if you know there is a way to turn it off!

## License

Since this is a fork of Google's CameraView, license follows the [original one](https://github.com/google/cameraview/blob/master/LICENSE).