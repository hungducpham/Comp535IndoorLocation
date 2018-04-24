package group1.comp535.rice.indoorlocation.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.TriggerEventListener;
import android.hardware.TriggerEvent;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import group1.comp535.rice.indoorlocation.R;
import group1.comp535.rice.indoorlocation.adapter.WiFiDataAdapter;
import group1.comp535.rice.indoorlocation.data.LocationPoint;
import group1.comp535.rice.indoorlocation.data.WiFiData;
import group1.comp535.rice.indoorlocation.utils.NeuralNetwork;

import static java.util.Collections.min;

/**
 * Created by daiwei.ldw on 3/25/18.
 */

public class LocatingFragment extends Fragment implements SensorEventListener {

    List<WiFiData> wifidata = new LinkedList<WiFiData>();
    private WiFiDataAdapter adapter;
    WifiManager wifi;

    private SensorManager mSensorManager;
    private TriggerEventListener mListener;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private Sensor significantMotion;
    private long lastTimestamp = 0;

    private double speed_x = 0,speed_y = 0;   // these are the speed in x,y and z axis
    private double location_x = 0, location_y = 0;

    private LocationPoint currentLocation = null;
    private double spaceBetweenPoints_x = 5.6;
    private double spaceBetweenPoints_y = 3.2;

    private NeuralNetwork nn_model;
    private boolean moving;
    private boolean stopping;
    private ArrayList<Double> sensorData;
    private LocationPoint lastRecordedLocation;
    private boolean recordingSensorData = false;
    private boolean recording;
    private ArrayList<Double> sensorDataRecorded;
    private Button recordButton;
    private Button stopButton;
    private Button discardButton;
    private Button saveButton;
    private Button resetButton;
    private EditText textX;
    private EditText textY;
    private double last_x = 0, last_y = 0;
    private double currentTimeInMillis;
    private double lastMovedDistance;
    

    private int mode = 1;

    public static LocatingFragment getInstance() {
        LocatingFragment sf = new LocatingFragment();
        return sf;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Get an instance to the accelerometer,
        this.mSensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);

        this.accelerometer = this.mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        this.gyroscope = this.mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI, 250);
        mSensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_UI,250);

        /**this.significantMotion = this.mSensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);
         TriggerEventListener mTriggerEventListener = new TriggerEventListener() {
        @Override
        public void onTrigger(TriggerEvent event) {
        Toast.makeText(getContext(), "moving", Toast.LENGTH_LONG).show();
        Log.v("moving", "moving");
        moving= true;
        sensorData = new ArrayList<Double>();
        }
        };
         mSensorManager.requestTriggerSensor(mTriggerEventListener, this.significantMotion);
         **/
        this.nn_model = new NeuralNetwork();
        this.nn_model.importFromFile(this.mode);
        this.sensorData = new ArrayList<Double>();
        //Log.v("", "Done loading neural network");
        View v = inflater.inflate(R.layout.locating_fragment, null);
        ListView listView = (ListView) v.findViewById(R.id.record_data);
        Button locatingButton = v.findViewById(R.id.locating);
        recordButton = v.findViewById(R.id.record);
        saveButton = v.findViewById(R.id.save);
        discardButton = v.findViewById(R.id.discard);
        resetButton = v.findViewById(R.id.reset);
        stopButton = v.findViewById(R.id.stop);
        textX = v.findViewById(R.id.txtX);
        textY = v.findViewById(R.id.txtY);

        locatingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                determineLocation();
            }
        });
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recordButtonClicked();
            }
        });
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveButtonClicked();
            }
        });
        discardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                discardButtonClicked();
            }
        });
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetButtonClicked();
            }
        });
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopButtonClicked();
            }
        });

        adapter = new WiFiDataAdapter(getActivity(),wifidata);
        listView.setAdapter(adapter);

        wifi = (WifiManager) getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (!wifi.isWifiEnabled())
        {
            Toast.makeText(getContext().getApplicationContext(), "wifi is disabled..making it enabled", Toast.LENGTH_LONG).show();
            wifi.setWifiEnabled(true);
        }

        getContext().registerReceiver(new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context c, Intent intent)
            {
                wifidata.clear();
                List<ScanResult> results = wifi.getScanResults();
                Log.v("Wifi Data Size",results.size()+"");
                for (ScanResult result : results) {
                    if (result.SSID.contentEquals("Rice Owls")  || result.SSID.contentEquals("Rice IoT")||result.SSID.contentEquals("Rice Visitor")||result.SSID.contentEquals("eduroam")){
//                        WifiManager.calculateSignalLevel(result.level, 100);
                        wifidata.add(new WiFiData(result.SSID, result.BSSID, WifiManager.calculateSignalLevel(result.level, 100)));
                    }
                }
                adapter.notifyDataSetChanged();
                determineLocation();
            }
        }, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        this.currentTimeInMillis = System.currentTimeMillis();
        this.scanWiFiData();

        return v;
    }

    private void determineLocation() {
        //get data from shared preference
        SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
        Set<String> nameList = sharedPref.getStringSet("namelist",new HashSet<String>());

//        Log.v("Shared Preference Data", nameList.toString());

        List<LocationPoint> locations = new ArrayList<>();
        Gson gson = new Gson();

        for (String tempName:nameList) {
            String tempData = sharedPref.getString(tempName,"");
//            Log.v("Shared Preference Data",tempData);
            if (tempData.length()!= 0){
                LocationPoint obj = gson.fromJson(tempData, LocationPoint.class);
                locations.add(obj);
            }
        }

        // calculate cost, choose the lowest one
        int cost = Integer.MAX_VALUE;
        LocationPoint closetPoint = null;

        LocationPoint currentInfo = new LocationPoint();
        currentInfo.setWifidata(this.wifidata);

        List<LocationPoint> knnPoints = new ArrayList();
        List<Integer> knnDistance = new ArrayList<>();

        int knnSize = 3;

        for (LocationPoint tempPoint:locations) {
            int new_cost = tempPoint.calculateWiFiSignalDistance(currentInfo);
//            Log.v("Location Cost","Location Name: "+tempPoint.getLocationName()+" Cost: "+new_cost);

            if (new_cost > 0  ){
                if (knnDistance.size() <= knnSize){
                    knnDistance.add(new_cost);
                    knnPoints.add(tempPoint);
                }else if (min(knnDistance)>new_cost){
                    int index = knnDistance.indexOf(min(knnDistance));
                    knnDistance.remove(index);
                    knnPoints.remove(index);

                    knnDistance.add(new_cost);
                    knnPoints.add(tempPoint);
                }
            }
        }

        double x = 0, y = 0;
        double distance_under = 0;
        for (int i = 0; i < knnDistance.size();i++){
            x += ((double)knnPoints.get(i).getX())/knnDistance.get(i);
            y += ((double)knnPoints.get(i).getY())/knnDistance.get(i);

            distance_under += ((double)1)/((double)knnDistance.get(i));
        }

        x = x/distance_under;
        y = y/distance_under;

        if (knnPoints.size()!=0) {

            Log.v("locating",x+"");
            Log.v("locating",y+"");
            Log.v("locating",distance_under+"");

            Log.v("Locating", knnDistance.get(0) +"");

            // Cloest point analysis
            int index = knnDistance.indexOf(min(knnDistance));
            this.currentLocation = knnPoints.get(index);
            knnPoints.remove(index);

            double diff_x = 0, diff_y = 0;
            if (knnPoints.size() == 2)
            {
                // Triangle Analysis
                int diff1_x = knnPoints.get(0).getX()-this.currentLocation.getX();
                int diff1_y = knnPoints.get(0).getY()-this.currentLocation.getY();
                int diff2_x = knnPoints.get(1).getX()-this.currentLocation.getX();
                int diff2_y = knnPoints.get(1).getY()-this.currentLocation.getY();

                // determine if it is triangle in the same block
                if (Math.abs(diff1_x) <= 1 && Math.abs(diff1_y) <= 1 && Math.abs(diff2_x) <= 1 && Math.abs(diff2_y) <= 1) {
                    if (diff1_x * diff2_x >= 0 && diff1_y * diff2_y >= 0) {
                        // same block and in triangle
                        if ((Math.abs(diff1_x) + Math.abs(diff2_x) + Math.abs(diff2_y)+ Math.abs(diff1_y) == 3 )) {
                            if (Math.abs(diff1_x) + Math.abs(diff2_x) == 2) {
                                diff_x = 0.125;
                                diff_y = 0.375;
                            }else{
                                diff_x = 0.375;
                                diff_y = 0.125;
                            }
                        }else if ((Math.abs(diff1_x) + Math.abs(diff2_x) + Math.abs(diff2_y)+ Math.abs(diff1_y) == 2 )){
                            diff_x = 0.25;
                            diff_y = 0.25;
                        }

                        if (diff1_x <0 || diff2_x < 0) {
                            diff_x = diff_x * -1;
                        }

                        if (diff1_y <0 || diff2_y < 0) {
                            diff_y = diff_y * -1;
                        }
                    }
                }
            }



//            this.currentLocation = closetPoint;
            DecimalFormat df = new DecimalFormat();
            df.setMaximumFractionDigits(2);
            Toast.makeText(getContext(), "Result Found: x:" + df.format(x)+" y: "+df.format(y) + " closest = "+this.currentLocation.getLocationName() + "Traingle Analysis x: "+ df.format(this.currentLocation.getX()+diff_x)+" y: "+df.format(this.currentLocation.getY()+diff_y) , Toast.LENGTH_LONG).show();
        }
        else{
//            Toast.makeText(getContext(),"No Result Found",Toast.LENGTH_LONG).show();
            Log.v("Locating","No Results");
        }

    }

    public void scanWiFiData()
    {
        wifi.startScan();
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if(recordingSensorData) //mode enables recording of sensor data for training
        {
            if (recording) {
                if (sensorEvent.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
                    double accX = sensorEvent.values[0];
                    double accY = sensorEvent.values[1];
                    double accZ = sensorEvent.values[2];

                    sensorDataRecorded.add(accX);
                    sensorDataRecorded.add(accY);
                    //sensorDataRecorded.add(accZ);
                }
                if (sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                    double rotateX = sensorEvent.values[0];
                    double rotateY = sensorEvent.values[0];
                    double rotateZ = sensorEvent.values[0];
                    sensorDataRecorded.add(rotateX);
                    sensorDataRecorded.add(rotateY);
                    //sensorDataRecorded.add(rotateZ);
                }
            }
        }

        else
            {
                double currentTime = System.currentTimeMillis();


                if (sensorEvent.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
                    double accX = sensorEvent.values[0];
                    double accY = sensorEvent.values[1];
                    //double accZ = sensorEvent.values[2];

                    sensorData.add(accX);
                    sensorData.add(accY);
                    //sensorData.add(accZ);

                }
                //gyroscope
                if (sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                    double rotateX = sensorEvent.values[0];
                    double rotateY = sensorEvent.values[0];
                    //double rotateZ = sensorEvent.values[0];
                    sensorData.add(rotateX);
                    sensorData.add(rotateY);
                    //sensorData.add(rotateZ);

                }

                if (currentTime - this.currentTimeInMillis >= 3000) //interval of calculating imu distance ~ 2 sec
                {
                    currentTimeInMillis = currentTime;
                    Double[] input1 = new Double[0];
                    input1 = sensorData.toArray(input1);
                    sensorData = new ArrayList<Double>();
                    double[] input = new double[input1.length];
                    for (int i = 1; i < input1.length; i++) {
                        input[i] = input1[i].doubleValue();
                    }

                    double[] output = this.nn_model.feedForward(input);
                    double distance = Math.sqrt((output[0]*output[0])   + (output[1]*output[1]));
                    if (distance < 0.5) moving = false;
                    else {
                        if(!moving) {
                            moving = true;
                            lastMovedDistance = distance;
                            Toast.makeText(getContext(), "Movement detected: ", Toast.LENGTH_LONG).show();
                        }
                        else {
                            moving = false;
                            lastMovedDistance = 0;
                            Toast.makeText(getContext(), "Stopping detected: ", Toast.LENGTH_LONG).show();
                            
                        }
                    }
                    
                }
                if(moving) {
                    wifi.startScan();
                    determineLocation();
                }
            }

    }

    private void updateLocationInformation() {
        if (this.currentLocation == null || this.currentLocation.getX() == -1 || this.currentLocation.getY() == -1){
            return;
        }

        int new_x = this.currentLocation.getX();
        int new_y = this.currentLocation.getY();

        if (this.location_x >= this.spaceBetweenPoints_x){
            int times = (int)(this.location_x / this.spaceBetweenPoints_x);

            new_x = this.currentLocation.getX() + times;
            this.location_x = times*this.spaceBetweenPoints_x;
        }

        if (this.location_y >= this.spaceBetweenPoints_y){
            int times = (int)(this.location_y / this.spaceBetweenPoints_y);

            new_y = this.currentLocation.getY() + times;
            this.location_y -= times*this.spaceBetweenPoints_y;
        }

        String newPointName = "Position_"+new_x+"_"+new_y;
        this.currentLocation = new LocationPoint();
        this.currentLocation.setLocationName(newPointName);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private void recordButtonClicked() {
        if (recordingSensorData) {
            recording = true;
            sensorDataRecorded = new ArrayList<Double> ();
            recordButton.setVisibility(View.INVISIBLE);
            stopButton.setVisibility(View.VISIBLE);
        }
    }

    private void saveButtonClicked() {

        if (recordingSensorData) {
            double X = Double.parseDouble(textX.getText().toString());
            double Y = Double.parseDouble(textY.getText().toString());
            this.writeSensorData(convertToString(sensorDataRecorded), X * 3.542 * 0.3048, Y * 4 * 0.3048);
            saveButton.setVisibility(View.INVISIBLE);
            textX.setVisibility(View.INVISIBLE);
            textY.setVisibility(View.INVISIBLE);
            recordButton.setVisibility(View.VISIBLE);
            Toast.makeText(getContext(), "Length of recorded data is " + sensorDataRecorded.size(),Toast.LENGTH_LONG ).show();

        }
    }
    private void discardButtonClicked() {
        discardButton.setVisibility(View.INVISIBLE);
        recordButton.setVisibility(View.VISIBLE);
        textX.setVisibility(View.INVISIBLE);
        textY.setVisibility(View.INVISIBLE);
        saveButton.setVisibility(View.INVISIBLE);

    }
    private void resetButtonClicked() {
        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "sensor_data.txt");
        file.delete();
        Toast.makeText(getContext(),"sensor data reset", Toast.LENGTH_LONG).show();
    }
    private void stopButtonClicked() {
        if(recordingSensorData) {
            recording = false;
            stopButton.setVisibility(View.INVISIBLE);
            saveButton.setVisibility(View.VISIBLE);
            textX.setVisibility(View.VISIBLE);
            textY.setVisibility(View.VISIBLE);
            discardButton.setVisibility(View.VISIBLE);
            //Log.v("Debug", "Length of recorded data is " + sensorDataRecorded.size());
        }
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

    private void writeSensorData(String data, double X, double Y) {
        try {
            File file = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS), "sensor_data.txt");
            if (!file.exists()) Log.v("debug","File doesn't exist");
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
}
