package group1.comp535.rice.indoorlocation.data;

import android.util.Log;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import group1.comp535.rice.indoorlocation.utils.OtherUtils;

/**
 * Created by daiwei.ldw on 4/11/18.
 */

public class LocationPoint {

    List<WiFiData> wifidata = new LinkedList<WiFiData>();
    HashMap<String,WiFiData> searchMap = new HashMap<>();
    String locationName;

    public double x = 0;
    public double y = 0;
    public double coordinateX = 0;
    public double coordinateY = 0;
    public String type;
    public double previousCoordinateX = 0;
    public double previousCoordinateY = 0;
    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;

        String positions[] = this.locationName.split("_");
        if (positions.length == 3) {
            this.type = positions[0];
            x = Integer.parseInt(positions[1]);
            y = Integer.parseInt(positions[2]);
            convertToCoordinate();
        }
    }

    public List<WiFiData> getWiFiData() {
        return wifidata;
    }

    public void setWifiData(List<WiFiData> wifidata) {
        this.wifidata = wifidata;

        for (WiFiData temp: this.wifidata) {
            searchMap.put(temp.getBSSID(),temp);
        }

    }

    /**
     *
     * @param wifiTestPoint
     * @param distanceMode distanceMode = 1 is for normalized L2 distance between Wifi data; distanceMode = 2 is for normalized L1 distance
     * @return
     */
    public double calculateWiFiSignalDistance(LocationPoint wifiTestPoint, int distanceMode) {

        double cost = -1;

        if(distanceMode != 1) {
            Log.e("Error", "wrong distance mode or wrong input");
            return cost;
        }
        if (distanceMode ==1) {
            for (WiFiData tempData : this.wifidata) {
                WiFiData correspondingData = wifiTestPoint.searchForWiFiData(tempData);
                double tempValue = 0;
                if (correspondingData != null) {
                    //calculate strength different and add to cost
                    tempValue = Math.abs(tempData.getStrength1() - correspondingData.getStrength1());
                } else {
                    tempValue = tempData.getStrength1()*10;
                }
                cost += tempValue * tempValue;
            }
            cost = Math.sqrt(cost/this.wifidata.size());
        }
        return cost;
    }

    public double calculateWiFiSignalDistance(LocationPoint wifiTestPoint,  WiFiData[] chosenWiFiData) {
        double cost = 0;
        for(WiFiData tempData: chosenWiFiData) {
            WiFiData correspondingData = wifiTestPoint.searchForWiFiData(tempData);
            double tempValue = 0;
            if (correspondingData != null) {
                //calculate strength different and add to cost
                tempValue = Math.abs(tempData.getStrength1() - correspondingData.getStrength1());
            } else {
                tempValue = tempData.getStrength1()*10;
            }
            cost += tempValue * tempValue;
        }
        cost = Math.sqrt(cost/chosenWiFiData.length);
        return cost;
    }

    public void convertToCoordinate() {
        if (type.equalsIgnoreCase("lobby")) {
            coordinateX = 5.7 *x; coordinateY = 3.5 * y;
        }
        else if (type.equalsIgnoreCase("hallway")) {
            coordinateX = 5.7*x; coordinateY = 5.7*y;
        }
        else {
            coordinateX = -1; coordinateY = -1;
        }
    }
    public WiFiData searchForWiFiData(WiFiData tempData){
        return this.searchMap.get(tempData.getBSSID());
    }
    public WiFiData searchForWiFiData(String BSSID) {
        return this.searchMap.get(BSSID);
    }

    /**
     *
     * @param distance
     * @param heading heading takes value in {0,..,7}
     */


    public void move(double distance, int heading) {
        previousCoordinateX = coordinateX;
        previousCoordinateY = coordinateY;
        double[] moveDirection = OtherUtils.mapHeadingToMatrix(heading);
        this.coordinateX = this.previousCoordinateX + distance*moveDirection[0];
        this.coordinateY = this.previousCoordinateY + distance*moveDirection[1];


    }


    public void correction_move(double distance, int heading) {
        double[] moveDirection = OtherUtils.mapHeadingToMatrix(heading);
        this.coordinateX = this.previousCoordinateX +  distance*moveDirection[0];
        this.coordinateY = this.previousCoordinateY + distance*moveDirection[1];
    }

}
