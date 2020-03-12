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
import group1.comp535.rice.indoorlocation.utils.OtherUtils;


public class WiFiLocationFragment extends Fragment {

    //Buttons and text boxes
    Button recordSensorButton, saveButton, discardButton,startMeasureButton, stopMeasureButton, resetWiFiButton, testGPSButton;
    EditText textX, textY;


    Intent batteryStatus;

    /**
     * variables to handle wifi
     */
    List<WiFiData> wifidata = new LinkedList<WiFiData>();
    private WiFiDataAdapter adapter;
    WifiManager wifi;
    private List<LocationPoint> savedWifiLocations;
    private String wifiLocationTechnique = "KNN";

    //variables to determine current mode of program
    boolean determiningLocation = false;
    boolean recordingWifiData = false;
    boolean testGPS = false;
    boolean testWifiBatteryConsumption = false;


    //these variables are for testing of battery consumption of constantly broadcasting and receiving Wifi data
    double previousBatteryLevel;
    Handler timerHandler;
    Runnable timeRunnable;

    //the method called by main activity when initializing
    public static WiFiLocationFragment getInstance() {
        WiFiLocationFragment sf = new WiFiLocationFragment();
        return sf;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context context = getContext();

        requestPermissionCheck(context);
        initVariables(context);
    }

    /**
     * check if all the needed permissions are given
     * @param context
     */
    void requestPermissionCheck(Context context) {
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
            //After this point you wait for callback in onRequestPermissionsResult(int, String[], int[]) overriden method

        }else{
            toastShow("No need for permission", 0);
        }
    }

    /**
     * init variables for the fragment
     * @param context current context
     */
    void initVariables(Context context) {
        //init variables for the testing of wifi battery consumption
        //create a time runnable that repeats itself every 300 milliseconds
        if (testWifiBatteryConsumption) {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            batteryStatus = context.registerReceiver(null, ifilter);

            timerHandler = new Handler();
            timeRunnable = new Runnable() {
                @Override
                public void run() {
                    long currentTimeMillis = System.currentTimeMillis();
                    scanWiFiData();
                    timerHandler.postDelayed(this, 300); //every 0.3 second

                }
            };
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

        initButtons(v);

        setUpWifi(v, listView);
        return v;
    }


    public void initButtons(View v) {
        recordSensorButton = v.findViewById(R.id.record_wifi);
        discardButton = v.findViewById(R.id.discard_wifi);
        saveButton =  v.findViewById(R.id.save_wifi);
        startMeasureButton =  v.findViewById(R.id.start_measure_wifi);
        stopMeasureButton = v.findViewById(R.id.stop_measure_wifi);
        resetWiFiButton = v.findViewById(R.id.reset_wifi);
        textX = v.findViewById(R.id.coordX);
        textY = v.findViewById(R.id.coordY);
        testGPSButton = v.findViewById(R.id.gps_test);
        if (testWifiBatteryConsumption) {
            recordSensorButton.setVisibility(View.INVISIBLE);
            resetWiFiButton.setVisibility(View.INVISIBLE);
            startMeasureButton.setVisibility(View.VISIBLE);
        }
        else {
            recordSensorButton.setVisibility(View.VISIBLE);
            resetWiFiButton.setVisibility(View.VISIBLE);
            startMeasureButton.setVisibility(View.INVISIBLE);
        }


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
        testGPSButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                GPSButtonClicked();
            }
        });
    }

    /**
     * The method below handles Wifi activities
     *
     * @param v current view
     * @param listView current list view
     */
    public void setUpWifi(View v, ListView listView) {
        adapter = new WiFiDataAdapter(getActivity(),wifidata);
        listView.setAdapter(adapter);
        wifi = (WifiManager) getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifi.isWifiEnabled() == false) //if Wifi is disabled in your phone
        {
            Toast.makeText(getContext().getApplicationContext(), "wifi is disabled..making it enabled", Toast.LENGTH_LONG).show();
            wifi.setWifiEnabled(true);
        }

        getContext().registerReceiver(new BroadcastReceiver()
        {
            @Override
            /**
             * Method called when receive Wifi input
             *
             */

            public void onReceive(Context c, Intent intent)
            {
                boolean success = intent.getBooleanExtra(
                        WifiManager.EXTRA_RESULTS_UPDATED, false);
                if (success) toastShow("receive something", 0);
                else toastShow("unsuccessful scan", 0);

                wifidata.clear(); //clear current wifi data buffer
                List<ScanResult> results = wifi.getScanResults(); //get wifi scan result

                //only record results from Rice Owls/Rice IoT/Rice Visitor/eduroam networks
                for (ScanResult result : results) {
                    if (result.SSID.contentEquals("Rice Owls")  || result.SSID.contentEquals("Rice IoT")||result.SSID.contentEquals("Rice Visitor")||result.SSID.contentEquals("eduroam")){
//                        WifiManager.calculateSignalLevel(result.level, 100);
                        wifidata.add(new WiFiData(result.SSID, result.BSSID, WifiManager.calculateSignalLevel(result.level, 1000)));
                    }
                }
                adapter.notifyDataSetChanged();

                if (recordingWifiData)
                {

                    textX.setVisibility(View.VISIBLE);
                    textY.setVisibility(View.VISIBLE);
                    discardButton.setVisibility(View.VISIBLE);
                    saveButton.setVisibility(View.VISIBLE);
                    recordingWifiData = false;
                }

                //if is determining location
                if(determiningLocation) {
                    LocationPoint resultLocation = determineWiFiLocation(wifidata, savedWifiLocations, wifiLocationTechnique);
                    toastShow("Result location coordinate X: " + resultLocation.coordinateX + ", coordinate Y: " + resultLocation.coordinateY, 0);
                    determiningLocation = false;
                }
            }
        }, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
//        this.scanWiFiData();

        //get the list of saved Wifi locations from json file
        SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
        Set<String> nameList = sharedPref.getStringSet("namelist", new HashSet<String>());

        Gson gson = new Gson();
        this.savedWifiLocations = new ArrayList<>();
        for (String tempName : nameList) {
            String tempData = sharedPref.getString(tempName, "");

            if (tempData.length() != 0) {
                LocationPoint obj = gson.fromJson(tempData, LocationPoint.class);
                savedWifiLocations.add(obj);
            }
        }
        toastShow("Number of saved WiFi locations: " + savedWifiLocations.size(), 0);
    }

    /**
     * record Wifi location
     * @param x the x coordinate of the location
     * @param y the y coordinate of the location
     */
    private void recordLocation(double x, double y) {
        String locationName = "" + x + "," + y;

        SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
        Set<String> nameList =  sharedPref.getStringSet("namelist",new HashSet<String>());
        Gson gson = new Gson();


        /*here we assume that location must be unique. However, you might want to have duplicate data for one location
         * Please experiment with different mode to see which one works the best
         */
        if(!nameList.contains(locationName))
        {

            //create new location point with the associated wifi data
            LocationPoint tempPoint = new LocationPoint();
            tempPoint.setLocationName(locationName);
            tempPoint.setWifiData(this.wifidata);
            tempPoint.coordinateX = x;
            tempPoint.coordinateY = y;
            String json = gson.toJson(tempPoint);

            //add the new location point to the json file
            nameList.add(locationName);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString(locationName,json);
            editor.putStringSet("namelist",nameList);
            editor.commit();
            toastShow("saved location " + locationName + ", size of wifi: "+ this.wifidata.size(), 0);
        }
//        else
//        {
//            String obj_string = sharedPref.getString(locationName, "");
//            if (obj_string.length() != 0) {
//                LocationPoint tempPoint = gson.fromJson(obj_string, LocationPoint.class);
//                tempPoint.addNewMeasurement(this.wifidata);
//                toastShow("updating measurements of location "+ locationName+ ", size of wifi: "+ tempPoint.wifidata.size(), 0);
//            }
//
//        }


    }


    /**
     * Determine wifi location based on saved locations
     * @param savedWifiLocations
     * @return
     */
    public LocationPoint determineWiFiLocation(List<WiFiData> wifidata, List<LocationPoint> savedWifiLocations, String wifiLocationTechnique) {
        LocationPoint resultLocation = new LocationPoint();
        if (savedWifiLocations.size() == 0) {
            Toast.makeText(getContext(), "No WiFi location recorded", Toast.LENGTH_SHORT).show();
            return resultLocation;
        }
        //if using KNN
        if (wifiLocationTechnique.equals("KNN"))
        {
            int KNNsize = 3; //this hyperparameter is subjected to tuning
            //get the nearest k neighbors
            ArrayList<Double> KNNDistances = new ArrayList<>();
            resultLocation.setWifiData(wifidata);
            for (int i = 0; i < savedWifiLocations.size(); i++) {
                KNNDistances.add(resultLocation.calculateWiFiSignalDistance(savedWifiLocations.get(i), 1));
            }

            int[] nearestNeighborsIndex = OtherUtils.findKMinIndexArrayList(KNNDistances, KNNsize);

            LocationPoint[] nearestNeighbors = new LocationPoint[KNNsize];
            for (int i = 0; i < KNNsize; i++) {
                nearestNeighbors[i] = savedWifiLocations.get(nearestNeighborsIndex[i]);
            }
            double numeratorX = 0, numeratorY = 0, denominator = 0;
            for (LocationPoint nearestNeighbor : nearestNeighbors) {
                double t = 1 / resultLocation.calculateWiFiSignalDistance(nearestNeighbor, 1);
                numeratorX += t * nearestNeighbor.coordinateX;
                denominator += t;
                numeratorY += t * nearestNeighbor.coordinateY;
            }

            resultLocation.coordinateX = numeratorX / denominator;
            resultLocation.coordinateY = numeratorY / denominator;
        }
        return resultLocation;
    }

    public void scanWiFiData()
    {
        boolean result = false;
        result = wifi.startScan();
    }

    public void recordSensorButtonClicked() {
        recordingWifiData = true;
        recordSensorButton.setVisibility(View.INVISIBLE);
        startMeasureButton.setVisibility(View.INVISIBLE);
        toastShow("Scanning WiFi data, please wait", 0);
        scanWiFiData();


    }


    public void discardButtonClicked() {
        discardButton.setVisibility(View.INVISIBLE);
        saveButton.setVisibility(View.INVISIBLE);
        recordSensorButton.setVisibility(View.VISIBLE);
        textX.setVisibility(View.INVISIBLE);
        textY.setVisibility(View.INVISIBLE);
    }


    public void saveButtonClicked() {
        recordLocation(Double.parseDouble(textX.getText().toString()), Double.parseDouble(textY.getText().toString()));
        discardButton.setVisibility(View.INVISIBLE);
        saveButton.setVisibility(View.INVISIBLE);
        recordSensorButton.setVisibility(View.VISIBLE);
        textX.setVisibility(View.INVISIBLE);
        textY.setVisibility(View.INVISIBLE);
    }

    /**
     * Reset all saved wifi locations - Use with caution
     * */
    public void resetWiFiButtonClicked() {
        SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
        sharedPref.edit().clear().commit();
        toastShow("WiFi data cleared", 0);
    }


    /**
     * start measure and stop measure buttons are for measuring the battery consumption of constantly receiving Wifi data
     */

    public void startMeasureButtonClicked() {
        startMeasureButton.setVisibility(View.INVISIBLE);
        stopMeasureButton.setVisibility(View.VISIBLE);
        previousBatteryLevel = getBatteryLevel();
        timerHandler.post(timeRunnable);
    }


    public void stopMeasureButtonClicked() {
        stopMeasureButton.setVisibility(View.INVISIBLE);
        timerHandler.removeCallbacks(timeRunnable);
        double batteryLevel = getBatteryLevel();
        double consumption = batteryLevel - previousBatteryLevel;
        toastShow("Percentage battery consumption: " + consumption, 0);
        startMeasureButton.setVisibility(View.VISIBLE);
    }



    /**
     * Do nothing for now
     */
    public void GPSButtonClicked() {
    }

    /**
     * Show the message in phone
     * @param s string message
     * @param n time to show the message in second
     */
    public void toastShow(String s, int n) {
        Toast.makeText(getContext(), s, n).show();
    }

    /**
     * get current battery level
     * @return current battery level
     */
    double getBatteryLevel() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        batteryStatus = getContext().registerReceiver(null, ifilter);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        double batteryPct = level/(float)scale;
        return batteryPct*100;
    }
}
