package group1.comp535.rice.indoorlocation.data;

import android.util.Log;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import group1.comp535.rice.indoorlocation.utils.OtherUtils;



public class LocationPoint {

    public List<WiFiData> wifidata = new LinkedList<WiFiData>();
    public HashMap<String,WiFiData> searchMap = new HashMap<>();
    public String locationName;


    public double coordinateX = 0;
    public double coordinateY = 0;
    public String type; //might want to include multiple type of location point
    //public int multiplicity = 1;

    public String getLocationName() {
        return locationName;
    }

    /**
     * Set location name and coordinate
     * @param locationName in the format "x, y" where x, y are doubles
     */
    public void setLocationName(String locationName) {
        this.locationName = locationName;
        this.coordinateX = Double.parseDouble(locationName.split(" ")[0]);
        this.coordinateY = Double.parseDouble(locationName.split(" ")[1]);

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

    //enable multiple wifi signal recordings of one location point
    /**
    public void addNewMeasurement(List<WiFiData> new_wifidata){
        for (int i = 0; i < new_wifidata.size(); i ++) {
            WiFiData data = new_wifidata.get(i);
            for(int j = 0; j < this.wifidata.size(); j ++) {
                WiFiData data2 = this.wifidata.get(j);
                if (data2.getBSSID().equals(data.getBSSID())) {
                    data2.strength = (data2.strength*multiplicity + data.strength)/(multiplicity + 1);
                }
            }
        }
        this.multiplicity += 1;
    }
     **/


    /**
     *
     * @param wifiTestPoint
     * @param distanceMode distanceMode = 1 is for normalized L2 distance between Wifi data; distanceMode = 2 is for normalized L1 distance
     * @return
     */
    public double calculateWiFiSignalDistance(LocationPoint wifiTestPoint, int distanceMode) {

        double cost = -1;
        if (distanceMode ==1) {
            for (WiFiData tempData : this.wifidata) {
                WiFiData correspondingData = wifiTestPoint.searchForWiFiData(tempData);
                double tempValue = 0;
                if (correspondingData != null) {
                    //calculate strength different and add to cost
                    tempValue = Math.abs(tempData.getStrength1() - correspondingData.getStrength1());

                } else {
                    //tempValue = tempData.getStrength1()*10;
                    tempValue = 0; //we only care about the WAP that is inside for both of the measurement
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


    /**
    public void correction_move(double distance, int heading) {
        double[] moveDirection = OtherUtils.mapHeadingToMatrix(heading);
        this.coordinateX = this.previousCoordinateX +  distance*moveDirection[0];
        this.coordinateY = this.previousCoordinateY + distance*moveDirection[1];
    }
     **/
}
