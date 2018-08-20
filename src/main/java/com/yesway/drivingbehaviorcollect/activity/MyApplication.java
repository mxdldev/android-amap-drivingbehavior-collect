package com.yesway.drivingbehaviorcollect.activity;

import android.app.Application;
import android.hardware.SensorManager;

public class MyApplication extends Application {
    public static boolean hasService = false;
    public static int speed = 2;
    public static int angle = 25;
    public static int collectRate = SensorManager.SENSOR_DELAY_NORMAL;
    public static int collectRateGyro = 1000;
    public static boolean gpsIsAvailable = false;

    public static int getCollectRateGyro() {
        return collectRateGyro;
    }

    public static void setCollectRateGyro(int collectRateGyro) {
        MyApplication.collectRateGyro = collectRateGyro;
    }

    public static boolean isHasService() {
        return hasService;
    }

    public static void setHasService(boolean hasService) {
        MyApplication.hasService = hasService;
    }

    public static int getSpeed() {
        return speed;
    }

    public static int getAngle() {
        return angle;
    }

    public static void setSpeed(int speed) {
        MyApplication.speed = speed;
    }

    public static void setAngle(int angle) {
        MyApplication.angle = angle;
    }

    public static int getCollectRate() {
        return collectRate;
    }

    public static void setCollectRate(int collectRate) {
        MyApplication.collectRate = collectRate;
    }

    public static boolean isGpsIsAvailable() {
        return gpsIsAvailable;
    }

    public static void setGpsIsAvailable(boolean gpsIsAvailable) {
        MyApplication.gpsIsAvailable = gpsIsAvailable;
    }
}
