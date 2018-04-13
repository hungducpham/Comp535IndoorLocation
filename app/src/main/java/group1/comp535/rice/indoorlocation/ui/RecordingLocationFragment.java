package group1.comp535.rice.indoorlocation.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.gson.Gson;

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

public class RecordingLocationFragment extends Fragment implements AdapterView.OnItemSelectedListener,SensorEventListener {

    List<WiFiData> wifidata = new LinkedList<WiFiData>();
    private WiFiDataAdapter adapter;
    WifiManager wifi;

    String currentSelection;

    private SensorManager sensorManager;
    double ax,ay,az;   // these are the acceleration in x,y and z axis

    public static RecordingLocationFragment getInstance() {
        RecordingLocationFragment sf = new RecordingLocationFragment();
        return sf;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                (getContext().checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED)
                ||(getContext().checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED)
                || (getContext().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)){
            requestPermissions(new String[]{Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.CHANGE_WIFI_STATE,Manifest.permission.ACCESS_COARSE_LOCATION},
                    1);

            Toast.makeText(getContext(),"Requesting Permission", Toast.LENGTH_LONG).show();
            Log.v("Permission","Permission Requesting");
            //After this point you wait for callback in onRequestPermissionsResult(int, String[], int[]) overriden method

        }else{
            Toast.makeText(getContext(),"No Need for permission", Toast.LENGTH_LONG).show();
            Log.v("Permission","No Permission Issue");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.v("Permission Result",requestCode+"");
        if (requestCode == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            Toast.makeText(getContext(),"Permission Granted", Toast.LENGTH_LONG).show();
            Log.v("Permission Result","Granted");

        }else{
            Toast.makeText(getContext(),"Permission Denied", Toast.LENGTH_LONG).show();
            Log.v("Permission Result","Denied");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.record_location_fragment, null);
        ListView listView = (ListView) v.findViewById(R.id.record_data);
        Button refreshButton = (Button) v.findViewById(R.id.refresh);
        Button recordButton = (Button) v.findViewById(R.id.record);

        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.v("WiFi Refresh","Refresh button pressed");
                scanWiFiData();
            }
        });

        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recordLocation();
            }
        });

        Spinner spinner = (Spinner) v.findViewById(R.id.curentLocationSpinner);
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> array_adapter = ArrayAdapter.createFromResource(getContext(),
                R.array.Position_Array, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        array_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        spinner.setAdapter(array_adapter);
        spinner.setOnItemSelectedListener(this);

        adapter = new WiFiDataAdapter(getActivity(),wifidata);
        listView.setAdapter(adapter);

        wifi = (WifiManager) getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifi.isWifiEnabled() == false)
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
                    if (result.SSID == "Rice Owls" || result.SSID == "Rice IoT"||result.SSID == "Rice Visitor"||result.SSID == "eduroam")
                        WifiManager.calculateSignalLevel(result.level, 10);
                        wifidata.add(new WiFiData(result.SSID, result.BSSID, WifiManager.calculateSignalLevel(result.level, 10)));
                }
                adapter.notifyDataSetChanged();
            }
        }, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
//        this.scanWiFiData();

        return v;
    }

    private void recordLocation() {
        if (this.currentSelection == null || this.currentSelection.length() == 0){
            Toast.makeText(getContext(),"Please make a selection",Toast.LENGTH_SHORT);
            return;
        }

        SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);

        LocationPoint tempPoint = new LocationPoint();
        tempPoint.setLocationName(this.currentSelection);
        tempPoint.setWifidata(this.wifidata);

        Gson gson = new Gson();
        String json = gson.toJson(tempPoint);

        Set<String> nameList =  sharedPref.getStringSet("namelist",new HashSet<String>());
        nameList.add(this.currentSelection);

        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(this.currentSelection,json);
        editor.putStringSet("namelist",nameList);
        editor.commit();
    }

    public void scanWiFiData()
    {

        Log.v("WiFi Refresh","Refreshing");
        wifi.startScan();

    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        Log.v("Select Item",adapterView.getItemAtPosition(i).toString());
        currentSelection = adapterView.getItemAtPosition(i).toString();
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
        Log.v("Select Item","No Item Selected");
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType()==Sensor.TYPE_ACCELEROMETER){
            ax=sensorEvent.values[0];
            ay=sensorEvent.values[1];
            az=sensorEvent.values[2];
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}
