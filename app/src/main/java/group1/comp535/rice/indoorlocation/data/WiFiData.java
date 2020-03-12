package group1.comp535.rice.indoorlocation.data;


public class WiFiData {

    public String name;
    public String BSSID;
    public int strength;

    public WiFiData(String name, String BSSID, int strength) {
        this.setName(name);
        this.setBSSID(BSSID);
        this.setStrength1(strength);
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
        return strength;
    }

    public void setStrength1(int strength1) {
        this.strength = strength;
    }

}
