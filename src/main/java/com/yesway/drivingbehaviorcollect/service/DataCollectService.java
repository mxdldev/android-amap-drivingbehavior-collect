package com.yesway.drivingbehaviorcollect.service;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.yesway.drivingbehaviorcollect.activity.MyApplication;
import com.yesway.drivingbehaviorcollect.activity.R;
import com.yesway.drivingbehaviorcollect.entity.AcceleratedSpeed;
import com.yesway.drivingbehaviorcollect.entity.AngleSpeed;
import com.yesway.drivingbehaviorcollect.filter.MeanFilter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class DataCollectService extends Service implements SensorEventListener, LocationListener {
    private static final String TAG = "DataCollectService";
    private LocationManager locationManager;
    private SensorManager sensorManager;
    private String collectFileNameGyro = "gyrodata.csv";
    private String collectFileNameAccel = "acceldata.csv";
    private String headerTableGyro = "x axis,y axis,z axis,speed,x anagle,y anagle,z anagle,angle,date,time,calculateResult";
    private String headerTableAccel = "x axis,y axis,z axis,speed,timestamp,currtime,result";
    private PrintWriter printWriterGyro;
    private PrintWriter printWriterAccel;
    private List<AngleSpeed> listAngleSpeed = new ArrayList<AngleSpeed>();
    private List<AcceleratedSpeed> listAcceleratedSpeed = new ArrayList<AcceleratedSpeed>();
    // 偏心率
    public static final float EPSILON = 0.000000001f;
    // 10亿纳秒=1秒
    private static final float NS2S = 1.0f / 1000000000.0f;
    // 均值滤波窗口
    private static final int MEAN_FILTER_WINDOW = 10;
    // 最小样本计数
    private static final int MIN_SAMPLE_COUNT = 30;

    // 是否初始化了方位
    private boolean hasInitialOrientation = false;
    // 是否初始化了校准
    private boolean stateInitializedCalibrated = false;
    private int accelerationSampleCount = 0;
    private int magneticSampleCount = 0;
    private long timestampOldCalibrated = 0;
    private float lastXAxisAngle = -1;
    private float lastXAxisAngleByRotationVector = -1;

    private long currTimeGyro;
    private long lastTimeGyro;
    private long currTimeAccel;
    private long lastTimeAccel;

    private DecimalFormat df;
    private float[] rotationVectorMatrix;
    // Calibrated maths.
    private float[] currentRotationMatrixCalibrated;
    private float[] deltaRotationMatrixCalibrated;
    private float[] deltaRotationVectorCalibrated;
    private float[] gyroscopeOrientationCalibrated;

    private float[] initialRotationMatrix;
    private float[] acceleration;
    private float[] magnetic;

    // 均值过滤器
    private MeanFilter accelerationFilter;
    private MeanFilter magneticFilter;

    private float[] gravity;
    private static final float ALPHA = 0.8f;

    private float azimuthGPS = -1;
    // private DataCollectBinder dataCollectBinder;

    private float xAngle;
    private float yAngle;
    private float zAngle;

    private static int turnLeft;
    private static int turnRight;
    private static int addSpeed;
    private static int cutSpeed;
    private Intent intentReceiver;
    private Messenger messengerActivity;
    private Messenger messagerService;


    private SoundPool soundPool;
    private HashMap<Integer, Integer> soundMap = new HashMap<Integer, Integer>();
    private Toast toast;
    private Handler handlerService = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    messengerActivity = msg.replyTo;
                    break;
                case 1:
                    ++turnLeft;
                    soundPool.play(soundMap.get(1), 0.1f, 0.1f, 0, 0, 1);
                    //TTSController.getInstance(DataCollectService.this).playTextFromLinkedList("亲，左转弯了");
                    sendMsg();
                    toast = Toast.makeText(DataCollectService.this, "左转弯了", Toast.LENGTH_SHORT);
                    toast.show();
                    break;
                case 2:
                    ++turnRight;
                    soundPool.play(soundMap.get(2), 0.1f, 0.1f, 0, 0, 1);
                   // TTSController.getInstance(DataCollectService.this).playTextFromLinkedList("亲，右转弯了");
                    sendMsg();
                    toast = Toast.makeText(DataCollectService.this, "右转弯了", Toast.LENGTH_SHORT);
                    toast.show();
                    break;
                case 3:
                    ++addSpeed;
                    soundPool.play(soundMap.get(3), 0.1f, 0.1f, 0, 0, 1);
                    //TTSController.getInstance(DataCollectService.this).playTextFromLinkedList("亲，急加速了");
                    sendMsg();
                    toast = Toast.makeText(DataCollectService.this, "急加速了", Toast.LENGTH_SHORT);
                    toast.show();
                    break;
                case 4:
                    ++cutSpeed;
                    soundPool.play(soundMap.get(4), 0.1f, 0.1f, 0, 0, 1);
                    //TTSController.getInstance(DataCollectService.this).playTextFromLinkedList("亲，急减速了");
                    sendMsg();
                    toast = Toast.makeText(DataCollectService.this, "急减速了", Toast.LENGTH_SHORT);
                    toast.show();
                    break;
                default:
                    break;
            }
        }
    };

    public void sendMsg() {
        if (messengerActivity == null)
            return;
        Bundle bundle;
        Message msg1;
        bundle = new Bundle();
        bundle.putInt("turnLeft", turnLeft);
        bundle.putInt("turnRight", turnRight);
        bundle.putInt("addSpeed", addSpeed);
        bundle.putInt("cutSpeed", cutSpeed);
        msg1 = new Message();
        msg1.what = 1;
        msg1.setData(bundle);
        try {
            messengerActivity.send(msg1);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private Timer timer;
    private TimerTask timerTask = new TimerTask() {
        @Override
        public void run() {
            if (messengerActivity == null)
                return;
            Bundle bundle;
            Message msg1;
            bundle = new Bundle();
            bundle.putFloat("x", gyroscopeOrientationCalibrated[0]);
            bundle.putFloat("y", gyroscopeOrientationCalibrated[1]);
            bundle.putFloat("z", gyroscopeOrientationCalibrated[2]);
            bundle.putFloat("g", azimuthGPS);

            msg1 = new Message();
            msg1.what = 0;
            try {
                msg1.setData(bundle);
                messengerActivity.send(msg1);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };

    public Messenger getMessagerService() {
        return messagerService;
    }

    public void setMessagerService(Messenger messagerService) {
        this.messagerService = messagerService;
    }

    private Messenger messagerActivity;

    @Override
    public IBinder onBind(Intent intent) {
        // return new MyBinder();
        return messagerService.getBinder();
    }

    public class DataCollectBinder extends Binder {
        public DataCollectService getService() {
            return DataCollectService.this;
        }
    }

	/*class MyBinder extends ICollect.Stub {

		@Override
		public float[] getData() throws RemoteException {
			float[] data = new float[3];
			data[0] = 15;
			return data;
		}

	}*/

    @Override
    public void onCreate() {
        super.onCreate();
        ((MyApplication) getApplication()).setHasService(true);
        messagerService = new Messenger(handlerService);
        timer = new Timer();
        createDataFile();
        initSound();
        initMaths();
        initFilters();
        startCollect();
    }

    private void initSound() {
        turnLeft = 0;
        turnRight = 0;
        addSpeed = 0;
        cutSpeed = 0;

        soundPool = new SoundPool(1, AudioManager.STREAM_SYSTEM, 0);
        soundMap.put(1, soundPool.load(this, R.raw.turnover_left, 1));
        soundMap.put(2, soundPool.load(this, R.raw.turnover_right, 1));
        soundMap.put(3, soundPool.load(this, R.raw.accelebration_jia, 1));
        soundMap.put(4, soundPool.load(this, R.raw.accelebration_jian, 1));

    }

    @Override
    public void onLocationChanged(Location location) {
        if (location == null)
            return;
        azimuthGPS = location.getBearing();
        Log.v(TAG, "azimuthCar:" + azimuthGPS + "azimuthPhone:" + lastXAxisAngleByRotationVector);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCollect();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        if (LocationProvider.AVAILABLE == status) {
            MyApplication.setGpsIsAvailable(true);
        } else {
            MyApplication.setGpsIsAvailable(false);
        }
    }


    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationVectorMatrix, event.values);
            determineOrientation(rotationVectorMatrix);
        }
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            onLinearAccelerationSensorChanged(event.values, event.timestamp);
        }
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            onAccelerationSensorChanged(event.values, event.timestamp);
        }

        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            onMagneticSensorChanged(event.values, event.timestamp);
        }

        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            onGyroscopeSensorChanged(event.values, event.timestamp);
        }
    }

    private void determineOrientation(float[] rotationMatrix) {
        if (messengerActivity == null)
            return;
        float[] orientationValues = new float[3];
        SensorManager.getOrientation(rotationMatrix, orientationValues);
        lastXAxisAngleByRotationVector = (float) (Math.toDegrees(orientationValues[0]) + 360) % 360;
        //(float)Math.toDegrees(orientationValues[0]);
        Message msg = new Message();
        msg.what = 2;
        Bundle bundle = new Bundle();
        bundle.putFloatArray("angleArray", orientationValues);
        msg.setData(bundle);
        try {
            messengerActivity.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void initMaths() {
        rotationVectorMatrix = new float[16];
        acceleration = new float[3];
        magnetic = new float[3];
        gravity = new float[3];
        initialRotationMatrix = new float[9];

        deltaRotationVectorCalibrated = new float[4];
        deltaRotationMatrixCalibrated = new float[9];
        currentRotationMatrixCalibrated = new float[9];
        gyroscopeOrientationCalibrated = new float[3];

        currentRotationMatrixCalibrated[0] = 1.0f;
        currentRotationMatrixCalibrated[4] = 1.0f;
        currentRotationMatrixCalibrated[8] = 1.0f;
    }

    private void initFilters() {
        accelerationFilter = new MeanFilter();
        accelerationFilter.setWindowSize(MEAN_FILTER_WINDOW);

        magneticFilter = new MeanFilter();
        magneticFilter.setWindowSize(MEAN_FILTER_WINDOW);
    }

    @SuppressLint("NewApi")
    public void onGyroscopeSensorChanged(float[] gyroscope, long timestamp) {
        if (!hasInitialOrientation) {
            return;
        }
        if (!stateInitializedCalibrated) {
            currentRotationMatrixCalibrated = matrixMultiplication(currentRotationMatrixCalibrated, initialRotationMatrix);
            stateInitializedCalibrated = true;
            lastTimeGyro = SystemClock.uptimeMillis();
        }
        if (timestampOldCalibrated != 0 && stateInitializedCalibrated) {

            final float dT = (timestamp - timestampOldCalibrated) * NS2S;

            float axisX = gyroscope[0];
            float axisY = gyroscope[1];
            float axisZ = gyroscope[2];

            float omegaMagnitude = (float) Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);

            if (omegaMagnitude > EPSILON) {
                axisX /= omegaMagnitude;
                axisY /= omegaMagnitude;
                axisZ /= omegaMagnitude;
            }

            float thetaOverTwo = omegaMagnitude * dT / 2.0f;

            float sinThetaOverTwo = (float) Math.sin(thetaOverTwo);
            float cosThetaOverTwo = (float) Math.cos(thetaOverTwo);

            deltaRotationVectorCalibrated[0] = sinThetaOverTwo * axisX;
            deltaRotationVectorCalibrated[1] = sinThetaOverTwo * axisY;
            deltaRotationVectorCalibrated[2] = sinThetaOverTwo * axisZ;
            deltaRotationVectorCalibrated[3] = cosThetaOverTwo;

            SensorManager.getRotationMatrixFromVector(deltaRotationMatrixCalibrated, deltaRotationVectorCalibrated);

            currentRotationMatrixCalibrated = matrixMultiplication(currentRotationMatrixCalibrated, deltaRotationMatrixCalibrated);

            SensorManager.getOrientation(currentRotationMatrixCalibrated, gyroscopeOrientationCalibrated);

            currTimeGyro = SystemClock.uptimeMillis();
            if (currTimeGyro - lastTimeGyro > MyApplication.getCollectRateGyro()) {
                writeData(gyroscope, gyroscopeOrientationCalibrated, timestamp);
                lastTimeGyro = currTimeGyro;
            }
        }
        timestampOldCalibrated = timestamp;
    }

    private void writeData(float[] speeds, float[] radians, long timestamp) {
        double speed = Math.sqrt(Math.pow(speeds[0], 2) + Math.pow(speeds[1], 2) + Math.pow(speeds[2], 2));
        double radian = Math.sqrt(Math.pow(radians[0], 2) + Math.pow(radians[1], 2) + Math.pow(radians[2], 2));
        AngleSpeed angleSpeed = new AngleSpeed();
        angleSpeed.setxAsixSpeed(speeds[0]);
        angleSpeed.setyAsixSpeed(speeds[1]);
        angleSpeed.setzAsixSpeed(speeds[2]);
        angleSpeed.setAngleSpeed(speed);
        angleSpeed.setxRadian(radians[0]);
        angleSpeed.setyRadian(radians[1]);
        angleSpeed.setzRadian(radians[2]);
        angleSpeed.setAverageRadian(radian);
        angleSpeed.setTimestamp(timestamp);
        angleSpeed.setCurrTime(new Date());
        float currXAxisAngle = (float) (Math.toDegrees(radians[0]) + 360) % 360;
        // 若0<α<90 &&270<β<360&&360-β+α>45,则左转弯了
        // 若270<α<360 &&0<β<90&&360-α+β>45,则右转弯了
        // 若β-α>45，则右转弯了
        // 若β-α<-45，则左转弯了
        String calculateResult = "";
        if (lastXAxisAngle != -1) {
            if (0 < lastXAxisAngle && lastXAxisAngle < 90 && 270 < currXAxisAngle && currXAxisAngle < 360) {
                if (360 - currXAxisAngle + lastXAxisAngle > MyApplication.getAngle()) {
                    calculateResult = "A left turn";
                    handlerService.sendEmptyMessage(1);
                    Log.v(TAG + ">>>>>>>>>>", calculateResult);
                }
            } else if (270 < lastXAxisAngle && lastXAxisAngle < 360 && 0 < currXAxisAngle && currXAxisAngle < 90) {
                if (360 + currXAxisAngle - lastXAxisAngle > MyApplication.getAngle()) {
                    calculateResult = "A right turn";
                    handlerService.sendEmptyMessage(2);
                    Log.v(TAG + ">>>>>>>>>>", calculateResult);
                }
            } else {
                if (currXAxisAngle - lastXAxisAngle > MyApplication.getAngle()) {
                    calculateResult = "A right turn";
                    handlerService.sendEmptyMessage(2);
                    Log.v(TAG + "***********", calculateResult);
                } else if (currXAxisAngle - lastXAxisAngle < -MyApplication.getAngle()) {
                    calculateResult = "A left turn";
                    handlerService.sendEmptyMessage(1);
                    Log.v(TAG + "***********", calculateResult);
                }
            }
        }
        if (!TextUtils.isEmpty(calculateResult)) {
            Log.v(TAG, "!!!!!!!!!!!!!!!!!!!" + calculateResult);
        }
        // Log.v(TAG,
        // "currXAxisAngle="+currXAxisAngle+",lastXAxisAngle="+lastXAxisAngle);
        angleSpeed.setCalculateResult(calculateResult);
        lastXAxisAngle = currXAxisAngle;
        listAngleSpeed.add(angleSpeed);

		/*
         * if (printWriter != null) printWriter.println(angleSpeed.toString());
		 */
    }

    public void onAccelerationSensorChanged(float[] acceleration, long timeStamp) {
        if (!hasInitialOrientation) {
            System.arraycopy(acceleration, 0, this.acceleration, 0, acceleration.length);
            this.acceleration = accelerationFilter.filterFloat(this.acceleration);
        }
        accelerationSampleCount++;
        if (accelerationSampleCount > MIN_SAMPLE_COUNT && magneticSampleCount > MIN_SAMPLE_COUNT && !hasInitialOrientation) {
            calculateOrientation();
        }
    }

    public void onLinearAccelerationSensorChanged(float[] acceleration, long timeStamp) {

        if (lastTimeAccel == 0)
            lastTimeAccel = SystemClock.uptimeMillis();
        currTimeAccel = SystemClock.uptimeMillis();

        float[] values = acceleration.clone();
        values = highPass(values[0], values[1], values[2]);
        // if(currTimeAccel - lastTimeAccel > 200){
        lastTimeAccel = currTimeAccel;
        AcceleratedSpeed cceleratedSpeed = new AcceleratedSpeed();
        double averageSpeed = Math.sqrt(Math.pow(values[0], 2) + Math.pow(values[1], 2) + Math.pow(values[2], 2));
        cceleratedSpeed.setxAsixSpeed(values[0]);
        cceleratedSpeed.setyAsixSpeed(values[1]);
        cceleratedSpeed.setzAsixSpeed(values[2]);
        cceleratedSpeed.setTimestamp(timeStamp);
        cceleratedSpeed.setCurrTime(new Date());
        cceleratedSpeed.setAverageSpeed(averageSpeed);
        String calculateResult = "";
        float speedMax = 0;
        if (Math.abs(values[1]) > Math.abs(values[0])) {
            speedMax = Math.abs(values[1]);
        } else {
            speedMax = Math.abs(values[0]);
        }
        if (speedMax > MyApplication.getSpeed()) {
            float x = Math.abs(values[0]);
            float y = Math.abs(values[1]);
            float z = Math.abs(values[2]);

            // if(z > high)
            // high = z;
            // 若0<=α<90，则0 <= β < α + 90 || α+270 < β <=360
            // 若90<=α<360，则α-90 < β < α + 90

            // 若0<=α<180，则α+180 < β < 360 || 0<= β <α
            // 若α>=180，则α-180 < β < α
            float currAzimuthCar;
            if (MyApplication.isGpsIsAvailable() && azimuthGPS != -1) {
                currAzimuthCar = azimuthGPS;
            } else {
                currAzimuthCar = lastXAxisAngleByRotationVector;
            }
            float currAzimuthPhone = lastXAxisAngleByRotationVector;
            boolean sameDirection = false;
            if (speedMax == y) {
				/*if (currAzimuthCar < 90) {
					if ((currAzimuthPhone >= 0 && currAzimuthPhone < currAzimuthCar + 90) || currAzimuthPhone > currAzimuthCar + 270 && currAzimuthPhone <= 360)
						sameDirection = true;
				} else {
					if (currAzimuthPhone > currAzimuthCar - 90 && currAzimuthPhone < currAzimuthCar + 90)
						sameDirection = true;
				}
				if (sameDirection && values[1] > 0 || !sameDirection && values[1] < 0) {
					handlerService.sendEmptyMessage(3);
					calculateResult = "急加速了";
				} else {
					calculateResult = "急减速了";
					handlerService.sendEmptyMessage(4);
				}*/
                sameDirection = calDirect(currAzimuthPhone,currAzimuthCar);
                msgHint(sameDirection, values[1],cceleratedSpeed);
            } else if (speedMax == x) {
              /*  if (currAzimuthCar >= 0 && currAzimuthCar < 180) {
                    if ((currAzimuthPhone > currAzimuthCar + 180 && currAzimuthPhone < 360) || currAzimuthPhone >= 0 && currAzimuthPhone < currAzimuthCar) {
                        sameDirection = true;
                    }
                } else {
                    if (currAzimuthPhone > currAzimuthCar - 180 && currAzimuthPhone < currAzimuthCar) {
                        sameDirection = true;
                    }
                }
                if (currAzimuthPhone == currAzimuthCar)
                    if (sameDirection && values[0] > 0 || !sameDirection && values[0] < 0) {
                        calculateResult = "急加速了";
                        handlerService.sendEmptyMessage(3);
                    } else {
                        calculateResult = "急减速了";
                        handlerService.sendEmptyMessage(4);
                    }*/
                currAzimuthPhone = currAzimuthPhone - 90;
                sameDirection = calDirect(currAzimuthPhone,currAzimuthCar);
                msgHint(sameDirection, values[1],cceleratedSpeed);
            }
        }
       // cceleratedSpeed.setCalculateResult(calculateResult);
       // listAcceleratedSpeed.add(cceleratedSpeed);
        // }
    }

    public void msgHint(boolean b, float value,AcceleratedSpeed cceleratedSpeed) {
        String calculateResult = "";
        if (b) {
            if (value > 0) {
                handlerService.sendEmptyMessage(3);
                calculateResult = "急加速了";
            } else {
                calculateResult = "急减速了";
                handlerService.sendEmptyMessage(4);
            }

        } else {
            if (value > 0) {
                calculateResult = "急减速了";
                handlerService.sendEmptyMessage(4);
            } else {
                handlerService.sendEmptyMessage(3);
                calculateResult = "急加速了";
            }
        }
        cceleratedSpeed.setCalculateResult(calculateResult);
        listAcceleratedSpeed.add(cceleratedSpeed);
    }
    public boolean calDirect(float currAzimuthPhone,float currAzimuthCar){
        boolean sameDirection  = false;
        if (currAzimuthPhone >= 0 && currAzimuthPhone <= 90) {
            if ((currAzimuthCar >= 0 && currAzimuthCar <= currAzimuthPhone + 90) || (currAzimuthCar >= 270 + currAzimuthPhone && currAzimuthCar <= 360)) {
                sameDirection = true;
            } else {
                sameDirection = false;
            }

        } else if (currAzimuthPhone > 90 && currAzimuthPhone <= 270) {
            if (currAzimuthCar >= currAzimuthPhone - 90 && currAzimuthCar <= currAzimuthPhone + 90) {
                sameDirection = true;
            }else{
                sameDirection = false;
            }
        }else if(currAzimuthPhone > 270 && currAzimuthPhone <= 360){
            if ((currAzimuthCar >= 0 && currAzimuthCar <= currAzimuthPhone - 270) || (currAzimuthCar >= currAzimuthPhone-90 && currAzimuthCar <= 360)) {
                sameDirection = true;
            } else {
                sameDirection = false;
            }
        }
        return sameDirection;
    }

    public void onMagneticSensorChanged(float[] magnetic, long timeStamp) {
        System.arraycopy(magnetic, 0, this.magnetic, 0, magnetic.length);
        this.magnetic = magneticFilter.filterFloat(this.magnetic);
        magneticSampleCount++;
    }

    private void calculateOrientation() {
        hasInitialOrientation = SensorManager.getRotationMatrix(initialRotationMatrix, null, acceleration, magnetic);
        if (hasInitialOrientation) {
            sensorManager.unregisterListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
            sensorManager.unregisterListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD));
        }
    }

    private float[] matrixMultiplication(float[] a, float[] b) {
        float[] result = new float[9];

        result[0] = a[0] * b[0] + a[1] * b[3] + a[2] * b[6];
        result[1] = a[0] * b[1] + a[1] * b[4] + a[2] * b[7];
        result[2] = a[0] * b[2] + a[1] * b[5] + a[2] * b[8];

        result[3] = a[3] * b[0] + a[4] * b[3] + a[5] * b[6];
        result[4] = a[3] * b[1] + a[4] * b[4] + a[5] * b[7];
        result[5] = a[3] * b[2] + a[4] * b[5] + a[5] * b[8];

        result[6] = a[6] * b[0] + a[7] * b[3] + a[8] * b[6];
        result[7] = a[6] * b[1] + a[7] * b[4] + a[8] * b[7];
        result[8] = a[6] * b[2] + a[7] * b[5] + a[8] * b[8];

        return result;
    }

    private void showGyroscopeNotAvailableAlert() {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setTitle("陀螺仪");
        alertDialogBuilder.setMessage("你的设备不支持陀螺仪...").setCancelable(false).setNegativeButton("I'll look around...", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    private float[] highPass(float x, float y, float z) {
        float[] filteredValues = new float[3];

        gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * x;
        gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * y;
        gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * z;

        filteredValues[0] = x - gravity[0];
        filteredValues[1] = y - gravity[1];
        filteredValues[2] = z - gravity[2];

        return filteredValues;
    }

    @SuppressLint("InlinedApi")
    private void startCollect() {
        listAcceleratedSpeed.clear();
        listAngleSpeed.clear();

        hasInitialOrientation = false;
        // 是否初始化了校准
        stateInitializedCalibrated = false;
        accelerationSampleCount = 0;
        magneticSampleCount = 0;
        timestampOldCalibrated = 0;
        lastXAxisAngle = -1;

        currTimeGyro = 0;
        lastTimeGyro = 0;
        currTimeAccel = 0;
        lastTimeAccel = 0;

        turnLeft = 0;
        turnRight = 0;
        addSpeed = 0;
        cutSpeed = 0;

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 200, 0, this);

        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), MyApplication.getCollectRate());
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION), MyApplication.getCollectRate());
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), MyApplication.getCollectRate());
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), MyApplication.getCollectRate());
        boolean enabled = sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), MyApplication.getCollectRate());
        if (!enabled) {
            showGyroscopeNotAvailableAlert();
        }
        timer.schedule(timerTask, 0, 200);
    }

    private void createDataFile() {
        File dir = new File(Environment.getExternalStorageDirectory() + File.separator + "gyrdata");
        if (!dir.exists())
            dir.mkdir();
        File fileGyro = new File(dir, collectFileNameGyro);
        File fileAccel = new File(dir, collectFileNameAccel);
        try {
            if (!fileGyro.exists())
                fileGyro.createNewFile();
            if (!fileAccel.exists())
                fileAccel.createNewFile();
            printWriterGyro = new PrintWriter(new BufferedWriter(new FileWriter(fileGyro)));
            printWriterGyro.println(headerTableGyro);

            printWriterAccel = new PrintWriter(new BufferedWriter(new FileWriter(fileAccel)));
            printWriterAccel.println(headerTableAccel);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("InlinedApi")
    private void stopCollect() {
        if (toast != null) {
            toast.cancel();
            handlerService.removeCallbacksAndMessages(new Object());
        }
        sensorManager.unregisterListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
        sensorManager.unregisterListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION));
        sensorManager.unregisterListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD));
        sensorManager.unregisterListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE));
        sensorManager.unregisterListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR));
        ((MyApplication) getApplication()).setHasService(false);
        if (printWriterGyro != null && listAngleSpeed != null && listAngleSpeed.size() > 0) {
            for (AngleSpeed angleSpeed : listAngleSpeed)
                printWriterGyro.println(angleSpeed.toString());

            printWriterGyro.close();
        }
        if (printWriterAccel != null && listAcceleratedSpeed != null && listAcceleratedSpeed.size() > 0) {
            for (AcceleratedSpeed acceleratedSpeed : listAcceleratedSpeed)
                printWriterAccel.println(acceleratedSpeed.toString());

            printWriterAccel.close();
        }
        timer.cancel();
    }

    public float getxAngle() {
        return xAngle;
    }

    public void setxAngle(float xAngle) {
        this.xAngle = xAngle;
    }

    public float getyAngle() {
        return yAngle;
    }

    public void setyAngle(float yAngle) {
        this.yAngle = yAngle;
    }

    public float getzAngle() {
        return zAngle;
    }

    public void setzAngle(float zAngle) {
        this.zAngle = zAngle;
    }

    public static int getTrunLeft() {
        return turnLeft;
    }

    public static void setTrunLeft(int trunLeft) {
        DataCollectService.turnLeft = trunLeft;
    }

    public static int getTrunRight() {
        return turnRight;
    }

    public static void setTrunRight(int trunRight) {
        DataCollectService.turnRight = trunRight;
    }

    public static int getAddSpeed() {
        return addSpeed;
    }

    public static void setAddSpeed(int addSpeed) {
        DataCollectService.addSpeed = addSpeed;
    }

    public static int getCutSpeed() {
        return cutSpeed;
    }

    public static void setCutSpeed(int cutSpeed) {
        DataCollectService.cutSpeed = cutSpeed;
    }
}
