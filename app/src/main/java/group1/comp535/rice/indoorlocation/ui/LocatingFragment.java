package group1.comp535.rice.indoorlocation.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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

public class LocatingFragment extends Fragment {

    List<WiFiData> wifidata = new LinkedList<WiFiData>();
    private WiFiDataAdapter adapter;
    WifiManager wifi;

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
                    if (result.SSID == "Rice Owls" || result.SSID == "Rice IoT"||result.SSID == "Rice Visitor" || result.SSID == "eduroam")
                        WifiManager.calculateSignalLevel(result.level, 10);
                    wifidata.add(new WiFiData(result.SSID, result.BSSID, WifiManager.calculateSignalLevel(result.level, 10)));
                }
                adapter.notifyDataSetChanged();
            }
        }, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        this.scanWiFiData();

        return v;
    }

    private void determineLocation() {
        //get data from shared preference
        SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
        Set<String> nameList = sharedPref.getStringSet("namelist",new HashSet<String>());

        List<LocationPoint> locations = new ArrayList<>();
        Gson gson = new Gson();

        for (String tempName:nameList) {
            String tempData = sharedPref.getString(tempName,"");
            if (tempData.length()!= 0){
                LocationPoint obj = gson.fromJson(tempData, LocationPoint.class);
                locations.add(obj);
            }
        }

        // calculate cost, choose the lowest one
        int cost = Integer.MAX_VALUE;
        LocationPoint cloestPoint = null;


        LocationPoint currentInfo = new LocationPoint();
        currentInfo.setWifidata(this.wifidata);

        for (LocationPoint tempPoint:locations) {
            int temp_value = tempPoint.calculateWiFiSignalDistance(currentInfo);
            if (temp_value > 0 || cost > temp_value){
                cost = temp_value;
                cloestPoint = tempPoint;
            }
        }

        if (cloestPoint != null)
        Toast.makeText(getContext(),"Result Found: "+ cloestPoint.getLocationName(),Toast.LENGTH_LONG).show();
        else
            Toast.makeText(getContext(),"No Result Found: ",Toast.LENGTH_LONG).show();
    }

    public void scanWiFiData()
    {
        wifi.startScan();
    }

}
