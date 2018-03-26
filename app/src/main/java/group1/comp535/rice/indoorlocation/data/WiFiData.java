package group1.comp535.rice.indoorlocation.data;

/**
 * Created by daiwei.ldw on 3/25/18.
 */

public class WiFiData {

    private String name;
    private String BSSID;
    private int strength1;
    private int strength2;

    public WiFiData(String name, String BSSID, int strength1, int strength2) {
        this.setName(name);
        this.setBSSID(BSSID);
        this.setStrength1(strength1);
        this.setStrength2(strength2);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBSSID() {
        return BSSID;
    }

    public void setBSSID(String BSSID) {
        this.BSSID = BSSID;
    }

    public int getStrength1() {
        return strength1;
    }

    public void setStrength1(int strength1) {
        this.strength1 = strength1;
    }

    public int getStrength2() {
        return strength2;
    }

    public void setStrength2(int strength2) {
        this.strength2 = strength2;
    }

}
