package group1.comp535.rice.indoorlocation.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

import java.util.LinkedList;
import java.util.List;

import group1.comp535.rice.indoorlocation.R;
import group1.comp535.rice.indoorlocation.adapter.WiFiDataAdapter;
import group1.comp535.rice.indoorlocation.data.WiFiData;

/**
 * Created by daiwei.ldw on 3/25/18.
 */

public class RecordingLocationFragment extends Fragment {

    List<WiFiData> wifidata = new LinkedList<WiFiData>();
    private WiFiDataAdapter adapter;
    WifiManager wifi;

    public static RecordingLocationFragment getInstance() {
        RecordingLocationFragment sf = new RecordingLocationFragment();
        return sf;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.record_location_fragment, null);
        ListView listView = (ListView) v.findViewById(R.id.record_data);
        Button refreshButton = (Button) v.findViewById(R.id.refresh);

        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.v("WiFi Refresh","Refresh button pressed");
                scanWiFiData();
            }
        });

        wifidata.add(new WiFiData("Eduroam","3e:4r:5t:6y:7u:8i:8i",12,13));
        wifidata.add(new WiFiData("Eduroam","3e:4r:5t:6y:7u:8i:8i",12,13));
        wifidata.add(new WiFiData("Eduroam","3e:4r:5t:6y:7u:8i:8i",12,13));
        wifidata.add(new WiFiData("Eduroam","3e:4r:5t:6y:7u:8i:8i",12,13));
        wifidata.add(new WiFiData("Eduroam","3e:4r:5t:6y:7u:8i:8i",12,13));
        wifidata.add(new WiFiData("Eduroam","3e:4r:5t:6y:7u:8i:8i",12,13));
        wifidata.add(new WiFiData("Eduroam","3e:4r:5t:6y:7u:8i:8i",12,13));
        wifidata.add(new WiFiData("Eduroam","3e:4r:5t:6y:7u:8i:8i",12,13));

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
                for (ScanResult result : results) {
                    wifidata.add(new WiFiData(result.SSID, result.BSSID, result.level, result.level));
                }
                adapter.notifyDataSetChanged();
            }
        }, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
//        this.scanWiFiData();

        return v;
    }

    public void scanWiFiData()
    {

        Log.v("WiFi Refresh","Refreshing");
        wifi.startScan();

    }

}
