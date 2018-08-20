package com.yesway.drivingbehaviorcollect.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.yesway.drivingbehaviorcollect.service.DataCollectService;

import java.text.DecimalFormat;


public class MainActivity extends Activity implements OnClickListener {
    private static final String TAG = "MainActivity";
    private Button btnStart;
    private Button btnEnd;
    private Button btnSet;
    private TextView xAxisCalibrated;
    private TextView yAxisCalibrated;
    private TextView zAxisCalibrated;
    private SoundPool soundPool;
    private Intent collectService;
    private DataServiceConnection dataServiceConnection;

    private TextView txtTurnLeft;
    private TextView txtTurnRight;
    private TextView txtAddSpeed;
    private TextView txtCutSpeed;
    private TextView txtGyroAngle;
    private TextView txtGpsAngle;

    private TextView sensorXValue;
    private TextView sensorYValue;
    private TextView sensorZValue;


    private DataReceiver receiver;
    private DecimalFormat df = new DecimalFormat("###.##");
    private Messenger messengerActivity;
    private TextView txtStandAngle;
    private TextView txtStandSpeed;
    private TextView txtCollectRate;
    private TextView txtGyraRate;
    private Messenger messengerService;
    private TextView txtGspAvailable;
    private TextView txtAngleXvalue;
    private TextView txtAngleYvalue;
    private TextView txtAngleZvalue;
    private ProgressDialog dialog;
    private Handler handlerActivity = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    Bundle peekDataAngle = msg.peekData();
                    xAxisCalibrated.setText(df.format(peekDataAngle.getFloat("x")) + "");
                    yAxisCalibrated.setText(df.format(peekDataAngle.getFloat("y")) + "");
                    zAxisCalibrated.setText(df.format(peekDataAngle.getFloat("z")) + "");

                    //txtGyroAngle.setText(df.format((float) (Math.toDegrees(peekDataAngle.getFloat("x")) + 360) % 360) + "度");

                    txtAngleZvalue.setText(df.format((float) (Math.toDegrees(peekDataAngle.getFloat("x")) + 360) % 360) + "度");
                    txtAngleXvalue.setText(df.format((float) (Math.toDegrees(peekDataAngle.getFloat("y")) + 360) % 360) + "度");
                    txtAngleYvalue.setText(df.format((float) (Math.toDegrees(peekDataAngle.getFloat("z")) + 360) % 360) + "度");


                    if(peekDataAngle.getFloat("g")!=-1f)
                     txtGpsAngle.setText(peekDataAngle.getFloat("g") + "");

                    txtGspAvailable.setText(String.valueOf(MyApplication.isGpsIsAvailable()));
                    break;
                case 1:
                    Bundle peekData = msg.peekData();
                    txtTurnLeft.setText(peekData.getInt("turnLeft") + "次");
                    txtTurnRight.setText(peekData.getInt("turnRight") + "次");
                    txtAddSpeed.setText(peekData.getInt("addSpeed") + "次");
                    txtCutSpeed.setText(peekData.getInt("cutSpeed") + "次");

                    break;
                case 2:
                    float[] peekRotation = msg.peekData().getFloatArray("angleArray");
                    double azimuth = (Math.toDegrees(peekRotation[0]) + 360) % 360;
                    double pitch = (Math.toDegrees(peekRotation[1]) + 360) % 360;
                    double roll = (Math.toDegrees(peekRotation[2]) + 360) % 360;

                    sensorXValue.setText(df.format(azimuth));

                    sensorYValue.setText(df.format(pitch));

                    sensorZValue.setText(df.format(roll));
                default:
                    break;
            }
		
		/*	bundle.putFloat("turnLeft",trunLeft);
			bundle.putFloat("turnRight",trunRight);
			bundle.putFloat("addSpeed",addSpeed);。
			bundle.putFloat("cutSpeed",cutSpeed);*/

        }

        ;
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        openGPS();
        initView();

       // initVoice();

       // TTSController.getInstance(this).playTextFromLinkedList("亲，右转弯了");
        runOnUiThread(new Runnable() {

            @Override
            public void run() {

            }

        });

    }

   /* private void initVoice() {
        TTSController ttsManager = TTSController.getInstance(this);// 初始化语音模块
        ttsManager.init();
        setVolumeControlStream(AudioManager.STREAM_MUSIC);// 设置声音控制
        TTSController.getInstance(this).startSpeaking();
    }*/

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_start:
                xAxisCalibrated.setText("");
                yAxisCalibrated.setText("");
                zAxisCalibrated.setText("");

                txtGyroAngle.setText("");
                txtGpsAngle.setText("");

                txtTurnLeft.setText("");
                txtTurnRight.setText("");
                txtAddSpeed.setText("");
                txtCutSpeed.setText("");
                sensorXValue.setText("");
                sensorYValue.setText("");
                sensorZValue.setText("");

                txtAngleZvalue.setText("");
                txtAngleXvalue.setText("");
                txtAngleYvalue.setText("");

                txtGspAvailable.setText(String.valueOf(MyApplication.isGpsIsAvailable()));

                startService(collectService);
                bindService(collectService, dataServiceConnection, Service.BIND_AUTO_CREATE);
                break;
            case R.id.btn_end:
                if (((MyApplication) getApplication()).isHasService()) {
                    dialog = new ProgressDialog(MainActivity.this);
                    dialog.setMessage("数据保存中..");
                    dialog.show();
                    unbindService(dataServiceConnection);
                    stopService(collectService);
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            dialog.dismiss();
                        }
                    },1000);
                }
                break;
            case R.id.btn_set:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                LayoutInflater inflater = LayoutInflater.from(this);
                final View view = inflater.inflate(R.layout.login, null);
                builder.setIcon(R.drawable.ic_launcher);
                builder.setTitle("自定义输入框");
                builder.setView(view);
                builder.setNegativeButton("确定",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                EditText txtAngle = (EditText) view.findViewById(R.id.et_angle);
                                EditText txtSpeed = (EditText) view.findViewById(R.id.et_speed);
                                EditText txtRate = (EditText) view.findViewById(R.id.et_collect_rate);

                                if(!TextUtils.isEmpty(txtAngle.getText().toString())){
                                    MyApplication.setAngle(Integer.parseInt(txtAngle.getText().toString()));
                                    txtStandAngle.setText(String.valueOf(MyApplication.getAngle()));
                                }
                                if(!TextUtils.isEmpty(txtSpeed.getText().toString())){
                                    MyApplication.setSpeed(Integer.parseInt(txtSpeed.getText().toString()));
                                    txtStandSpeed.setText(String.valueOf(MyApplication.getSpeed()));
                                }
                                if(!TextUtils.isEmpty(txtRate.getText().toString())){
                                    MyApplication.setCollectRateGyro(Integer.parseInt(txtRate.getText().toString()));
                                    txtGyraRate.setText(String.valueOf(MyApplication.getCollectRateGyro()));
                                }
                            }
                        });
                builder.setPositiveButton("取消",
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // TODO Auto-generated method stub

                            }
                        });
                builder.show();
                break;
            case R.id.btn_set_collectrate:
                AlertDialog.Builder dialog = new  AlertDialog.Builder(this);
                dialog.setTitle("采集频率设置");
                dialog.setSingleChoiceItems(new String[]{"0毫秒", "20毫秒", "67毫秒", "200毫秒"}, MyApplication.getCollectRate(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        MyApplication.setCollectRate(which);
                        initCollectRate();
                        dialog.dismiss();
                    }
                });
            /*    dialog.setNegativeButton("确定",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        });
                dialog.setPositiveButton("取消",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        });*/
                dialog.show();
                break;

            default:
                break;
        }
    }

    class DataServiceConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            //dataCollectService = ((DataCollectBinder)service).getService();
            //messengerService = dataCollectService.getMessagerService();
            messengerService = new Messenger(service);
            messengerActivity = new Messenger(handlerActivity);
            Message msg = new Message();
            msg.what = 0;
            msg.replyTo = messengerActivity;
            try {
                messengerService.send(msg);
            } catch (RemoteException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
		
		/*	Message obtain = Message.obtain();
			obtain.what = 1;
			//handler.sendMessageDelayed(obtain, 1000);
*/
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (dataServiceConnection == null)
            dataServiceConnection = new DataServiceConnection();

        if (collectService == null)
            collectService = new Intent(this, DataCollectService.class);

        if (((MyApplication) getApplication()).isHasService())
            bindService(collectService, dataServiceConnection, Service.BIND_AUTO_CREATE);
		
		/*IntentFilter filter = new IntentFilter();
		filter.addAction(AppConfig.BroadcastReceiver.RECEIVER_TYPE);
		registerReceiver(receiver,filter);*/
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (((MyApplication) getApplication()).isHasService())
            unbindService(dataServiceConnection);
        //unregisterReceiver(receiver);
        //handler.removeMessages(1);
    }

    private void

    initView() {
        //df = new DecimalFormat("#.##");
        findViewById(R.id.btn_start).setOnClickListener(this);
        findViewById(R.id.btn_end).setOnClickListener(this);
        findViewById(R.id.btn_set).setOnClickListener(this);
        findViewById(R.id.btn_set_collectrate).setOnClickListener(this);
        xAxisCalibrated = (TextView) this.findViewById(R.id.value_x_axis_calibrated);
        yAxisCalibrated = (TextView) this.findViewById(R.id.value_y_axis_calibrated);
        zAxisCalibrated = (TextView) this.findViewById(R.id.value_z_axis_calibrated);

        txtTurnLeft = (TextView) this.findViewById(R.id.txt_turn_left);
        txtTurnRight = (TextView) this.findViewById(R.id.txt_turn_right);
        txtAddSpeed = (TextView) this.findViewById(R.id.txt_add_speed);
        txtCutSpeed = (TextView) this.findViewById(R.id.txt_cut_speed);

        sensorXValue = (TextView) this.findViewById(R.id.txt_sensorXValue);
        sensorYValue = (TextView) this.findViewById(R.id.txt_sensorYValue);
        sensorZValue = (TextView) this.findViewById(R.id.txt_sensorZValue);
        txtGyraRate = (TextView) this.findViewById(R.id.txt_gyra_rate);

        txtAngleZvalue = (TextView) findViewById(R.id.txt_angle_x);
        txtAngleXvalue = (TextView) findViewById(R.id.txt_angle_y);
        txtAngleYvalue = (TextView) findViewById(R.id.txt_angle_z);


        txtGpsAngle = (TextView) findViewById(R.id.txt_gps_angle);
        txtGyroAngle = (TextView) findViewById(R.id.txt_gyro_angle);
        txtStandAngle = (TextView) findViewById(R.id.txt_stand_angle);
        txtStandSpeed = (TextView) findViewById(R.id.txt_stand_speed);

        txtCollectRate = (TextView) findViewById(R.id.txt_collect_rate);
        txtGspAvailable = (TextView) findViewById(R.id.txt_gsp_available);

        txtStandAngle.setText(String.valueOf(MyApplication.getAngle()));
        txtStandSpeed.setText(String.valueOf(MyApplication.getSpeed()));
        txtGspAvailable.setText(String.valueOf(MyApplication.isGpsIsAvailable()));
        txtGyraRate.setText(String.valueOf(MyApplication.getCollectRateGyro()));
        initCollectRate();
        receiver = new DataReceiver();
    }

    private void initCollectRate() {
        int rate = 0 ;
        switch (MyApplication.getCollectRate()){
            case SensorManager.SENSOR_DELAY_FASTEST:
                rate = 0;
                break;
            case SensorManager.SENSOR_DELAY_GAME:
                rate = 20;
                break;
            case SensorManager.SENSOR_DELAY_UI:
                rate = 67;
                break;
            case SensorManager.SENSOR_DELAY_NORMAL:
                rate = 200;
                break;
        }
        txtCollectRate.setText(String.valueOf(rate));
    }

    private void openGPS() {
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "GPS模块正常", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "请开启GPS！", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        startActivityForResult(intent, 0);
    }

    class DataReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getExtras();
            Toast.makeText(MainActivity.this, "values:" + bundle.getFloat("x") + "", Toast.LENGTH_SHORT).show();
        }
    }
}
