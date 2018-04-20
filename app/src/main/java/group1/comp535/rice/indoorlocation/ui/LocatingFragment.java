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
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import group1.comp535.rice.indoorlocation.R;
import group1.comp535.rice.indoorlocation.adapter.WiFiDataAdapter;
import group1.comp535.rice.indoorlocation.data.LocationPoint;
import group1.comp535.rice.indoorlocation.data.WiFiData;

/**
 * Created by daiwei.ldw on 3/25/18.
 */

public class LocatingFragment extends Fragment implements SensorEventListener {

    List<WiFiData> wifidata = new LinkedList<WiFiData>();
    private WiFiDataAdapter adapter;
    WifiManager wifi;

    private SensorManager mSensorManager;
    private Sensor accelerometer;
    private long lastTimestamp = 0;

    private double speed_x = 0,speed_y = 0;   // these are the speed in x,y and z axis
    private double location_x = 0, location_y = 0;

    private LocationPoint currentLocation = null;
    private double spaceBetweenPoints_x = 5.6;
    private double spaceBetweenPoints_y = 3.2;

    private double last_x = 0, last_y = 0;

    public static LocatingFragment getInstance() {
        LocatingFragment sf = new LocatingFragment();
        return sf;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get an instance to the accelerometer
        this.mSensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        this.accelerometer = this.mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.locating_fragment, null);
        ListView listView = (ListView) v.findViewById(R.id.record_data);
        Button locatingButton = v.findViewById(R.id.locating);

        locatingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                determineLocation();
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
                        wifidata.add(new WiFiData(result.SSID, result.BSSID, WifiManager.calculateSignalLevel(result.level, 1000)));
                    }
                }
                adapter.notifyDataSetChanged();
                determineLocation();
            }
        }, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

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

        for (LocationPoint tempPoint:locations) {
            int new_cost = tempPoint.calculateWiFiSignalDistance(currentInfo);
//            Log.v("Location Cost","Location Name: "+tempPoint.getLocationName()+" Cost: "+new_cost);
            if (new_cost > 0 && new_cost < cost ){
                cost = new_cost;
                closetPoint = tempPoint;
            }
        }

        if (closetPoint != null) {
            this.currentLocation = closetPoint;
            Toast.makeText(getContext(), "Result Found: " + closetPoint.getLocationName(), Toast.LENGTH_LONG).show();
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
        if (sensorEvent.sensor.getType()==Sensor.TYPE_LINEAR_ACCELERATION){
            double tempx=sensorEvent.values[0];
            double tempy=sensorEvent.values[1];
            double tempz=sensorEvent.values[2];

            //Log.v("SensorData","Motion Detected: x:"+tempx+" y:"+tempy+" z:"+ tempz);

            long currentTimestamp = sensorEvent.timestamp;
            if (this.lastTimestamp == 0)
                this.lastTimestamp = currentTimestamp;

            float timeDiff = (currentTimestamp - this.lastTimestamp) / 1000000000.0f;

            this.speed_x += tempx*timeDiff;

            this.speed_y += tempy * timeDiff;

            this.location_x += this.speed_x * timeDiff;
            this.location_y += this.speed_y * timeDiff;

            this.lastTimestamp = currentTimestamp;

            //Log.v("SensorSpeed","New Speed: x "+this.speed_x+" y "+this.speed_y);
            //Log.v("SensorLocation","New Location: x "+this.location_x+" y "+this.location_y);
//            updateLocationInformation();
        }
        //gyroscope
        if (sensorEvent.sensor.getType()==Sensor.TYPE_GYROSCOPE) {
            double rotateX = sensorEvent.values[0];
            double rotateY = sensorEvent.values[0];
            double rotateZ = sensorEvent.values[0];

            //Log.v("SensorRotation","X: "+rotateX+" Y: "+rotateY);
            //Log.v("SensorLocation","New Location: x "+this.location_x+" y "+this.location_y);
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
}
