package com.example.vicky.bikesafe;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Build;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends AppCompatActivity implements SensorEventListener, TextToSpeech.OnInitListener{

    private final int N_SAMPLE = 300;
    private static List<Float> ax;
    private static List<Float> ay;
    private static List<Float> az;
    private static List<Float> gx;
    private static List<Float> gy;
    private static List<Float> gz;
    private static List<Float> am;
    private static List<Float> gm;

    private static List<Float> dev_a;
    private static List<Float> dev_ax;
    private static List<Float> dev_ay;
    private static List<Float> dev_az;


    private static List<Float> dataframe;

    private TextView txt_car;
    private TextView txt_bike;
    private TextView txt_walk;
    private TextView txt_cycle;
    private TextView txt_still;

    private  TextToSpeech textToSpeech;
    private AudioManager adm;
    private NotificationManager mNotificationManager;

    private float[] results;
    private String[] labels = {"Motor-Bike","car", "Walking", "Cycling", "Still"};

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mGyroscope;

    FileWriter acc, gyro;

    private ActivityInference activityInference;

    public MainActivity() {
    }


    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txt_car = (TextView)findViewById(R.id.car_prob);
        txt_bike = (TextView)findViewById(R.id.bike_prob);
        txt_walk = (TextView)findViewById(R.id.walk_prob);
        txt_cycle = (TextView)findViewById(R.id.cycle_prob);
        txt_still = (TextView)findViewById(R.id.still_prob);

        ax = new ArrayList<>();
        ay = new ArrayList<>();
        az = new ArrayList<>();

        gx = new ArrayList<>();
        gy = new ArrayList<>();
        gz = new ArrayList<>();

        am = new ArrayList<>();
        gm = new ArrayList<>();

        dev_a = new ArrayList<>();

        dev_ax = new ArrayList<>();
        dev_ay = new ArrayList<>();
        dev_az = new ArrayList<>();

        dataframe = new ArrayList<>();
        try {
            acc = new FileWriter(new File(getStorageDir(), "acc_sensors_" + System.currentTimeMillis() + ".csv"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            gyro = new FileWriter(new File(getStorageDir(), "gyro_sensors_" + System.currentTimeMillis() + ".csv"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        textToSpeech = new TextToSpeech(this, this);
        textToSpeech.setLanguage(Locale.US);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        adm = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        if(mAccelerometer !=null){
            mSensorManager.registerListener(MainActivity.this, mAccelerometer, 20000);
//            Log.d("TAG", "onCreate: Registered accelerometr listener");

        }

        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if(mGyroscope !=null){
            mSensorManager.registerListener(MainActivity.this, mGyroscope, 20000);
//            Log.d("TAG", "onCreate: Registered Gyroscope listener");

        }
        activityInference = new ActivityInference(getApplicationContext());

        try {
            InputStreamReader is = new InputStreamReader(getAssets()
                    .open("assets/Bike/2019-05-31_00-21-22/accelerometerLinear.csv"));

            BufferedReader reader = new BufferedReader(is);
            reader.readLine();
            String line;
            StringTokenizer st = null;
            while ((line = reader.readLine()) != null) {
                st = new StringTokenizer(line, ",");
                Log.d("sesnor type ", "sensor data at time stamp " + st.nextToken() + " ax= " + st.nextToken() +"ay= " + st.nextToken() );
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        drivingModePrediction();
        Sensor sensor = sensorEvent.sensor;
//        Log.d("sesnor type ", "sensor type is " + sensor.getType());
        if(sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION){
//            Log.d("TAG", "onSensorChanged: AX " + sensorEvent.values[0] + "AY "
//                    + sensorEvent.values[1] + "AZ" + sensorEvent.values[2]);
            ax.add(sensorEvent.values[0]);
            ay.add(sensorEvent.values[1]);
            az.add(sensorEvent.values[2]);
            try {
                acc.write(String.format("%d; %f; %f; %f\n", sensorEvent.timestamp,sensorEvent.values[0], sensorEvent.values[1], sensorEvent.values[2]));
            } catch (IOException e) {
                e.printStackTrace();
            }

        }else if(sensor.getType() == Sensor.TYPE_GYROSCOPE){
//            Log.d("TAG", "onSensorChanged: GX " + sensorEvent.values[0] + "GY "
//                    + sensorEvent.values[1] + "GZ " + sensorEvent.values[2]);
            gx.add(sensorEvent.values[0]);
            gy.add(sensorEvent.values[1]);
            gz.add(sensorEvent.values[2]);
            try {
                gyro.write(String.format("%d; %f; %f; %f\n", sensorEvent.timestamp,sensorEvent.values[0], sensorEvent.values[1], sensorEvent.values[2]));
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    private String getStorageDir() {
        return this.getExternalFilesDir(null).getAbsolutePath();

    }
    private void drivingModePrediction() {
//        Log.d("size = ", "drivingModePrediction: " + ax.size() );
        if(ax.size() == N_SAMPLE && gx.size() == N_SAMPLE){
            float sam=0,sgm=0, sax=0, say=0, saz=0 , mna, mng, mnax, mnay, mnaz ;

//            magnitude of acc. and gyro
            for (int i=0;i<N_SAMPLE;i++){
                am.add((float) Math.sqrt(Math.pow(ax.get(i), 2) + Math.pow(ay.get(i), 2)
                 + Math.pow(az.get(i), 2)));

                gm.add((float) Math.sqrt(Math.pow(gx.get(i), 2) + Math.pow(gy.get(i), 2)
                        + Math.pow(gz.get(i), 2)));

            }
//          sum of the lsit
            for(int i=0; i<N_SAMPLE; i++){
                sam += am.get(i);
                sgm += gm.get(i);

                sax += ax.get(i);
                say += ay.get(i);
                saz += az.get(i);

            }
//          mean of the list
            mna = (float) (sam/(N_SAMPLE*1.0));
            mng = (float) (sgm/(N_SAMPLE*1.0));

            mnax = (float) (sax/(N_SAMPLE*1.0));
            mnay = (float) (say/(N_SAMPLE*1.0));
            mnaz = (float) (saz/(N_SAMPLE*1.0));

//            deviation
            for(int i=0 ;i <N_SAMPLE; i++){
                dev_a.add(mna-am.get(i));
                dev_ax.add(mnax-ax.get(i));
                dev_ay.add(mnay-ay.get(i));
                dev_az.add(mnaz-az.get(i));
            }

            dataframe.addAll(gx);
            dataframe.addAll(gy);
            dataframe.addAll(gz);

            dataframe.addAll(ax);
            dataframe.addAll(ay);
            dataframe.addAll(az);

            dataframe.addAll(am);
            dataframe.addAll(gm);

            dataframe.addAll(dev_ax);
            dataframe.addAll(dev_ay);
            dataframe.addAll(dev_az);

            dataframe.addAll(dev_a);

            results = activityInference.getActivityProb(toFloatArray(dataframe));

            txt_bike.setText(Float.toString(round(results[0],2)));
            txt_car.setText(Float.toString(round(results[1],2)));
            txt_walk.setText(Float.toString(round(results[2],2)));
            txt_cycle.setText(Float.toString(round(results[3],2)));
            txt_still.setText(Float.toString(round(results[4],2)));


            //clear all values
            ax.clear();ay.clear();az.clear();
            gx.clear();gy.clear();gz.clear();
            am.clear();gm.clear();
            dev_a.clear();dev_ax.clear();dev_ay.clear();dev_az.clear();
            dataframe.clear();
        }
    }
    private float[] toFloatArray(List<Float> list)
    {
        int i = 0;
        float[] array = new float[list.size()];

        for (Float f : list) {
            array[i++] = (f != null ? f : Float.NaN);
        }
        return array;
    }

    public static float round(float d, int decimalPlace) {
        BigDecimal bd = new BigDecimal(Float.toString(d));
        bd = bd.setScale(decimalPlace, BigDecimal.ROUND_HALF_UP);
        return bd.floatValue();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
    protected void changeInterruptionFiler(int interruptionFilter){
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M){ // If api level minimum 23
            // If notification policy access granted for this package
            if(mNotificationManager.isNotificationPolicyAccessGranted()){
                // Set the interruption filter
                mNotificationManager.setInterruptionFilter(interruptionFilter);
            }else {
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                startActivity(intent);
            }
        }
    }

    @Override
    public void onInit(int i) {
        Timer time = new Timer();
        time.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if(results == null || results.length==0){
                    return;
                }
                float max =-1;
                int idx = -1;
                for (int i=0; i<results.length;i++){
                    if(results[i] >max){
                        idx = i;
                        max = results[i];
                    }
                }
                if(idx == 2){
                    changeInterruptionFiler(NotificationManager.INTERRUPTION_FILTER_NONE);
                }
                else {
                    changeInterruptionFiler(NotificationManager.INTERRUPTION_FILTER_ALL);
                }
                textToSpeech.speak(labels[idx], TextToSpeech.QUEUE_ADD, null, Integer.toString(new Random().nextInt()));
            }
        }, 200, 5000);
    }
}
