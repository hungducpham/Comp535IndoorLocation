package group1.comp535.rice.indoorlocation.ui;

import android.content.Context;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.os.Environment;

import java.io.BufferedWriter;

import java.io.OutputStreamWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.File;
import java.io.FileReader;

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
    private Button stopRecordingButton;
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
        mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI, 1000000);
        mSensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_UI, 1000000);



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
        this.textX = v.findViewById(R.id.txtX);
        this.textY = v.findViewById(R.id.txtY);
        this.stopRecordingButton = v.findViewById(R.id.stopRecording);
        //this.incX = v.findViewById(R.id.IncX);
        //this.incY = v.findViewById(R.id.IncY);

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

        resetAllButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetAllData();
            }
        });
        deleteLastButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                deleteLastItem();
            }
        });
        stopRecordingButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                stopRecordingButtonClick();
            }
        });
        return v;
    }
    public void recordButtonClick() {
        this.deleteLastButton.setVisibility(View.INVISIBLE);
        this.recordButton.setVisibility(View.INVISIBLE);
        this.resetAllButton.setVisibility(View.INVISIBLE);
        this.saveButton.setVisibility(View.INVISIBLE);
        this.stopRecordingButton.setVisibility(View.VISIBLE);
        sensorData = new ArrayList<Double>();
        recording = true;
        startRecordingTime = System.currentTimeMillis();
    }
    public void stopRecordingButtonClick() {
        this.saveButton.setVisibility(View.VISIBLE);
        this.recordButton.setVisibility(View.VISIBLE);
        this.textX.setVisibility(View.VISIBLE);
        this.textY.setVisibility(View.VISIBLE);
        this.deleteLastButton.setVisibility(View.VISIBLE);
        this.resetAllButton.setVisibility(View.VISIBLE);
        recording = false;
    }
    public void saveButtonClick() {
        //write data down to text file
        double X = Double.parseDouble(textX.getText().toString());
        double Y = Double.parseDouble(textY.getText().toString());
        writeSensorData(convertToString(sensorData), X*3.65*0.3048,Y*4*0.3048 );
        saveButton.setVisibility(View.INVISIBLE);
        recordButton.setVisibility(View.VISIBLE);
        Log.v("length of data", "Length of sensor data is " + sensorData.size()/6);
        textX.setVisibility(View.INVISIBLE);
        textY.setVisibility(View.INVISIBLE);
        this.stopRecordingButton.setVisibility(View.INVISIBLE);
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

            if (sensorEvent.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
                //record acceleration data
                double accx = sensorEvent.values[0];
                double accy = sensorEvent.values[1];
                double accz = sensorEvent.values[2];
                //Log.v("SensorData", "Acceleration detected X: " + accx + " Y: " + accy + " Z: " + accz);
                sensorData.add(accx);
                sensorData.add(accy);
                sensorData.add(accz);
            }
            if (sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                //record gyroscope data
                double gyx = sensorEvent.values[0];
                double gyy = sensorEvent.values[1];
                double gyz = sensorEvent.values[2];
                //Log.v("SensorData", "Gyroscope detected X: " + gyx + " Y: " + gyy + " Z: " + gyz);
                sensorData.add(gyx);
                sensorData.add(gyy);
                sensorData.add(gyz);
            }
        }
    }

    private void writeSensorData(String data, double X, double Y) {
        try {
            File file = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS), "sensor_data.txt");
            //if (file.exists()) Log.v("debug","File exist");
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


    private void deleteLastData() {

    }

    private void resetAllData() {
        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "sensor_data.txt");
        file.delete();
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

    private void deleteLastItem() {
        try {


            File file = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS), "sensor_data.txt");
            FileReader fr = new FileReader(file);
            BufferedReader myReader = new BufferedReader(fr);
            //copy all file into memory
            ArrayList<String> lines = new ArrayList<String>();
            String line = "";
            while((line = myReader.readLine()) != null ) {
                lines.add(line);
            }
            myReader.close();
            //remove 2 last lines
            lines.remove(lines.size() -1);
            lines.remove(lines.size() -1);

            //clear the file
            FileOutputStream fOut = new FileOutputStream(file,false);
            OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);
            myOutWriter.write("");

            //write it back
            fOut = new FileOutputStream(file,true);
            myOutWriter = new OutputStreamWriter(fOut);
            BufferedWriter myBufferedWriter = new BufferedWriter(myOutWriter);
            for(String li: lines) {
                myBufferedWriter.append(li);
                myBufferedWriter.append("\n");
            }
        }

        catch(IOException e) {
            Log.e("deleteLast", "Delete last item error");
        }
    }





}
