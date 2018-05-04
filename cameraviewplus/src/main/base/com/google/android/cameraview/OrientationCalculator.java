package com.google.android.cameraview;

/**
 * Created by abdelhady on 9/23/14.
 *
 * to use this class do the following 3 steps in your activity:
 *
 * define 3 sensors as member variables
 Sensor accelerometer;
 Sensor magnetometer;
 Sensor vectorSensor;
 DeviceOrientation deviceOrientation;
 *
 * add this to the activity's onCreate
 mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
 accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
 magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
 deviceOrientation = new DeviceOrientation();
 *
 * add this to onResume
 mSensorManager.registerListener(deviceOrientation.getEventListener(), accelerometer, SensorManager.SENSOR_DELAY_UI);
 mSensorManager.registerListener(deviceOrientation.getEventListener(), magnetometer, SensorManager.SENSOR_DELAY_UI);
 *
 * add this to onPause
 mSensorManager.unregisterListener(deviceOrientation.getEventListener());
 *
 *
 * then, you can simply call * deviceOrientation.getOrientation() * wherever you want
 *
 *
 * another alternative to this class's approach:
 * http://stackoverflow.com/questions/11175599/how-to-measure-the-tilt-of-the-phone-in-xy-plane-using-accelerometer-in-android/15149421#15149421
 *
 */
public class OrientationCalculator {
    private final int ORIENTATION_PORTRAIT = 0;
    private final int ORIENTATION_LANDSCAPE_REVERSE = 90;
    private final int ORIENTATION_LANDSCAPE = 270;
    private final int ORIENTATION_PORTRAIT_REVERSE = 180;

    private int smoothness = 1;
    private float averagePitch = 0;
    private float averageRoll = 0;
    private int orientation = ORIENTATION_PORTRAIT;

    private float[] pitches;
    private float[] rolls;

    public OrientationCalculator() {
        pitches = new float[smoothness];
        rolls = new float[smoothness];
    }

    public int getOrientation() {
        return orientation;
    }

    public void update (float pitch, float roll) {
        averagePitch = addValue(pitch, pitches);
        averageRoll = addValue(roll, rolls);
        orientation = calculateOrientation();
    }

    private float addValue(float value, float[] values) {
        value = (float) Math.round((Math.toDegrees(value)));
        float average = 0;
        for (int i = 1; i < smoothness; i++) {
            values[i - 1] = values[i];
            average += values[i];
        }
        values[smoothness - 1] = value;
        average = (average + value) / smoothness;
        return average;
    }

    private int calculateOrientation() {
        // finding local orientation dip
        if (((orientation == ORIENTATION_PORTRAIT || orientation == ORIENTATION_PORTRAIT_REVERSE)
                && (averageRoll > -30 && averageRoll < 30))) {
            if (averagePitch > 0)
                return ORIENTATION_PORTRAIT_REVERSE;
            else
                return ORIENTATION_PORTRAIT;
        } else {
            // divides between all orientations
            if (Math.abs(averagePitch) >= 30) {
                if (averagePitch > 0)
                    return ORIENTATION_PORTRAIT_REVERSE;
                else
                    return ORIENTATION_PORTRAIT;
            } else {
                if (averageRoll > 0) {
                    return ORIENTATION_LANDSCAPE_REVERSE;
                } else {
                    return ORIENTATION_LANDSCAPE;
                }
            }
        }
    }
}