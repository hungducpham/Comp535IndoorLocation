package group1.comp535.rice.indoorlocation.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;
import android.os.BatteryManager;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import group1.comp535.rice.indoorlocation.R;
import group1.comp535.rice.indoorlocation.adapter.WiFiDataAdapter;
import group1.comp535.rice.indoorlocation.data.LocationPoint;
import group1.comp535.rice.indoorlocation.data.WiFiData;

/**
 * Created by daiwei.ldw on 3/25/18.
 */

public class WiFiLocationFragment extends Fragment implements AdapterView.OnItemSelectedListener {

    //buttons
    Button recordSensorButton;
    Button saveButton;
    Button discardButton;
    Button startMeasureButton;
    Button stopMeasureButton;
    Button resetWiFiButton;
    private EditText textX;
    private EditText textY;
    Intent batteryStatus;

    List<WiFiData> wifidata = new LinkedList<WiFiData>();
    private WiFiDataAdapter adapter;
    WifiManager wifi;

    String currentSelection;
    double currentBatteryLevel;
    Queue<Long> start_time;
    Handler timerHandler = new Handler();
    Runnable timeRunnable = new Runnable() {
        @Override
        public void run() {
            long currentTimeMillis = System.currentTimeMillis();
            //scanWiFiData();
            //Toast.makeText(getContext(), "Scanning wifi data ", Toast.LENGTH_LONG).show();
            scanWiFiData();
            timerHandler.postDelayed(this, 400); //every 0.4 second


        }
    };
    public static WiFiLocationFragment getInstance() {
        WiFiLocationFragment sf = new WiFiLocationFragment();
        return sf;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context context = getContext();
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        batteryStatus = context.registerReceiver(null, ifilter);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                (context.checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED)
                ||(context.checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED)
                || (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                || (context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                || (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)){
            requestPermissions(new String[]{Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.CHANGE_WIFI_STATE,
                            Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    1);


            toastShow("Requesting permission", 0);
            //Log.v("Permission","Permission Requesting");
            //After this point you wait for callback in onRequestPermissionsResult(int, String[], int[]) overriden method

        }else{
            toastShow("No need for permission", 0);
            //Log.v("Permission","No Permission Issue");
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
        View v = inflater.inflate(R.layout.wifi_location_fragment, null);
        ListView listView = (ListView) v.findViewById(R.id.recorded_wifi);

        recordSensorButton = v.findViewById(R.id.record_wifi);
        discardButton = v.findViewById(R.id.discard_wifi);
        saveButton =  v.findViewById(R.id.save_wifi);
        startMeasureButton =  v.findViewById(R.id.start_measure_wifi);
        stopMeasureButton = v.findViewById(R.id.stop_measure_wifi);
        resetWiFiButton = v.findViewById(R.id.reset_wifi);
        textX = v.findViewById(R.id.coordX);
        textY = v.findViewById(R.id.coordY);

        recordSensorButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recordSensorButtonClicked();
            }
        });
        discardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                discardButtonClicked();
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveButtonClicked();
            }
        });

        startMeasureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startMeasureButtonClicked();
            }
        });
        stopMeasureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopMeasureButtonClicked();
            }
        });
        resetWiFiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetWiFiButtonClicked();
            }
        });

        /**
        Spinner spinner = (Spinner) v.findViewById(R.id.curentLocationSpinner);
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> array_adapter = ArrayAdapter.createFromResource(getContext(),
                R.array.Position_Array, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        array_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        spinner.setAdapter(array_adapter);
        spinner.setOnItemSelectedListener(this);
        **/

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

                long end_time = System.currentTimeMillis();
                wifidata.clear();
                List<ScanResult> results = wifi.getScanResults();
                if(start_time != null && !start_time.isEmpty()) {
                    long start_timer = start_time.poll();
                    toastShow("scanned result received, size: " + results.size() + " in time: " + (end_time - start_timer) / 1000.0 + " seconds", 0);
                }
                for (ScanResult result : results) {
                    if (result.SSID.contentEquals("Rice Owls")  || result.SSID.contentEquals("Rice IoT")||result.SSID.contentEquals("Rice Visitor")||result.SSID.contentEquals("eduroam")){
//                        WifiManager.calculateSignalLevel(result.level, 100);
                        wifidata.add(new WiFiData(result.SSID, result.BSSID, WifiManager.calculateSignalLevel(result.level, 1000)));
                    }
                }
                adapter.notifyDataSetChanged();
                if (recordSensorButton.getVisibility() == View.INVISIBLE) {
                    textX.setVisibility(View.VISIBLE);
                    textY.setVisibility(View.VISIBLE);
                    discardButton.setVisibility(View.VISIBLE);
                    saveButton.setVisibility(View.VISIBLE);
                }
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
        tempPoint.setWifiData(this.wifidata);

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

        //Log.v("WiFi Refresh","Refreshing");
        long time = System.currentTimeMillis();
        start_time.add(time);
        boolean result = false;
        result = wifi.startScan();
        //toastShow("Wifi scanned, result: " + result, 0);



    }

    public void recordSensorButtonClicked() {
        recordSensorButton.setVisibility(View.INVISIBLE);
        startMeasureButton.setVisibility(View.INVISIBLE);
        toastShow("Scanning WiFi data, please wait", 0);
        scanWiFiData();


    }


    public void discardButtonClicked() {
        discardButton.setVisibility(View.INVISIBLE);
        saveButton.setVisibility(View.INVISIBLE);
        recordSensorButton.setVisibility(View.VISIBLE);
        startMeasureButton.setVisibility(View.VISIBLE);
        textX.setVisibility(View.INVISIBLE);
        textY.setVisibility(View.INVISIBLE);
    }


    public void saveButtonClicked() {
        discardButton.setVisibility(View.INVISIBLE);
        saveButton.setVisibility(View.INVISIBLE);
        recordSensorButton.setVisibility(View.VISIBLE);
        startMeasureButton.setVisibility(View.VISIBLE);
        textX.setVisibility(View.INVISIBLE);
        textY.setVisibility(View.INVISIBLE);
    }

    public void startMeasureButtonClicked() {
        startMeasureButton.setVisibility(View.INVISIBLE);
        stopMeasureButton.setVisibility(View.VISIBLE);
        currentBatteryLevel = getBatteryLevel();
        timerHandler.post(timeRunnable);
        start_time = new LinkedList<Long>();
    }
    public void stopMeasureButtonClicked() {
        stopMeasureButton.setVisibility(View.INVISIBLE);
        timerHandler.removeCallbacks(timeRunnable);
        double batteryLevel = getBatteryLevel();
        double consumption = batteryLevel - currentBatteryLevel;
        toastShow("Percentage battery consumption: " + consumption, 0);
        startMeasureButton.setVisibility(View.VISIBLE);

    }

    public void resetWiFiButtonClicked() {
        SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
        sharedPref.edit().clear().commit();
        toastShow("WiFi data cleared", 0);



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

    public void toastShow(String s, int n) {
        Toast.makeText(getContext(), s, n).show();
    }

    double getBatteryLevel() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        batteryStatus = getContext().registerReceiver(null, ifilter);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        double batteryPct = level/(float)scale;
        return batteryPct*100;
    }
}
