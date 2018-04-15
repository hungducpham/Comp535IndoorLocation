package group1.comp535.rice.indoorlocation.data;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by daiwei.ldw on 4/11/18.
 */

public class LocationPoint {

    List<WiFiData> wifidata = new LinkedList<WiFiData>();
    HashMap<String,WiFiData> searchMap = new HashMap<>();
    String locationName;

    int x = -1;
    int y = -1;

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;

        String positions[] = this.locationName.split("_");
        if (positions.length == 3) {
            x = Integer.getInteger(positions[1]);
            y = Integer.getInteger(positions[2]);
        }
    }

    public List<WiFiData> getWiFiData() {
        return wifidata;
    }

    public void setWifidata(List<WiFiData> wifidata) {
        this.wifidata = wifidata;

        for (WiFiData temp: this.wifidata) {
            searchMap.put(temp.getBSSID(),temp);
        }

    }

    public int calculateWiFiSignalDistance(LocationPoint wifiTestPoint){

        int cost = 0;
        List<WiFiData> wifiTestData = wifiTestPoint.getWiFiData();
        if (wifiTestData== null) {
            return -1;
        }

        for (WiFiData tempData: this.wifidata) {
            WiFiData correspondingData = wifiTestPoint.searchForWiFiData(tempData);
            if (correspondingData!= null) {
                //calculate strength different and add to cost
                cost += Math.abs(tempData.getStrength1()-correspondingData.getStrength1());
            }else{
                cost += 10;
            }
        }

        return cost;
    }

    public WiFiData searchForWiFiData(WiFiData tempData){
        return this.searchMap.get(tempData.getBSSID());
    }
}
