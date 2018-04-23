package group1.comp535.rice.indoorlocation.ui;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import android.os.Environment;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.File;
import java.io.FileReader;
import com.google.gson.Gson;
import group1.comp535.rice.indoorlocation.R;
import java.util.ArrayList;


public class RecordingSensorEventFragment extends Fragment implements SensorEventListener {
    
    


    private SensorManager mSensorManager;
    private Sensor accelerometer;
    private long lastTimestamp = 0;
    private Sensor gyroscope;

    private ArrayList<Double> sensorData;
    private boolean recording;
    private Button recordButton;
    private Button saveButton;
    private Button deleteLastButton;
    private Button resetAllButton;
    private EditText textX;
    private EditText textY;
    int l = 8;
    int timeStep  = 1;
    long startRecordingTime;


    public static RecordingSensorEventFragment getInstance() {
        RecordingSensorEventFragment fragment = new RecordingSensorEventFragment();
        return fragment;
    }
    public RecordingSensorEventFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Get an instance to the accelerometer
        this.mSensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        this.accelerometer = this.mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        this.gyroscope = this.mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        mSensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);



    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_record_sensor_event_fragment, container, false);
        this.recordButton = v.findViewById(R.id.recordSensorData );
        this.saveButton = v.findViewById(R.id.saveData);
        this.deleteLastButton = v.findViewById(R.id.deleteLast);
        this.resetAllButton = v.findViewById(R.id.resetAll);
        this.textX = v.findViewById(R.id.textX);
        this.textY = v.findViewById(R.id.textY);
        sensorData = new ArrayList<Double>();
        recording = false;


        recordButton.setOnClickListener(new View.OnClickListener()  {
            @Override
            public void onClick(View view) {
                recordButtonClick();
            }
        });
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveButtonClick();
            }
        });

        return v;
    }
    public void recordButtonClick() {
        this.deleteLastButton.setVisibility(View.INVISIBLE);
        this.recordButton.setVisibility(View.INVISIBLE);
        this.resetAllButton.setVisibility(View.INVISIBLE);
        this.saveButton.setVisibility(View.INVISIBLE);
        sensorData = new ArrayList<Double>();
        recording = true;
        startRecordingTime = System.currentTimeMillis();
    }

    public void saveButtonClick() {
        //write data down to text file
        double X = Double.parseDouble(textX.getText().toString());
        double Y = Double.parseDouble(textY.getText().toString());
        writeSensorData(convertToString(sensorData), X,Y);
        saveButton.setVisibility(View.INVISIBLE);
        recordButton.setVisibility(View.VISIBLE);
        textX.setVisibility(View.INVISIBLE);
        textY.setVisibility(View.INVISIBLE);
    }

    public void setResetAllButtonClick() {
        //

    }
    @Override
     public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (recording) {
            if (System.currentTimeMillis() - startRecordingTime < timeStep*1000) {
                if (sensorEvent.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
                    //record acceleration data
                    double accx = sensorEvent.values[0];
                    double accy = sensorEvent.values[1];
                    double accz = sensorEvent.values[2];
                    Log.v("SensorData","Acceleration detected X: " + accx + " Y: " + accy + " Z: "+ accz);
                    sensorData.add(accx);
                    sensorData.add(accy);
                    sensorData.add(accz);
                }
                if (sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                    //record gyroscope data
                    double gyx = sensorEvent.values[0];
                    double gyy = sensorEvent.values[1];
                    double gyz = sensorEvent.values[2];
                    Log.v("SensorData","Gyroscope detected X: " + gyx + " Y: " + gyy + " Z: "+ gyz);
                    sensorData.add(gyx);
                    sensorData.add(gyy);
                    sensorData.add(gyz);
                }

            }
            else {
                recording = false;
                deleteLastButton.setVisibility(View.VISIBLE);
                recordButton.setVisibility(View.INVISIBLE);
                resetAllButton.setVisibility(View.VISIBLE);
                saveButton.setVisibility(View.VISIBLE);
                textX.setVisibility(View.VISIBLE);
                textY.setVisibility(View.VISIBLE);

            }
        }
    }

    private void writeSensorData(String data, double X, double Y) {
        try {
            File file = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS), "sensor_data.txt");
            if (file.exists()) Log.v("debug","File exist");
            FileOutputStream fOut = new FileOutputStream(file,true);
            OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);
            BufferedWriter myBufferedWriter = new BufferedWriter(myOutWriter);
            myBufferedWriter.append(data);
            myBufferedWriter.append("\n");
            myBufferedWriter.append("" + X + " " + Y);
            myBufferedWriter.append("\n");
            myBufferedWriter.close();
            fOut.close();
        }
        catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }


    private void deleteLastLine() {

    }

    private void resetAllData() {

    }

    private String convertToString(ArrayList<Double> a) {
        Double[] input = new Double[1];
        input = a.toArray( input);
        String output = "";
        for (Double i: input) {
            output += i.toString()+ " ";
        }
        return output;

    }





}
