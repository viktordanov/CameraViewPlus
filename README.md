# CameraViewPlus
The aim of this library is to let you integrate camera features to your app, in case using `Intent` to launch default Camera cannot fulfill your requirements, such as overlaying images to your Camera preview.

This is a fork from Google's [CameraView](https://github.com/google/cameraview).  
Based on the original one, the following has been done (As per Version 0.9.4):

- Resolved some bugs
- Implemented zoom feature (Configurable since v0.9.5)
- Improved API friendliness
- Changed return image from `byte[]` to `Bitmap`, with rotation degrees
- Added camera event listeners
- Allow force fallback to Camera1
- Write this documentation...
- **NEW! 0.9.0** Callback to get each frame of camera preview in high FPS
- **NEW! 0.9.4** Configurable Preview and Capture image max resoultion

## When do I need this library?
Surprising, I found that there are not much CameraView library out there.  
I think it is because in most use-cases, we just need to launch the default camera by `Intent` and get the returned URI.  
As far as I know, unless **you want to customize the layout of the "Camera Activity"** (e.g. overlaying a photoframe on the preview), it is rare that you need to implement your own CameraView.

## Why this library?
For those libraries I found out there, such as [CameraView](https://github.com/natario1/CameraView) and [Fotoapparat](https://github.com/Fotoapparat/Fotoapparat), although they are really good libraries, up to date and with a lot of revisions, I still encountered various minor problems.

For example, distorted aspect ratios in some devices, low FPS in returned preview frame, wrong orientation of returned preview frame, not using new Camera 2 API (Which produces a sound when taking pictures, less smooth zooming), insufficient documentations, etc...

Although this library does not have as much functionalities and flexibilities the above 2 libraries provided, but if what you want is simply a CameraView that is able to show a correct preview, take a correct picture in a correct orientation, then you are at the right place (I hope).

*Recently I used it to create a real-time face detection app, overlaying images onto those faces and take a picture with those overlays, with correct orientations no matter how you hold the device. This might be more difficult than you imagined!*

## Basic Functionalities

1. Using Camera2 API if the device's OS API level >= 21 && Camera Device is not [LEGACY](https://source.android.com/devices/camera/versioning#glossary).
2. Support different aspect ratios (But I strongly suggest you use either 4:3 or 16:9)
3. Able to switch between front and back camera
4. Auto-focus (Well of course)
5. Pinch zooming (Adjustable sensitivity)
6. Handle orientation for you **using device's sensor (0.9.0)**
7. Flash light (**But I haven't changed any code about it, nor used it, nor tested it**)
8. Callback when focus is locked (i.e. You can play animation, if you like, when focus is locked)
9. **NEW! 0.9.0** Callback of each preview frame, passed to you in `byte[]` with width, height and rotation degrees
10. **NEW! 0.9.4** Configurable Preview and Capture image max resoultion

## What this library does NOT offer but you are probably expecting

1. Handle runtime permission for you (You should not expect a `View` is responsible for this)
2. Tap to focus (Hard to implement... May be in the future!)

## Prerequisite

This library only supports, and tested, in a portrait activity.  
User can hold the device in any orientations he/she wants, the output image has correct orientation, but your activity configuration should never be rotated to landscape.

So, in your AndroidManifest.xml:

```xml
    <application
        ...>

        ...

        <activity android:name=".CameraActivity"
            android:screenOrientation="portrait"/>

    </application>
```

## Usage

### Step 1: Add to your project

Add to application's build.gradle:

```
    implementation 'com.asksira.android:cameraviewplus:0.9.5'
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
        app:flash="off"
        app:maximumWidth="4000"
        app:maximumPreviewWidth="1280"
        app:useHighResPicture="false"
        app:enableZoom="true"/>
```
        
| Attribute Name       | Default    | Allowed Values                         |
|:---------------------|:-----------|:---------------------------------------|
| autoFocus            | true       | true / false                           |
| cameraAspectRatio    | 4:3        | String (But I suggest only 4:3 or 16:9)|
| facing               | back       | back / front                           |
| flash                | auto       | auto / on / off / redEye / torch       |
| maximumWidth         | 0          | integers                               |
| maximumPreviewWidth  | 0          | integers                               |
| useHighResPicture    | true       | See below                              |
| enableZoom           | true       | true/ false                            |

`cameraAspectRatio` is width/height in LANDSCAPE mode. (Thus width is the LONGER side)  

`maximumWidth` and `maximumPreviewWidth` will limit the longer length of the resolution. For example, if you set `aspectRatio="16:9"` and `maximumWidth="2000"`, `3200*1800` will not be used, but `1920*1080` will be used.

If `useHighResPicture` is set to true && your device supports it, it will override `maximumWidth` and will use only super high resolution.

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

Warm reminder: `onReumse()` will be triggered again after user has granted or denied a permission.

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

(2) When camera has successfully taken a picture

```java
        cameraView.setOnPictureTakenListener(new CameraViewImpl.OnPictureTakenListener() {
            @Override
            public void onPictureTaken(Bitmap bitmap, int rotationDegrees) {
                startSavingPhoto(bitmap, int rotationDegrees);
            }
        });
```

Before saving the `Bitmap` into a `File`, rotate it to a correct orientation first:

```java
                Matrix matrix = new Matrix();
                matrix.postRotate(-rotationDegrees);
                return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
```

Warm Reminder: Do this in a background thread!

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
*(With that said, Camera1 API does not expose such precise zoom ratio. So, if the device uses Camera1 API, the zoom level difference is discrete and hard to tell the exact ratio change.)*

### Getting each camera preview frame

This is useful if you need to process each frame, e.g. Perform some face detections.

```java
cameraView.setOnFrameListener(new CameraViewImpl.OnFrameListener() {
            @Override
            public void onFrame(final byte[] data, final int width, final int height, int rotationDegrees) {
                //Do things in another background thread!
                Bitmap bitmap = Nv21Image.nv21ToBitmap(rs, data, width, height);
                //Do whatever you want with this Bitmap
            }
        });
```

`maximumPreviewWidth` will affect the width and height returned in `onFrame()`.

As you can see, I am using `Nv21Image.nv21ToBitmap()` to convert the `byte[]` into a `Bitmap`.  
This is because the `byte[]` of frames are in NV21 formatting instead of JPEG. This is a must for a high FPS; and the only format of Camera1 API.

The native `BitmapFactory` cannot decode NV21 byte array.  
I made use of [this library (EasyRS)](https://github.com/silvaren/easyrs) to do the conversion from NV21 to `Bitmap`(ARGB).

### Forcing to use Camera1

For any reason, if you want to fallback to Camera1 even for devices that supports Camera2 API, **Before the construction of CameraView**, (i.e. before `onCreate()` of the `Activity` that contains `CameraView`):  

```java
CameraViewConfig.isForceCamera1 = true;
```

## Release Notes

(Release Notes are not avaiable before v0.9.4.)

v0.9.5
1. Added optional callback to get raw `bytes[]` instead of `Bitmap`. (#9)
2. Tried to resolve #4 orientation not detected if device does not have a magnetometer.
3. Merged #7 So that user can now disable zoom feature.

v0.9.4
1. Fixed aspect ratio not working in Camera2, which is an original bug [here](https://github.com/google/cameraview/pull/177).
2. Implemented maximum preview width
3. Implemented maximum image width

## Want to know more?

### How does this library manage image's orientation?

In the original [Google's CameraView](https://github.com/google/cameraview), it uses OS's orientation (Whether the activity is portrait or landscape) to determine's the output image's orientation.

But this approach really sucks. All camera apps I know are defaulted to portrait mode (so that the activity will not be re-created when your rotate your device) to provide smoother UX.

At first (v0.7.0) I used EXIF attributes to read orientation from `byte[]`. But later I found that this does not always works.

So in v0.9.0 I abandoned the old method and implemented `SensorEventListener` to observe real time device orientation change. By reading the orientation value from the sensor, together with reading the camera's default orientation, **a rotation degrees value** is passed to you in both `OnPictureTaken()` and `onFrame()`. You can then rotate the bitmap by yourself using this value.

### Why a shutter sound is played in some devices?

As far as I know, this happens if your device has API Level >= 21, but still using Camera1 API.  
This happens if  
1. Your device has a [LEGACY](https://source.android.com/devices/camera/versioning#glossary) camera, or;
2. You have forced to use Camera1 on this device.  

As far as I know, you can do nothing about it.  

Please let me know if you know there is a way to turn it off!

## License

Since this is a fork of Google's CameraView, license follows the [original one](https://github.com/google/cameraview/blob/master/LICENSE).