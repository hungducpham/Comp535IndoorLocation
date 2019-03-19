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
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import android.os.BatteryManager;

import com.google.gson.Gson;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import group1.comp535.rice.indoorlocation.R;
import group1.comp535.rice.indoorlocation.adapter.WiFiDataAdapter;
import group1.comp535.rice.indoorlocation.data.LocationPoint;
import group1.comp535.rice.indoorlocation.data.WiFiData;
import group1.comp535.rice.indoorlocation.utils.ClassificationNeuralNetwork;
import group1.comp535.rice.indoorlocation.utils.LinearSVC;
import group1.comp535.rice.indoorlocation.utils.NeuralNetwork;
import group1.comp535.rice.indoorlocation.utils.OtherUtils;
import group1.comp535.rice.indoorlocation.utils.ButterworthFilter;
import group1.comp535.rice.indoorlocation.utils.StepDetectionUtil;


public class CombinedLocatingFragment extends Fragment implements SensorEventListener {
    private int KNNsize = 1;


    /**mode of the program
     * mode = 1: program using WiFi only to determine location
     * mode = 2: using both WiFi and sensor data to determine location
     * mode = 3: only recording sensor data for training
     */
    private int currentHeading = 0;
    private ArrayList<Double> pathLengths = new ArrayList<>();
    private ArrayList<Integer> pathHeadings  = new ArrayList<>();
    private ArrayList<double[]> positionBeforeTurn = new ArrayList<>();
    private final double step_length_variance = 0.03;
    private final double turning_variance = 0.05;
    private double current_heading_variance = 0;
    private double current_path_variance = 0;
    private ArrayList<double[][]> position_covariance_matrices_before_turn = new ArrayList<>();
    private double[][] current_position_covariance_matrix = new double[2][2];


    private int mode = 3;
    private String[] mode_name = {"using only WiFi","using both WiFi and sensor data", "only using sensor data" };
    private final int WIFIMODE_1NN = 1;
    private boolean CUMULATIVE_SENSOR_VALUE = true;
    //private boolean stepModel = true;
    private int frame_time = 20;
    private double acc_filter_threshold = 0.5;
    private double gyro_filter_threshold = 0.5;
    private double turning_buffer_sum_threshold = 5;
    private int recordingSensorMode = 1;  // sensor mode: 0 = accelerator (distance measuring); 1 = gyroscope (turning measuring)
    private int nn_mode = 1; //neural network mode: 1 = distance + heading; 2 = X,Y; 3 = RNN
    private int distance_mode = 1;
    private int wifi_mode = WIFIMODE_1NN; //Wifi location detection mode: 1= KNN with 1 nearest neigbor; 2 = KNN with 3 nearest neighbor; 3 = KNN and square-restriction
    //based on them and then picking the most common nearest neighbors;

    List<WiFiData> wifidata = new LinkedList<WiFiData>();
    private WiFiDataAdapter adapter;
    WifiManager wifi;

    private SensorManager mSensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;


    private double location_x = 0, location_y = 0;

    private LocationPoint currentLocation = null;
    private double spaceBetweenPoints_x = 5.6;
    private double spaceBetweenPoints_y = 3.2;

    private NeuralNetwork distanceNN;
    private ClassificationNeuralNetwork turningNN;
    private LinearSVC turning_model_SVC;
    private boolean possible_turn = false;


    private boolean recording;
    private ArrayList<Double> accelerationSensorDataRecorded = new ArrayList<>();
    private ArrayList<Double> accelerationXRecorded = new ArrayList<>(); //for acceleration data of X
    private ArrayList<Double> accelerationYRecorded  =new ArrayList<>(); //for acceleration data of Y
    private ArrayList<Double> accelerationZRecorded=new ArrayList<>(); //for acceleration data of Z
    private ArrayList<Double> gyroscopeSensorDataRecorded=new ArrayList<>();
    private ArrayList<Double> gyroscopeXRecorded=new ArrayList<>();
    private ArrayList<Double> gyroscopeYRecorded=new ArrayList<>();
    private ArrayList<Double> gyroscopeZRecorded=new ArrayList<>();
    private ArrayList<Double> turningDataRecorded = new ArrayList<>();
    private ArrayList<Double> filteredAccDataRecorded = new ArrayList<>();

    private Button locateButton;
    private Button recordButton;
    private Button finishRecordingButton;
    private Button discardButton;
    private Button saveButton;
    private Button resetButton;
    private Button startButton;
    private Button stopButton;
    private EditText textX;
    private EditText textY;
    private long lastRecordedTimeInMillis;
    private long recordButtonClickTime;
    private List<LocationPoint> savedWifiLocations;
    private LinkedList<Double> turning_data_buffer = new LinkedList<>();
    private double sum_turning_data_buffer = 0;

    private double lastAccX = 0;
    private double lastAccY=0;
    private double lastAccZ=0;
    private double lastRotateX=0;
    private double lastRotateY=0;
    private double lastRotateZ=0;
    private long lastTurningCheckTime = 0;



    private boolean updatingLocation = false;
    private Intent batteryStatus ;



    public static CombinedLocatingFragment getInstance() {
        CombinedLocatingFragment sf = new CombinedLocatingFragment();
        return sf;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context context = getContext();
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                (context.checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED)
                ||(context.checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED)
                || (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                || (context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                || (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)){
            requestPermissions(new String[]{Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.CHANGE_WIFI_STATE,Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    1);

            Toast.makeText(context,"Requesting Permission", Toast.LENGTH_LONG).show();
            Log.v("Permission","Permission Requesting");
            //After this point you wait for callback in onRequestPermissionsResult(int, String[], int[]) overriden method

        }else{
            Toast.makeText(context,"No Need for permission", Toast.LENGTH_LONG).show();
            Log.v("Permission","No Permission Issue");
        }


    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Get an instance to the accelerometer,
        this.mSensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);

        this.accelerometer = this.mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        this.gyroscope = this.mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI, 250);
        mSensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_UI, 250);


        this.lastRecordedTimeInMillis = System.currentTimeMillis();
        this.distanceNN = new NeuralNetwork();
        this.distanceNN.importFromFile(this.nn_mode);
        /*
        this.turningNN = new ClassificationNeuralNetwork();
        turningNN.importFromFile(4);       //suppose that mode # 4 is for turning neural network
        */
        this.turning_model_SVC = new LinearSVC();
        this.turning_model_SVC.reconstruct_from_file();
        this.pathHeadings.add(currentHeading);
        double[] t = new double[2]; t[0] = 0.0; t[1] = 0.0;
        this.positionBeforeTurn.add(t);
        double[][] t2 = new double[2][2];
        this.position_covariance_matrices_before_turn.add(t2);

        View v = inflater.inflate(R.layout.locating_fragment, null);
        ListView listView = (ListView) v.findViewById(R.id.recorded_data);
        locateButton = v.findViewById(R.id.locating);
        recordButton = v.findViewById(R.id.record);
        saveButton = v.findViewById(R.id.save);
        discardButton = v.findViewById(R.id.discard);
        resetButton = v.findViewById(R.id.reset);
        finishRecordingButton = v.findViewById(R.id.finish);
        startButton = v.findViewById(R.id.start);
        stopButton = v.findViewById(R.id.stop);
        textX = v.findViewById(R.id.txtX);
        textY = v.findViewById(R.id.txtY);


        locateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                determineLocation();
            }
        });
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recordButtonClicked();
            }
        });
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveButtonClicked();
            }
        });
        discardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                discardButtonClicked();
            }
        });
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetButtonClicked();
            }
        });
        finishRecordingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finishRecordingButtonClicked();
            }
        });
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startMeasuring();
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopMeasuring();
            }
        });
        adapter = new WiFiDataAdapter(getActivity(), wifidata);
        listView.setAdapter(adapter);

        wifi = (WifiManager) getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (!wifi.isWifiEnabled()) {
            Toast.makeText(getContext().getApplicationContext(), "wifi is disabled..making it enabled", Toast.LENGTH_LONG).show();
            wifi.setWifiEnabled(true);
        }

        getContext().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                wifidata.clear();
                List<ScanResult> results = wifi.getScanResults();
                Log.v("Wifi Data Size", results.size() + "");
                for (ScanResult result : results) {
                    if (result.SSID.contentEquals("Rice Owls") || result.SSID.contentEquals("Rice IoT") || result.SSID.contentEquals("Rice Visitor") || result.SSID.contentEquals("eduroam")) {
//                        WifiManager.calculateSignalLevel(result.level, 100);
                        wifidata.add(new WiFiData(result.SSID, result.BSSID, WifiManager.calculateSignalLevel(result.level, 1000)));
                    }
                }
                adapter.notifyDataSetChanged();
                //determineWiFiLocation(KNNsize);
            }
        }, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        //this.scanWiFiData();
        SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
        Set<String> nameList = sharedPref.getStringSet("namelist", new HashSet<String>());

        this.savedWifiLocations = new ArrayList<>();
        Gson gson = new Gson();

        for (String tempName : nameList) {
            String tempData = sharedPref.getString(tempName, "");

            if (tempData.length() != 0) {
                LocationPoint obj = gson.fromJson(tempData, LocationPoint.class);
                savedWifiLocations.add(obj);
            }
        }
        Toast.makeText(getContext(), "Current mode of the program: " +
                mode_name[mode-1], Toast.LENGTH_LONG).show();
        return v;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        long currentTimeMillis = processSensorData(sensorEvent);
        if (mode == 3 && recordingSensorMode == 1 && recording && stopButton.getVisibility() != View.VISIBLE) { //if currently recording gyroscope data
            if(currentTimeMillis - recordButtonClickTime >= 2000)
                finishRecordingButtonClicked();
        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private void recordButtonClicked() {

        recording = true;
        toastShow("recording sensor data", 0);
        //clear gyroscope and accelerator data
        gyroscopeSensorDataRecorded= new ArrayList<>();
        gyroscopeXRecorded = new ArrayList<>();
        gyroscopeYRecorded = new ArrayList<>();
        gyroscopeZRecorded = new ArrayList<>();

        accelerationSensorDataRecorded = new ArrayList<>();
        accelerationXRecorded = new ArrayList<>();
        accelerationYRecorded = new ArrayList<>();
        accelerationZRecorded = new ArrayList<>();
        //start the timer for frame
        lastRecordedTimeInMillis = System.currentTimeMillis();
        recordButtonClickTime = lastRecordedTimeInMillis;
        finishRecordingButton.setVisibility(View.VISIBLE);
        recordButton.setVisibility(View.INVISIBLE);


    }

    private void saveButtonClicked() {


        double X = Double.parseDouble(textX.getText().toString());
        double Y = Double.parseDouble(textY.getText().toString());
        double[] accData = OtherUtils.convertArrayListToArray(accelerationSensorDataRecorded);
        double[] gyroData = OtherUtils.convertArrayListToArray(gyroscopeSensorDataRecorded);

            double[] accXData = OtherUtils.convertArrayListToArray(accelerationXRecorded);
            double[] accYData = OtherUtils.convertArrayListToArray(accelerationYRecorded);
            double[] accZData = OtherUtils.convertArrayListToArray(accelerationZRecorded);
            double[] gyroXData = OtherUtils.convertArrayListToArray(gyroscopeXRecorded);
            double[] gyroYData = OtherUtils.convertArrayListToArray(gyroscopeYRecorded);
            double[] gyroZData = OtherUtils.convertArrayListToArray(gyroscopeZRecorded);
            //double[] filteredAccelerationData = ButterworthFilter.filter(accData);
            //double[] filteredGyroscopeData = ButterworthFilter.filter(gyroData);

            this.writeSensorData(OtherUtils.convertToString(accData) + "\n" +
                    OtherUtils.convertToString(gyroData), X, Y, "cumulative_sensor_data.txt");
            this.writeSensorData(OtherUtils.convertToString(accXData) + "\n" +
                    OtherUtils.convertToString(accYData) + "\n" + OtherUtils.convertToString(accZData), X, Y, "acceleration_data.txt");
            this.writeSensorData(OtherUtils.convertToString(gyroXData) + "\n" +
                    OtherUtils.convertToString(gyroYData) + "\n" + OtherUtils.convertToString(gyroZData), X, Y, "gyroscope_data.txt");
            //Toast.makeText(getContext(), "Recorded acceleration and gyroscope data, size of data is accelerator: " +
            //        accelerationSensorDataRecorded.size() + " , gyroscope: " + gyroscopeSensorDataRecorded.size(), Toast.LENGTH_SHORT).show();


            //detect_step
            if(recordingSensorMode == 0) //recording accelerator mode
            {
                double[] filteredAccelerationData = ButterworthFilter.filter(accData);
                int[] steps_butterworth = StepDetectionUtil.detectStep(filteredAccelerationData);
                //int[] steps_raw = StepDetectionUtil.detectStepRaw(accData);
                //Toast.makeText(getContext(), "Number of step detected from Butterworth: " + (steps_butterworth.length - 1), Toast.LENGTH_LONG).show();
                //Toast.makeText(getContext(), "Number of step detected using raw data: " + (steps_raw.length / 2), Toast.LENGTH_LONG).show();

                if (steps_butterworth.length > 1) {
                    for (int i = 1; i < steps_butterworth.length - 1; i++) {
                        String str = "";
                        for (int j = steps_butterworth[i]; j < steps_butterworth[i + 1]; j++) {
                            str = str + filteredAccelerationData[j] + " ";
                        }
                        this.writeSensorData(str, X, 0, "step_data.txt");
                    }
                }
                Toast.makeText(getContext(), "Recorded acceleration and gyroscope data, size of data is accelerator: " +
                       accelerationSensorDataRecorded.size() + " , gyroscope: " + gyroscopeSensorDataRecorded.size(), Toast.LENGTH_SHORT).show();
            }
            else if(recordingSensorMode ==1) //recording turning mode
            {
                this.writeSensorData(OtherUtils.convertToString(gyroZData),X,0, "turning_data.txt" );
                Toast.makeText(getContext(), "Recorded turn data, type: " + X + ", number of data point: " + gyroZData.length , Toast.LENGTH_SHORT).show();
            }






        saveButton.setVisibility(View.INVISIBLE);
        textX.setVisibility(View.INVISIBLE);
        textY.setVisibility(View.INVISIBLE);
        discardButton.setVisibility(View.INVISIBLE);
        recordButton.setVisibility(View.VISIBLE);
        turningDataRecorded = new ArrayList<>();

        resetLocalVariables();

    }

    private void discardButtonClicked() {

        discardButton.setVisibility(View.INVISIBLE);
        recordButton.setVisibility(View.VISIBLE);
        textX.setVisibility(View.INVISIBLE);
        textY.setVisibility(View.INVISIBLE);
        saveButton.setVisibility(View.INVISIBLE);
        toastShow("discarded newest data", 0);
        resetLocalVariables();


    }






    /**
     * write sensor data into file name sensor_data.txt
     *
     * @param data the sensor data - most probably acceleration measurement
     * @param X relative x coordinate of the resulting place
     * @param Y relative y coordinate of the resulting place
     */

    private void writeSensorData(String data, double X, double Y, String file_name) {
        try {
            File file = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS), file_name);
            if (!file.exists()) Log.v("debug", "File doesn't exist");
            FileOutputStream fOut = new FileOutputStream(file, true);
            OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);
            BufferedWriter myBufferedWriter = new BufferedWriter(myOutWriter);
            myBufferedWriter.append(data);
            myBufferedWriter.append("\n");
            myBufferedWriter.append("" + X + " " + Y);
            myBufferedWriter.append("\n");
            myBufferedWriter.close();
            fOut.close();
        } catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }

    /**
     * determine the k-nearest WiFi neighbors and determine the location based on that information
     * @param k number of nearest neighbors
     * @return the LocationPoint estimated.
     */
    private LocationPoint determineWiFiLocation(int k) {


        LocationPoint resultLocation = new LocationPoint();
        if(this.savedWifiLocations.size() == 0 ) {
            Toast.makeText(getContext(), "No WiFi location recorded", Toast.LENGTH_SHORT).show();
            return resultLocation;
        }
        //get the nearest k neighbors
        ArrayList<Double> KNNDistances = new ArrayList<>(this.savedWifiLocations.size());
        resultLocation.setWifiData(this.wifidata);
        /**
         * if wifi_mode = 1 or 2.
         * They need the same k-nearest neighbors calculation scheme.
         */

        if(this.wifi_mode == 1 || this.wifi_mode == 2) {
            for (int i = 0; i < savedWifiLocations.size(); i++) {
                KNNDistances.set(i, resultLocation.calculateWiFiSignalDistance(savedWifiLocations.get(i), distance_mode));
            }

            int[] nearestNeighborsIndex = OtherUtils.findKMinIndexArrayList(KNNDistances, KNNsize);

            LocationPoint[] nearestNeighbors = new LocationPoint[KNNsize];
            for (int i = 0; i < KNNsize; i++) {
                nearestNeighbors[i] = this.savedWifiLocations.get(nearestNeighborsIndex[i]);
            }


            /**
             * wifi_mode = 1
             * straight-forward calculation.
             * Use the assumption that difference in real distance is proportional to difference in WiFi distance.
             */
            if (this.wifi_mode == 1) //use KNN with various k
            {
                double numeX = 0;
                double numeY = 0;
                double deno = 0;
                for (int i = 0; i < k; i++) {
                    double d = KNNDistances.get(nearestNeighborsIndex[i]);
                    numeX += d * nearestNeighbors[i].coordinateX;
                    numeY += d * nearestNeighbors[i].coordinateY;
                    deno += d;
                }
                resultLocation.x = numeX / deno;
                resultLocation.y = numeY / deno;
                resultLocation.type = nearestNeighbors[0].type;
            }
            /**wifi_mode = 2
             * use k neighbors to approximate a part of the circle as possible location
             *  This part assumes you chose k =3. k != 3 will print error and return
             */
            else if (this.wifi_mode == 2) //
            {
                resultLocation.type = nearestNeighbors[0].type;
                if (this.KNNsize != 3) {
                    Log.e("Error", "wrong KNN k");
                    return resultLocation;
                }

                LocationPoint closestPoint = nearestNeighbors[0];
                double x1 = nearestNeighbors[1].x - closestPoint.x;
                double y1 = nearestNeighbors[1].y - closestPoint.y;
                double x2 = nearestNeighbors[2].x - closestPoint.x;
                double y2 = nearestNeighbors[2].y - closestPoint.y;
                //use the fixed points methods for
                if (x1 * y1 != 0) //case 1: when the 2nd closest point is not on any axis, choose the one at the center of the rectangle
                {
                    resultLocation.x = closestPoint.x + 0.5 * x1 / Math.abs(x1);
                    resultLocation.y = closestPoint.y + 0.5 * y1 / Math.abs(y1);

                } else if (x1 * y1 == 0)// divide the square created by closest point into 8 triangles.
                {
                    if (Math.abs(x1) == 1) {
                        if (x2 + x1 == 0) //if 3rd point is on the opposite side of the 2nd point, there is some noise in the mesurement
                        {
                            resultLocation = closestPoint;
                        } else //pick the middle point of the triangle that is determined by 3 closest points
                        {
                            resultLocation.x = closestPoint.x + 1 / 3 * x1;
                            resultLocation.y = closestPoint.y + 1 / 6 * y2;
                        }
                    } else if (Math.abs(y1) == 1) {
                        if (y2 + y1 == 0) //if 3rd point is on the opposite side of the 2nd point, there is some noise in the mesurement
                        {
                            resultLocation = closestPoint;
                        } else //pick the middle point of the triangle that is determined by 3 closest points
                        {
                            resultLocation.x = closestPoint.x + 1 / 3 * y1;
                            resultLocation.y = closestPoint.y + 1 / 6 * x2;
                        }
                    } else {
                        Log.e("Error", "There is something wrong in the rectangle logic 1");
                    }
                } else {
                    Log.e("Error", "There is something wrong in the rectangle logic 2");
                }

            }
        }

        /**
         * wifi mode = 3
         * we pick several subsets of the WiFi AP, and calculate distance based on them
         * the idea is that usually distances to 3 WAPs are enough for location detection, more WAPs only add unnecessary calculation and outlier/noise
         * to the result.
         */
        else if (this.wifi_mode == 3) {
            //randomly choose subsets of size subset_size of WiFi APs
            int subset_size = 5; //this parameter can be varied
            int subset_num = 5; //this parameter can be varied
            int[][] nearestNeighborsIndices = new int[subset_num][KNNsize];
            for(int i = 0; i < subset_num; i ++) {
                int[] chosenSubset = OtherUtils.generateRandomNumbersInRange(subset_size, this.wifidata.size());
                WiFiData[] chosenWiFiData = new WiFiData[subset_size];
                for (int j = 0; j < subset_size; j ++) {
                    chosenWiFiData[j] = this.wifidata.get(chosenSubset[j]);
                }

                for (int j = 0; j < savedWifiLocations.size(); j++) {
                    KNNDistances.set(j, resultLocation.calculateWiFiSignalDistance(savedWifiLocations.get(i), chosenWiFiData));
                }
                int[] nearestNeighborsIndex = OtherUtils.findKMinIndexArrayList(KNNDistances, KNNsize);
                nearestNeighborsIndices[i]  = nearestNeighborsIndex;

            }
            //find the most common nearest neighbors based on the nearest neighbor indices
            int[] nearestNeighborsIndex = new int[KNNsize];
            for(int i = 0; i < KNNsize; i ++) {
                int[] temp_array =  new int[subset_num];
                for (int j = 0; j < subset_num; j ++) {
                    temp_array[j] = nearestNeighborsIndices[j][i];
                }
                int bestIndex = OtherUtils.findMostCommonValue(temp_array);
                nearestNeighborsIndex[i] = bestIndex; //this doesn't handle the case where one value gets duplicated in 2 places in the nearestNeighborsIndex
                //array. Will have to change it later in the future.
            }
            //now continue exactly the same as in wifi_mode 2   
            LocationPoint[] nearestNeighbors = new LocationPoint[KNNsize];
            for (int i = 0; i < KNNsize; i++) {
                nearestNeighbors[i] = this.savedWifiLocations.get(nearestNeighborsIndex[i]);
            }
            resultLocation.type = nearestNeighbors[0].type;
            if (this.KNNsize != 3) {
                Log.e("Error", "wrong KNN k");
                return resultLocation;
            }

            LocationPoint closestPoint = nearestNeighbors[0];
            double x1 = nearestNeighbors[1].x - closestPoint.x;
            double y1 = nearestNeighbors[1].y - closestPoint.y;
            double x2 = nearestNeighbors[2].x - closestPoint.x;
            double y2 = nearestNeighbors[2].y - closestPoint.y;
            //use the fixed points methods for
            if (x1 * y1 != 0) //case 1: when the 2nd closest point is not on any axis, choose the one at the center of the rectangle
            {
                resultLocation.x = closestPoint.x + 0.5 * x1 / Math.abs(x1);
                resultLocation.y = closestPoint.y + 0.5 * y1 / Math.abs(y1);

            } else if (x1 * y1 == 0)// divide the square created by closest point into 8 triangles.
            {
                if (Math.abs(x1) == 1) {
                    if (x2 + x1 == 0) //if 3rd point is on the opposite side of the 2nd point, there is some noise in the mesurement
                    {
                        resultLocation = closestPoint;
                    } else //pick the middle point of the triangle that is determined by 3 closest points
                    {
                        resultLocation.x = closestPoint.x + 1 / 3 * x1;
                        resultLocation.y = closestPoint.y + 1 / 6 * y2;
                    }
                } else if (Math.abs(y1) == 1) {
                    if (y2 + y1 == 0) //if 3rd point is on the opposite side of the 2nd point, there is some noise in the mesurement
                    {
                        resultLocation = closestPoint;
                    } else //pick the middle point of the triangle that is determined by 3 closest points
                    {
                        resultLocation.x = closestPoint.x + 1 / 3 * y1;
                        resultLocation.y = closestPoint.y + 1 / 6 * x2;
                    }
                } else {
                    Log.e("Error", "There is something wrong in the rectangle logic 3.1");
                }
            } else {
                Log.e("Error", "There is something wrong in the rectangle logic 3.2");
            }




        }
        resultLocation.convertToCoordinate();

        return resultLocation;

    }

    public void determineLocation(){
        /*
         determine location using WiFi only
         */
        if(mode == 1)
        {
            LocationPoint resultLocation  = determineWiFiLocation(KNNsize);

            Toast.makeText(getContext(), "location determined, x: "+ resultLocation.x + ", y: " + resultLocation.y, Toast.LENGTH_LONG).show();

        }
        /*
        determine location using a combination of IMUs and WiFi
         */
        if(mode == 2) {
            double coordX = currentLocation.coordinateX;
            double coordY = currentLocation.coordinateY;
            toastShow("Location determined, x: " + coordX + " y: " + coordY, 0);
        }
    }
    double distanceTravelled = 0;

    /**
     * Method when you click the start button
     * Start the timer and do things according to the current mode of the program
     */
    public void startMeasuring() {
        startButton.setVisibility(View.INVISIBLE);
        recordButton.setVisibility(View.INVISIBLE);
        resetButton.setVisibility(View.INVISIBLE);
        locateButton.setVisibility(View.INVISIBLE);
        stopButton.setVisibility(View.VISIBLE);
        long currentTimeMillis = System.currentTimeMillis();
        lastTurningCheckTime = currentTimeMillis;

        //timerHandler.postDelayed(timeRunnable, 0);
        recording = true;
        currentLocation = new LocationPoint(); //current location when start measuring start at (0-0)
        currentHeading = 0; //current heading = heading toward the positive x axis at the beginning of the measurement
        updatingLocation = true;
        double current_battery = getBatteryLevel();
        toastShow("Current battery level: " + current_battery, 0);




    }

    public void stopMeasuring() {
        stopButton.setVisibility(View.INVISIBLE);
        startButton.setVisibility(View.VISIBLE);
        recordButton.setVisibility(View.VISIBLE);
        resetButton.setVisibility(View.VISIBLE);
        locateButton.setVisibility(View.VISIBLE);
        recording = false;

        toastShow("Detected " + stepNum + "steps. Distance travelled in the last measurement: " + distanceTravelled, 0);
        toastShow("Current location is x = "  + currentLocation.coordinateX + ", y = " + currentLocation.coordinateY, 0);
        //double[] accData = OtherUtils.convertArrayListToArray(accelerationSensorDataRecorded);
        //this.writeSensorData(OtherUtils.convertToString(accData), 0,0,
        //         "accelerometer_sensor_data.txt");
        resetLocalVariables();

        //timerHandler.removeCallbacks(timeRunnable);

    }

    private void resetButtonClicked() {
        File file1 = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "cumulative_sensor_data.txt");
        File file2 = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "acceleration_data.txt");
        File file3 = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "gyroscope_data.txt");
        File file4 = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "step_data.txt");
        File file5 = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "accelerometer_sensor_data.txt");
        File file6 = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "turning_data.txt");
        file1.delete();
        file2.delete();
        file3.delete();
        file4.delete();
        file5.delete();
        file6.delete();
        Toast.makeText(getContext(), "all sensor data reset", Toast.LENGTH_SHORT).show();
        //reset the local variables
        resetLocalVariables();
    }

    private void finishRecordingButtonClicked() {

        recording = false;
        finishRecordingButton.setVisibility(View.INVISIBLE);
        saveButton.setVisibility(View.VISIBLE);
        textX.setVisibility(View.VISIBLE);
        textY.setVisibility(View.VISIBLE);
        discardButton.setVisibility(View.VISIBLE);
        if(recordingSensorMode  == 0) {
            double[] accData = OtherUtils.convertArrayListToArray(accelerationSensorDataRecorded);
            double[] filteredAccelerationData = ButterworthFilter.filter(accData);
            int[] steps_butterworth = StepDetectionUtil.detectStep(filteredAccelerationData);
            //int[] steps_raw = StepDetectionUtil.detectStepRaw(accData);
            Toast.makeText(getContext(), "Number of step detected from Butterworth: " + (steps_butterworth.length - 1), Toast.LENGTH_SHORT).show();

            //Toast.makeText(getContext(), "Number of step detected using raw data: " + (steps_raw.length/2 ), Toast.LENGTH_LONG).show();
            double[] filteredAccelerationData2 = OtherUtils.convertArrayListToArray(filteredAccDataRecorded);
            int[] steps_butterworth2 = StepDetectionUtil.detectStep(filteredAccelerationData2);
            Toast.makeText(getContext(), "Number of step detected from Butterworth2: " + (steps_butterworth2.length - 1), Toast.LENGTH_SHORT).show();
            stepNum = 0;
        }
        else if(recordingSensorMode == 1) {

        }

    }


    /** process sensor data
     * update and concatenate values of the global sensor data holders
     * apply Butterworth filter to data on the fly

     */
    private long processSensorData(SensorEvent sensorEvent) {
        long currentTimeMillis = System.currentTimeMillis();
        if (recording) {
            if (sensorEvent.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
                double accX = sensorEvent.values[0];
                double accY = sensorEvent.values[1];
                double accZ = sensorEvent.values[2];
                if(acc_filter_threshold > 0) {
                    if(accX < acc_filter_threshold) accX = 0;
                    if(accY < acc_filter_threshold) accY = 0;
                    if(accZ < acc_filter_threshold) accZ = 0;
                }
                if (CUMULATIVE_SENSOR_VALUE){
                    //aggregate the sensor data over a 20ms period
                    if (currentTimeMillis - lastRecordedTimeInMillis < frame_time) {
                        lastAccX += accX;
                        lastAccY += accY;
                        lastAccZ += accZ;
                    }

                }
                else {
                    lastAccX = accX;
                    lastAccY = accY;
                    lastAccZ = accZ;
                }
            }
            if (sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                double rotateX = sensorEvent.values[0];
                double rotateY = sensorEvent.values[1];
                double rotateZ = sensorEvent.values[2];
                if(gyro_filter_threshold > 0) {
                    if(Math.abs(rotateX) < gyro_filter_threshold) rotateX = 0;
                    if(Math.abs(rotateY) < gyro_filter_threshold) rotateY = 0;
                    if(Math.abs(rotateZ) < gyro_filter_threshold) rotateZ = 0;
                }
                if (CUMULATIVE_SENSOR_VALUE){
                    /**
                     if (currentTimeMillis - lastRecordedTimeInMillis < frame_time) {
                     lastRotateX += rotateX;
                     lastRotateY += rotateY;
                     lastRotateZ += rotateZ;
                     }
                     */
                    lastRotateX = rotateX;
                    lastRotateY = rotateY;
                    lastRotateZ = rotateZ;
                }
                else{
                    lastRotateX = rotateX;
                    lastRotateY = rotateY;
                    lastRotateZ = rotateZ;
                }


            }
            if (CUMULATIVE_SENSOR_VALUE) //if cumulative sensor recording mode is on
            {
                if (currentTimeMillis - lastRecordedTimeInMillis>= frame_time) {
                    //move frame to the next frame
                    lastRecordedTimeInMillis = currentTimeMillis;
                    //calculate the norm of the acceleration vector
                    updateSensorData();
                    //process accelerometer data
                    applyButterworthFilterAcc();
                    if(filteredAccDataRecorded.size() >= 2) {
                        detectStepOnTheFly();
                    }

                    //process gyroscope data
                    /**
                    if( currentTimeMillis - lastTurningCheckTime >= 1500) //check turn every 1.5 second
                    {
                        double[] lastTurningData = OtherUtils.convertArrayListToArray(turningDataRecorded);
                        int lastTurn = turningNN.predict(lastTurningData);

                        currentHeading = (currentHeading + lastTurn)%8;
                        if(lastTurn > 0) toastShow("turning detected, type: " + lastTurn + ", current heading: " + currentHeading, 0);
                        //refresh the turning data
                        turningDataRecorded =  new ArrayList<>();
                        lastTurningCheckTime = currentTimeMillis;
                    }
                    turningDataRecorded.add(lastRotateZ);
                     */
                    if(!possible_turn && sum_turning_data_buffer >= turning_buffer_sum_threshold) {
                        possible_turn = true;
                        lastTurningCheckTime = currentTimeMillis;
                        turningDataRecorded.clear();
                        for(int i = 0; i < turning_data_buffer.size(); i ++) {
                            double t = turning_data_buffer.poll();
                            turningDataRecorded.add(t);
                        }
                        //reset the turning buffer so that it does not mess with the logic of next frames
                        sum_turning_data_buffer = 0;
                        turning_data_buffer.clear();
                        toastShow("possible turn detected", 0);
                    }
                    else if(possible_turn) {
                        turningDataRecorded.add(lastRotateZ);
                    }
                    if( possible_turn && currentTimeMillis - lastTurningCheckTime >= 2000) //we assume that the turn can only happen in 1.5 second maximum
                    {
                        double[] lastTurningData = OtherUtils.convertArrayListToArray(turningDataRecorded);
                        int lastTurn = estimateTurning(lastTurningData);
                        perform_turn(lastTurn);
                        toastShow("turning detected, type: " + lastTurn + ", current heading: " + currentHeading, 0);
                        //refresh the turning data

                        possible_turn = false;
                    }

                        //reset the aggregated accelerations
                    lastAccX = 0;
                    lastAccY = 0;
                    lastAccZ = 0;
                    lastRotateZ = 0;
                    lastRotateY = 0;
                    lastRotateX = 0;
                }
            }
            else //if cumulative sensor recording mode is off
            {
                updateSensorData();
                applyButterworthFilterAcc();
                if(filteredAccDataRecorded.size() >= 2)
                    detectStepOnTheFly();
            }


        }
        return currentTimeMillis;
    }


    /**
     * estimate the turning category from the last 1.5 second of gyroscope data
     * @return
     */
    private int estimateTurning(double[] turningData) {

        int result = turning_model_SVC.predict(get_features_array(turningData));
        return result;
    }
    private double[] get_features_array(double[] turningData) {
        double[] result = new double[6];
        result[0] = OtherUtils.sum(turningData);
        result[1] = OtherUtils.maximum(turningData);
        result[2] = OtherUtils.minimum(turningData);
        result[3] = result[0]/turningData.length;
        result[4] = OtherUtils.sum(OtherUtils.abs(turningData));
        result[5] = OtherUtils.var(turningData);
        return result;
    }


    /**
     * save the sensor data into buffers using last recorded measurements
     */
    private void updateSensorData() {
        accelerationXRecorded.add(lastAccX);
        accelerationYRecorded.add(lastAccY);
        accelerationZRecorded.add(lastAccZ);
        gyroscopeXRecorded.add(lastRotateX);
        gyroscopeYRecorded.add(lastRotateY);
        gyroscopeZRecorded.add(lastRotateZ);
        accelerationSensorDataRecorded.add(Math.sqrt(lastAccX * lastAccX + lastAccY * lastAccY + lastAccZ * lastAccZ));
        gyroscopeSensorDataRecorded.add(Math.sqrt(lastRotateX * lastRotateX + lastRotateY * lastRotateY + lastRotateZ * lastRotateZ));



        if(!possible_turn) {
            turning_data_buffer.add(lastRotateZ);
            sum_turning_data_buffer += Math.abs(lastRotateZ);
            if (turning_data_buffer.size() > 5) {
                double t = turning_data_buffer.poll();
                sum_turning_data_buffer -= Math.abs(t);
            }
        }
    }

    //scalars needed to apply Butterworth filter on the fly
    int NPOLES = 10;
    double GAIN = 2.015579946e+04;
    double output = 0;
    double[] xv1 = new double[NPOLES +1]; double[] yv1 = new double[NPOLES + 1];
    double[] xv2 = new double[NPOLES +1]; double[] yv2 = new double[NPOLES + 1];

    //scalars needed to detect step
    double current_slope = 0;
    double current_high = 0;
    double previous_low = 0;
    double current_low = 0;
    int current_low_index = 0;
    int previous_low_index = 0;
    int current_high_index = 0;
    boolean newStep = false;
    double difference_threshold = 1.25;
    double low_threshold = 1.5;
    double high_slope_threshold = 0.5;
    int step_length_threshold = 5;
    ArrayList<Integer> steps = new ArrayList();
    ArrayList<Double> currentStep = new ArrayList<>(); //store the acc data of the current (most recent) step
    int stepNum = 0;

    /**
     * apply Butterworth filter on the fly to accelerometer data
     *
     */
    private void applyButterworthFilterAcc() {
        xv1[0] = xv1[1];
        xv1[1] = xv1[2];
        xv1[2] = xv1[3];
        xv1[3] = xv1[4];
        xv1[4] = xv1[5];
        xv1[5] = xv1[6];
        xv1[6] = xv1[7];
        xv1[7] = xv1[8];
        xv1[8] = xv1[9];
        xv1[9] = xv1[10];
        xv1[10] = accelerationSensorDataRecorded.get(accelerationSensorDataRecorded.size()-1)/GAIN;

        yv1[0] = yv1[1];
        yv1[1] = yv1[2];
        yv1[2] = yv1[3];
        yv1[3] = yv1[4];
        yv1[4] = yv1[5];
        yv1[5] = yv1[6];
        yv1[6] = yv1[7];
        yv1[7] = yv1[8];
        yv1[8] = yv1[9];
        yv1[9] = yv1[10];
        yv1[10] =   (xv1[0] + xv1[10]) + 10 * (xv1[1] + xv1[9]) + 45 * (xv1[2] + xv1[8])
                + 120 * (xv1[3] + xv1[7]) + 210 * (xv1[4] + xv1[6]) + 252 * xv1[5]
                + ( -0.0017696319 * yv1[0]) + (  0.0283358587 * yv1[1])
                + ( -0.2089123247 * yv1[2]) + (  0.9364034626 * yv1[3])
                + ( -2.8352616543 * yv1[4]) + (  6.0842140836 * yv1[5])
                + ( -9.4233371622 * yv1[6]) + ( 10.4762753570 * yv1[7])
                + ( -8.0944065927 * yv1[8]) + (  3.9876543673 * yv1[9]);
        filteredAccDataRecorded.add(yv1[10]);
    }



        //TODO: check if it is necessary to apply butterworth filter to gyroscope data as well

    /**
     * detect step on the fly from filterAccDataRecorded
     * @return 0 if no step detected, 1 if new step is detected, 2 if the old detected step need to be re-calibrate in length
     */
    private int detectStepOnTheFly() {

        //detect step on the fly
        int i = filteredAccDataRecorded.size() -1;
        double next_current_slope = filteredAccDataRecorded.get(i) - filteredAccDataRecorded.get(i-1);
        //detect new previous low at the starting of the movement
        if(next_current_slope > high_slope_threshold && previous_low_index == 0) {
            previous_low = filteredAccDataRecorded.get(i-1);
            previous_low_index = i-1;
        }
        else if (current_slope > 0 && next_current_slope <= 0 && filteredAccDataRecorded.get(i-1) - previous_low >= difference_threshold && filteredAccDataRecorded.get(i-1) > current_high) {
            current_high = filteredAccDataRecorded.get(i-1) ;
            current_high_index = i-1;

        }
        else if(current_slope < 0 && next_current_slope >= 0 && (current_high-filteredAccDataRecorded.get(i-1)) > difference_threshold && filteredAccDataRecorded.get(i-1) < low_threshold && i-1-previous_low_index > step_length_threshold) {
            current_low = filteredAccDataRecorded.get(i-1);
            current_low_index = i-1;
            newStep = true;
        }
        else if (current_slope < 0 && next_current_slope >= 0 && filteredAccDataRecorded.get(i-1) <= previous_low  && i-1-previous_low_index <= 2*step_length_threshold) {
            previous_low = filteredAccDataRecorded.get(i-1);
            previous_low_index = i-1;
            currentStep = new ArrayList<>();
            for (int j = previous_low_index; j < i; j ++) {
                currentStep.add(filteredAccDataRecorded.get(j));
            }
            double corrected_step_length = determineStepLength(OtherUtils.convertArrayListToArray(currentStep));
            if (updatingLocation) {
                //currentLocation.correction_move(corrected_step_length, currentHeading);
            }
            return 2;
        }
        else if (i == filteredAccDataRecorded.size() -1 && (current_low_index == previous_low_index) && (current_high_index > current_low_index) && filteredAccDataRecorded.get(i) < low_threshold && current_high - filteredAccDataRecorded.get(i) > difference_threshold) {
            current_low = filteredAccDataRecorded.get(i);
            current_low_index = i;
            newStep = true;
        }
        if(newStep) {
            steps.add(previous_low_index);
            currentStep = new ArrayList<>();
            //determine the sensor reading for current step
            for (int j = previous_low_index; j < i; j ++) {
                currentStep.add(filteredAccDataRecorded.get(j));
            }
            //update the variables to prepare for a next round of computation
            previous_low = current_low;
            previous_low_index = current_low_index;
            newStep = false;
            current_high = 0;
            stepNum +=1;

            newStep = false;
            double step_length = determineStepLength(OtherUtils.convertArrayListToArray(currentStep));
            //Toast.makeText(getContext(), "step detected: " + stepNum + ", step length: " + step_length, Toast.LENGTH_SHORT ).show();
            updateDistanceTravelled(step_length);

            //if updating location then update our current location
            if (updatingLocation) {
                perform_step(step_length);

            }
            return 1;
        }
        current_slope = next_current_slope;
        return 0;

    }

    void perform_step(double step_length) {
        currentLocation.move(step_length, currentHeading);
        if (pathLengths.size() == 0) {
            pathLengths.add(step_length);
            current_path_variance = step_length_variance;
        }
        else{
            double old_path_length = pathLengths.remove(pathLengths.size() -1);
            double new_path_length = old_path_length + step_length;
            pathLengths.add(new_path_length);
            current_path_variance = current_path_variance + step_length_variance;

            //update current position and current position's covariance matrix
            //position
            double epv2 = Math.exp(-1*current_heading_variance/2);
            double current_rad = OtherUtils.convertToRadAngle(currentHeading);
            double cos = Math.cos(current_rad);
            double sin = Math.sin(current_rad);
            double[] pos = positionBeforeTurn.get(positionBeforeTurn.size()-1);
            currentLocation.coordinateX = pos[0] + epv2
                    *new_path_length*Math.cos(current_rad);
            currentLocation.coordinateY = pos[1] + epv2
                    *new_path_length*Math.sin(current_rad);

            //covariance matrix

            double[][] previous_cov_matrix_before_turn = position_covariance_matrices_before_turn.get(position_covariance_matrices_before_turn.size()-1);
            current_position_covariance_matrix[0][0] = previous_cov_matrix_before_turn[0][0] + epv2*epv2*-1*new_path_length*new_path_length*cos*cos
            + 0.5*(new_path_length*new_path_length + current_path_variance)*
                    (cos*cos*(1+Math.exp(-2*current_heading_variance)) + sin*sin*(1-Math.exp(-2*current_heading_variance)));
            current_position_covariance_matrix[1][1] = previous_cov_matrix_before_turn[1][1] + 0.5*(new_path_length*new_path_length + current_path_variance
            + Math.exp(-2*current_heading_variance)*(-1*(new_path_length*new_path_length + current_path_variance)*Math.cos(2*current_rad)
                    - 2*Math.exp(current_heading_variance)*new_path_length*new_path_length*sin*sin));
            current_position_covariance_matrix[0][1] = previous_cov_matrix_before_turn[0][1]+ Math.exp(-2*current_heading_variance) * cos*sin*
                    (-1*(-1 + Math.exp(current_heading_variance))*new_path_length*new_path_length + current_path_variance);
            current_position_covariance_matrix[1][0] = current_position_covariance_matrix[0][1];

            toastShow("New path length is " + new_path_length + ", Current cov matrix: %f   " + OtherUtils.round(current_position_covariance_matrix[0][0],5) + ", " + OtherUtils.round(current_position_covariance_matrix[0][1],5) +
                    ", " + OtherUtils.round(current_position_covariance_matrix[0][1],5) + "," + OtherUtils.round(current_position_covariance_matrix[1][1],5), 0 );
        }


    }

    void perform_turn(int turn_type) {
        //update current heading
        currentHeading = (currentHeading + turn_type)%8;
        //update the path: add a new path, add the heading correspond to that path, add a new position and a new covariance matrix before turning
        pathHeadings.add(currentHeading);
        pathLengths.add(0.0);
        double[] pos  = {currentLocation.coordinateX, currentLocation.coordinateY};
        positionBeforeTurn.add(pos);
        position_covariance_matrices_before_turn.add(current_position_covariance_matrix);

        //update the path variance and the heading variance
        current_path_variance = step_length_variance;
        current_heading_variance = current_heading_variance + turning_variance;

        toastShow("current heading variance"+ current_heading_variance, 0);

    }
    void resetLocalVariables() {
        filteredAccDataRecorded = new ArrayList<>();
        accelerationSensorDataRecorded = new ArrayList<>();
        gyroscopeSensorDataRecorded = new ArrayList<>();
        gyroscopeXRecorded = new ArrayList<>();
        gyroscopeYRecorded = new ArrayList<>();
        gyroscopeZRecorded = new ArrayList<>();
        accelerationXRecorded = new ArrayList<>();
        accelerationYRecorded = new ArrayList<>();
        accelerationZRecorded = new ArrayList<>();
        xv1 = new double[NPOLES +1];
        yv1 = new double[NPOLES +1];
        xv2 = new double[NPOLES +1];
        yv2 = new double[NPOLES +1];
        //allTurningDataRecorded = new ArrayList<>();
        turningDataRecorded = new ArrayList<>();
        lastRotateX = 0;
        lastRotateY = 0;
        lastRotateZ = 0;
        lastAccX = 0;
        lastAccY = 0;
        lastAccZ = 0;
        distanceTravelled = 0;
        stepNum = 0;
        current_slope = 0;
        current_high = 0;
        previous_low = 0;
        current_low = 0;
        current_low_index = 0;
        previous_low_index = 0;
        current_high_index = 0;
        newStep = false;


    }

    void toastShow(String s, int n) {
        Toast.makeText(getContext(), s, n).show();
    }

    double determineStepLength(double[] steps) {
        double[] length = this.distanceNN.feedForward(steps);
        return length[0] + 0.1;
    }
    void updateDistanceTravelled(double stepLength) {
        distanceTravelled += stepLength;
    }

    double getBatteryLevel() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        batteryStatus = getContext().registerReceiver(null, ifilter);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        double batteryPct = level/(float)scale;
        return batteryPct;
    }



}
