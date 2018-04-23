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

import static java.util.Collections.min;

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
//        mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
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
                        wifidata.add(new WiFiData(result.SSID, result.BSSID, WifiManager.calculateSignalLevel(result.level, 100)));
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
        if (sensorEvent.sensor.getType()==Sensor.TYPE_LINEAR_ACCELERATION){
            double tempx=sensorEvent.values[0];
            double tempy=sensorEvent.values[1];
            double tempz=sensorEvent.values[2];

            Log.v("SensorData","Motion Detected: x:"+tempx+" y:"+tempy+" z:"+ tempz);

            long currentTimestamp = sensorEvent.timestamp;
            if (this.lastTimestamp == 0)
                this.lastTimestamp = currentTimestamp;

            float timeDiff = (currentTimestamp - this.lastTimestamp) / 1000000000.0f;

            this.speed_x += tempx*timeDiff;

            this.speed_y += tempy * timeDiff;

            this.location_x += this.speed_x * timeDiff;
            this.location_y += this.speed_y * timeDiff;

            this.lastTimestamp = currentTimestamp;

            Log.v("SensorSpeed","New Speed: x "+this.speed_x+" y "+this.speed_y);
            Log.v("SensorLocation","New Location: x "+this.location_x+" y "+this.location_y);
//            updateLocationInformation();
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
