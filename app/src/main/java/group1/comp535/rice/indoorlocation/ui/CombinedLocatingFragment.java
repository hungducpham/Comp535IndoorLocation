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
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

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
import group1.comp535.rice.indoorlocation.utils.ButterworthFilter;
import group1.comp535.rice.indoorlocation.utils.ClassificationNeuralNetwork;
import group1.comp535.rice.indoorlocation.utils.LinearSVC;
import group1.comp535.rice.indoorlocation.utils.NeuralNetwork;
import group1.comp535.rice.indoorlocation.utils.OtherUtils;
import group1.comp535.rice.indoorlocation.utils.StepDetectionUtil;


public class CombinedLocatingFragment extends Fragment implements SensorEventListener {


    private final double step_length_variance = 0.003814; //variance (average of square error) of steps length calculation in meter
    private final double turning_variance = 0.00956; //variance (average of square error) of turning angle calculation in rad
    private final int WIFIMODE_1NN = 1;
    List<WiFiData> wifidata = new LinkedList<WiFiData>();
    WifiManager wifi;
    double distanceTravelled = 0;

    /*Scalars needed to compute Butterworth filter of the accelerometer data on the fly
     */
    int NPOLES = 10;
    double GAIN = 2.015579946e+04;
    double output = 0;
    double[] xv1 = new double[NPOLES + 1];
    double[] yv1 = new double[NPOLES + 1];
    double[] xv2 = new double[NPOLES + 1];
    double[] yv2 = new double[NPOLES + 1];

    /*scalars needed to detect step
     */

    double current_slope = 0;
    double current_high = 0;
    double previous_low = 0;
    double current_low = 0;
    int current_low_index = 0;
    int previous_low_index = 0;
    int current_high_index = 0;
    boolean newStep = false;
    double difference_threshold = 1.5;
    double low_threshold = 1;
    double high_slope_threshold = 1;
    int step_length_threshold = 7;

    /*
    Variables containing the accelerometer data and detected indices of steps
     */
    ArrayList<Integer> steps = new ArrayList();
    ArrayList<Double> currentStep = new ArrayList<>(); //store the acc data of the current (most recent) step
    int stepNum = 0;
    int KNNsize = 2;
    /*
    Variables indicating mode and current status of the program
     */
    /**
     * mode of the program
     * mode = 1: program using WiFi only to determine location
     * mode = 2: using both WiFi and sensor data to determine location
     * mode = 3: only recording sensor data for training
     */
    private int mode = 3;
    private boolean recording;
    private int recordingSensorMode = 0;  // sensor mode: 0 = accelerator (distance measuring); 1 = gyroscope (turning measuring)
    private int nn_mode = 1; //neural network mode: 1 = distance + heading;



    private int currentHeading = 0;
    private ArrayList<Double> pathLengths = new ArrayList<>();
    private ArrayList<Integer> pathHeadings = new ArrayList<>();
    private ArrayList<double[]> positionBeforeTurn = new ArrayList<>();
    private double current_heading_variance = 0;
    private double current_path_variance = 0;
    private ArrayList<double[][]> position_covariance_matrices_before_turn = new ArrayList<>();
    private double[][] current_position_covariance_matrix = new double[2][2];

    private String[] mode_name = {"using only WiFi", "using both WiFi and sensor data", "only using sensor data"};
    private boolean CUMULATIVE_SENSOR_VALUE = true;
    //private boolean stepModel = true;
    private int frame_time = 20;
    private double acc_filter_threshold = 0.5;
    private double gyro_filter_threshold = 0.5;
    private double turning_buffer_sum_threshold = 2;
    private int wifi_distance_mode = 1;
    private int wifi_mode = WIFIMODE_1NN; //Wifi location detection mode: 1= KNN with 1 nearest neigbor; 2 = KNN with 3 nearest neighbor; 3 = KNN and square-restriction
    private WiFiDataAdapter adapter;
    private SensorManager mSensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;


    private LocationPoint currentLocation = new LocationPoint();
    private NeuralNetwork distanceNN;
    private LinearSVC turning_model_SVC;
    private boolean possible_turn = false;

    private ArrayList<Double> accelerationSensorDataRecorded = new ArrayList<>();
    private ArrayList<Double> accelerationXRecorded = new ArrayList<>(); //for acceleration data of X
    private ArrayList<Double> accelerationYRecorded = new ArrayList<>(); //for acceleration data of Y
    private ArrayList<Double> accelerationZRecorded = new ArrayList<>(); //for acceleration data of Z
    private ArrayList<Double> gyroscopeSensorDataRecorded = new ArrayList<>();
    private ArrayList<Double> gyroscopeXRecorded = new ArrayList<>();
    private ArrayList<Double> gyroscopeYRecorded = new ArrayList<>();
    private ArrayList<Double> gyroscopeZRecorded = new ArrayList<>();
    private ArrayList<Double> turningDataRecorded = new ArrayList<>();
    private ArrayList<Double> filteredAccDataRecorded = new ArrayList<>();
    private Button locateButton, recordButton, finishRecordingButton, discardButton, saveButton, resetButton,  startButton, stopButton;
    private EditText textX, textY;
    private long lastRecordedTimeInMillis;
    private List<LocationPoint> savedWifiLocations;
    private LinkedList<Double> turning_data_buffer = new LinkedList<>();
    private double sum_turning_data_buffer = 0;
    private double lastAccX = 0;
    private double lastAccY = 0;
    private double lastAccZ = 0;
    private double lastRotateX = 0;
    private double lastRotateY = 0;
    private double lastRotateZ = 0;
    private long lastTurningCheckTime = 0;
    private boolean updatingLocation = false;
    private Intent batteryStatus;

    public static CombinedLocatingFragment getInstance() {
        CombinedLocatingFragment sf = new CombinedLocatingFragment();
        return sf;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context context = getContext();
        requestPermissionCheck(context);
        initSensors(context);
        initLocalVariables(context);

    }

    void requestPermissionCheck(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                (context.checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED)
                || (context.checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED)
                || (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                || (context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                || (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    1);

            Toast.makeText(context, "Requesting Permission", Toast.LENGTH_LONG).show();
            Log.v("Permission", "Permission Requesting");
            //After this point you wait for callback in onRequestPermissionsResult(int, String[], int[]) overriden method

        } else {
            Toast.makeText(context, "No Need for permission", Toast.LENGTH_LONG).show();
            Log.v("Permission", "No Permission Issue");
        }
    }

    void initSensors(Context context) {
        this.mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.accelerometer = this.mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        this.gyroscope = this.mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI, 250);
        mSensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_UI, 250);


    }

    void initLocalVariables(Context context) {
        this.lastRecordedTimeInMillis = System.currentTimeMillis();
        this.distanceNN = new NeuralNetwork();
        this.distanceNN.reconstruct_from_file();
        this.turning_model_SVC = new LinearSVC();
        this.turning_model_SVC.reconstruct_from_file();
        this.pathHeadings.add(currentHeading); //keep track of the headings
        this.positionBeforeTurn.add(new double[2]);
        this.position_covariance_matrices_before_turn.add(new double[2][2]);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.locating_fragment, null);
        ListView listView = (ListView) v.findViewById(R.id.recorded_data);
        initButtons(v);
        initWifiComponents(v, listView);

        Toast.makeText(getContext(), "Current mode of the program: " +
                mode_name[mode - 1], Toast.LENGTH_LONG).show();
        return v;
    }


    void initButtons(View v){
        locateButton = v.findViewById(R.id.locating);
        recordButton = v.findViewById(R.id.record);
        saveButton = v.findViewById(R.id.save);
        discardButton = v.findViewById(R.id.discard);
        resetButton = v.findViewById(R.id.sensor_reset);
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
    }

    void initWifiComponents(View v, ListView listView) {
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
    }


    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        long currentTimeMillis = processSensorData(sensorEvent);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    private void recordButtonClicked() {
        //clear current local variables
        resetLocalVariables();
        recording = true;
        toastShow("recording sensor data", 0);
        //start the timer for frame
        lastRecordedTimeInMillis = System.currentTimeMillis();
        //recordButtonClickTime = lastRecordedTimeInMillis;
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


        //detect_step
        if (recordingSensorMode == 0) //recording accelerator mode
        {
            double[] filteredAccelerationData = ButterworthFilter.filter(accData);
            int[] steps_butterworth = StepDetectionUtil.detectStep(filteredAccelerationData);
            if (steps_butterworth.length > 1) {
                for (int i = 1; i < steps_butterworth.length - 1; i++) {
                    String str = "";
                    for (int j = steps_butterworth[i]; j < steps_butterworth[i + 1]; j++) {
                        str = str + filteredAccelerationData[j] + " ";
                    }
                    this.writeSensorData(str, X, 0, "step_data.txt");
                }
            }
            toastShow("Recorded acceleration, size of data is: " +
                    accelerationSensorDataRecorded.size(), Toast.LENGTH_SHORT);
        } else if (recordingSensorMode == 1) //recording turning mode
        {
            this.writeSensorData(OtherUtils.convertToString(gyroZData), X, 0, "turning_data.txt");
            Toast.makeText(getContext(), "Recorded turn data, type: " + X + ", size of data is: " + gyroZData.length, Toast.LENGTH_SHORT).show();
        }


        saveButton.setVisibility(View.INVISIBLE);
        textX.setVisibility(View.INVISIBLE);
        textY.setVisibility(View.INVISIBLE);
        discardButton.setVisibility(View.INVISIBLE);
        recordButton.setVisibility(View.VISIBLE);
        resetLocalVariables();

    }

    private void discardButtonClicked() {
        resetLocalVariables();
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
     * @param data the sensor data - acceleration data or gyroscope data
     * @param X    X number - for custom use
     * @param Y    Y number - for custom use
     * @param file_name name of the file
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
     *
     * @param k number of nearest neighbors
     * @return the LocationPoint estimated.
     * do nothing for now
     */
    private LocationPoint determineWiFiLocation(int k) {


        LocationPoint resultLocation = new LocationPoint();

        return resultLocation;
    }

    /**
     * determine current location based on current information about path length
     */
    public void determineLocation() {
        /*
         determine location using WiFi only
         */
        if (mode == 1) {
            LocationPoint resultLocation = determineWiFiLocation(KNNsize);

            Toast.makeText(getContext(), "location determined, x: " + resultLocation.coordinateX + ", y: " + resultLocation.coordinateY, Toast.LENGTH_LONG).show();

        }
        /*
        determine location using a combination of IMUs and WiFi
         */
        if (mode == 2) {
            double coordX = currentLocation.coordinateX;
            double coordY = currentLocation.coordinateY;
            toastShow("Location determined, x: " + coordX + " y: " + coordY, 0);
        }
    }

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
        toastShow("Current location is x = " + currentLocation.coordinateX + ", y = " + currentLocation.coordinateY, 0);
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
        recording = false; //update mode of the program
        if (recordingSensorMode == 0) //is recording accelerometer data
        {
            double[] accData = OtherUtils.convertArrayListToArray(accelerationSensorDataRecorded);
            double[] filteredAccelerationData = ButterworthFilter.filter(accData);
            int[] steps_butterworth = StepDetectionUtil.detectStep(filteredAccelerationData);
            //int[] steps_raw = StepDetectionUtil.detectStepRaw(accData);
            toastShow("Number of step detected from Butterworth: " + (steps_butterworth.length - 1), Toast.LENGTH_SHORT);

            //Toast.makeText(getContext(), "Number of step detected using raw data: " + (steps_raw.length/2 ), Toast.LENGTH_LONG).show();
//            double[] filteredAccelerationData2 = OtherUtils.convertArrayListToArray(filteredAccDataRecorded);
//            int[] steps_butterworth2 = StepDetectionUtil.detectStep(filteredAccelerationData2);
//            Toast.makeText(getContext(), "Number of step detected from Butterworth2: " + (steps_butterworth2.length - 1), Toast.LENGTH_SHORT).show();
            stepNum = 0;
        } else if (recordingSensorMode == 1) //is recording gyroscope data
        {
            //int lastTurn = estimateTurning(OtherUtils.convertArrayListToArray(gyroscopeZRecorded));
            //toastShow("turning estimated:" + lastTurn, 0);
        }

        finishRecordingButton.setVisibility(View.INVISIBLE);
        saveButton.setVisibility(View.VISIBLE);
        textX.setVisibility(View.VISIBLE);
        textY.setVisibility(View.VISIBLE);
        discardButton.setVisibility(View.VISIBLE);

    }

    /**
     * process sensor data
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
                if (acc_filter_threshold > 0) {
                    if (accX < acc_filter_threshold) accX = 0;
                    if (accY < acc_filter_threshold) accY = 0;
                    if (accZ < acc_filter_threshold) accZ = 0;
                }
                if (CUMULATIVE_SENSOR_VALUE) {
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
                if (gyro_filter_threshold > 0) {
                    if (Math.abs(rotateX) < gyro_filter_threshold) rotateX = 0;
                    if (Math.abs(rotateY) < gyro_filter_threshold) rotateY = 0;
                    if (Math.abs(rotateZ) < gyro_filter_threshold) rotateZ = 0;
                }
                lastRotateX = rotateX;
                lastRotateY = rotateY;
                lastRotateZ = rotateZ;

            }
            if (CUMULATIVE_SENSOR_VALUE) //if cumulative sensor recording mode is on
            {
                if (currentTimeMillis - lastRecordedTimeInMillis >= frame_time) {
                    //move frame to the next frame
                    lastRecordedTimeInMillis = currentTimeMillis;

                    //calculate the norm of the acceleration vector
                    updateSensorData();
                    //process accelerometer data
                    applyButterworthFilterAcc();

                    if (filteredAccDataRecorded.size() >= 2) {
                        detectStepOnTheFly();
                    }


                    if (!possible_turn && sum_turning_data_buffer >= turning_buffer_sum_threshold) {
                        possible_turn = true;
                        lastTurningCheckTime = currentTimeMillis;
                        turningDataRecorded.clear();
                        for (int i = 0; i < turning_data_buffer.size(); i++) {
                            double t = turning_data_buffer.poll();
                            turningDataRecorded.add(t);
                        }
                        //reset the turning buffer so that it does not mess with the logic of next frames
                        sum_turning_data_buffer = 0;
                        turning_data_buffer.clear();
                        toastShow("possible turn detected", 0);
                    } else if (possible_turn) {
                        turningDataRecorded.add(lastRotateZ);
                    }
                    if (possible_turn && currentTimeMillis - lastTurningCheckTime >= 2000) //we assume that the turn can only happen in 1.5 second maximum
                    {
                        double[] lastTurningData = OtherUtils.convertArrayListToArray(turningDataRecorded);
                        int lastTurn = estimateTurning(lastTurningData);
                        perform_turn(lastTurn);
                        toastShow("turning detected, type: " + lastTurn + ", current heading: " + currentHeading, 0);
                        //refresh the turning data
                        possible_turn = false;
                        if(mode == 3 && recordingSensorMode == 1 && stopButton.getVisibility() == View.INVISIBLE) {
                            finishRecordingButtonClicked();

                        }
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
                if (filteredAccDataRecorded.size() >= 2)
                    detectStepOnTheFly();
            }


        }
        return currentTimeMillis;
    }

    /**
     * estimate the turning category from the last 1.5 second of gyroscope data
     *
     * @return
     */
    private int estimateTurning(double[] turningData) {

        int result = (int) turning_model_SVC.predict(get_features_array(turningData));
        return result;
    }

    private double[] get_features_array(double[] turningData) {
        double[] result = new double[6];
        result[0] = OtherUtils.sum(turningData);
        result[1] = OtherUtils.maximum(turningData);
        result[2] = OtherUtils.minimum(turningData);
        result[3] = result[0] / turningData.length;
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


        if (!possible_turn) {
            turning_data_buffer.add(lastRotateZ);
            sum_turning_data_buffer += Math.abs(lastRotateZ);
            if (turning_data_buffer.size() > 5) {
                double t = turning_data_buffer.poll();
                sum_turning_data_buffer -= Math.abs(t);
            }
        }
    }

    /**
     * apply Butterworth filter on the fly to accelerometer data
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
        xv1[10] = accelerationSensorDataRecorded.get(accelerationSensorDataRecorded.size() - 1) / GAIN;

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
        yv1[10] = (xv1[0] + xv1[10]) + 10 * (xv1[1] + xv1[9]) + 45 * (xv1[2] + xv1[8])
                + 120 * (xv1[3] + xv1[7]) + 210 * (xv1[4] + xv1[6]) + 252 * xv1[5]
                + (-0.0017696319 * yv1[0]) + (0.0283358587 * yv1[1])
                + (-0.2089123247 * yv1[2]) + (0.9364034626 * yv1[3])
                + (-2.8352616543 * yv1[4]) + (6.0842140836 * yv1[5])
                + (-9.4233371622 * yv1[6]) + (10.4762753570 * yv1[7])
                + (-8.0944065927 * yv1[8]) + (3.9876543673 * yv1[9]);
        filteredAccDataRecorded.add(yv1[10]);
    }


    //TODO: check if it is necessary to apply butterworth filter to gyroscope data as well

    //TODO: check if there are better way to detect steps
    /**
     * detect step on the fly from filterAccDataRecorded
     *
     * @return 0 if no step detected, 1 if new step is detected, 2 if the old detected step need to be re-calibrate in length
     */
    private int detectStepOnTheFly() {

        //detect step on the fly
        int i = filteredAccDataRecorded.size() - 1;
        double next_current_slope = filteredAccDataRecorded.get(i) - filteredAccDataRecorded.get(i - 1);
        //detect new previous low at the starting of the movement
        if (next_current_slope > high_slope_threshold && previous_low_index == 0) {
            previous_low = filteredAccDataRecorded.get(i - 1);
            previous_low_index = i - 1;
        } else if (current_slope > 0 && next_current_slope <= 0 && filteredAccDataRecorded.get(i - 1) - previous_low >= difference_threshold && filteredAccDataRecorded.get(i - 1) > current_high) {
            current_high = filteredAccDataRecorded.get(i - 1);
            current_high_index = i - 1;

        } else if (current_slope < 0 && next_current_slope >= 0 && (current_high - filteredAccDataRecorded.get(i - 1)) > difference_threshold && filteredAccDataRecorded.get(i - 1) < low_threshold && i - 1 - previous_low_index > step_length_threshold) {
            current_low = filteredAccDataRecorded.get(i - 1);
            current_low_index = i - 1;
            newStep = true;
        } else if (current_slope < 0 && next_current_slope >= 0 && filteredAccDataRecorded.get(i - 1) <= previous_low && i - 1 - previous_low_index <= 2 * step_length_threshold) {
            previous_low = filteredAccDataRecorded.get(i - 1);
            previous_low_index = i - 1;
            currentStep = new ArrayList<>();
            for (int j = previous_low_index; j < i; j++) {
                currentStep.add(filteredAccDataRecorded.get(j));
            }
            double corrected_step_length = determineStepLength(OtherUtils.convertArrayListToArray(currentStep));
            if (updatingLocation) {
                //currentLocation.correction_move(corrected_step_length, currentHeading);
            }
            return 2;
        } else if (i == filteredAccDataRecorded.size() - 1 && (current_low_index == previous_low_index) && (current_high_index > current_low_index) && filteredAccDataRecorded.get(i) < low_threshold && current_high - filteredAccDataRecorded.get(i) > difference_threshold) {
            current_low = filteredAccDataRecorded.get(i);
            current_low_index = i;
            newStep = true;
        }
        if (newStep) {
            steps.add(previous_low_index);
            currentStep = new ArrayList<>();
            //determine the sensor reading for current step
            for (int j = previous_low_index; j < i; j++) {
                currentStep.add(filteredAccDataRecorded.get(j));
            }
            //update the variables to prepare for a next round of computation
            previous_low = current_low;
            previous_low_index = current_low_index;
            newStep = false;
            current_high = 0;
            stepNum += 1;

            newStep = false;
            double step_length = determineStepLength(OtherUtils.convertArrayListToArray(currentStep));
            //Toast.makeText(getContext(), "step detected: " + stepNum + ", step length: " + step_length, Toast.LENGTH_SHORT ).show();

            perform_step(step_length);

            //if updating location then update our current location
            if (updatingLocation) {


            }
            return 1;
        }
        current_slope = next_current_slope;
        return 0;
    }

    /**
     * update the current location of the user based on the step length and current heading
     * @param step_length
     */
    void perform_step(double step_length) {

        distanceTravelled += step_length;
        if (pathLengths.size() == 0) {
            pathLengths.add(step_length);
            current_path_variance = step_length_variance;
        } else {
            double old_path_length = pathLengths.remove(pathLengths.size() - 1);
            double new_path_length = old_path_length + step_length;
            pathLengths.add(new_path_length);
            current_path_variance = current_path_variance + step_length_variance;

            //update current position and current position's covariance matrix
            //position
            double epv2 = Math.exp(-1 * current_heading_variance / 2);
            double current_rad = OtherUtils.convertToRadAngle(currentHeading);
            double cos = Math.cos(current_rad);
            double sin = Math.sin(current_rad);
            double[] pos = positionBeforeTurn.get(positionBeforeTurn.size() - 1);
            currentLocation.coordinateX = pos[0] + epv2
                    * new_path_length * cos;
            currentLocation.coordinateY = pos[1] + epv2
                    * new_path_length * sin;

            //covariance matrix

            double[][] previous_cov_matrix_before_turn = position_covariance_matrices_before_turn.get(position_covariance_matrices_before_turn.size() - 1);
            current_position_covariance_matrix[0][0] = previous_cov_matrix_before_turn[0][0] + epv2 * epv2 * -1 * new_path_length * new_path_length * cos * cos
                    + 0.5 * (new_path_length * new_path_length + current_path_variance) *
                    (cos * cos * (1 + Math.exp(-2 * current_heading_variance)) + sin * sin * (1 - Math.exp(-2 * current_heading_variance)));
            current_position_covariance_matrix[1][1] = previous_cov_matrix_before_turn[1][1] + 0.5 * (new_path_length * new_path_length + current_path_variance
                    + Math.exp(-2 * current_heading_variance) * (-1 * (new_path_length * new_path_length + current_path_variance) * Math.cos(2 * current_rad)
                    - 2 * Math.exp(current_heading_variance) * new_path_length * new_path_length * sin * sin));
            current_position_covariance_matrix[0][1] = previous_cov_matrix_before_turn[0][1] + Math.exp(-2 * current_heading_variance) * cos * sin *
                    (-1 * (-1 + Math.exp(current_heading_variance)) * new_path_length * new_path_length + current_path_variance);
            current_position_covariance_matrix[1][0] = current_position_covariance_matrix[0][1];

            //toastShow("New path length is " + new_path_length + ", Current cov matrix: %f   " + OtherUtils.round(current_position_covariance_matrix[0][0], 5) + ", " + OtherUtils.round(current_position_covariance_matrix[0][1], 5) +
            //        ", " + OtherUtils.round(current_position_covariance_matrix[0][1], 5) + "," + OtherUtils.round(current_position_covariance_matrix[1][1], 5), 0);
            //toastShow("Step detected, new position: " + currentLocation.coordinateX+ ", "+ currentLocation.coordinateY, 0);
        }


    }

    void perform_turn(int turn_type) {
        //update current heading
        currentHeading = (currentHeading + turn_type) % 8;
        //update the path: add a new path, add the heading correspond to that path, add a new position and a new covariance matrix before turning
        pathHeadings.add(currentHeading);
        pathLengths.add(0.0);
        double[] pos = {currentLocation.coordinateX, currentLocation.coordinateY};
        positionBeforeTurn.add(pos);
        position_covariance_matrices_before_turn.add(current_position_covariance_matrix);

        //update the path variance and the heading variance
        current_path_variance = step_length_variance;
        current_heading_variance = current_heading_variance + turning_variance;

        toastShow("current heading variance" + current_heading_variance, 0);

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
        xv1 = new double[NPOLES + 1];
        yv1 = new double[NPOLES + 1];
        xv2 = new double[NPOLES + 1];
        yv2 = new double[NPOLES + 1];
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
        currentLocation.coordinateX = 0;
        currentLocation.coordinateY =0;
        positionBeforeTurn = new ArrayList<>();
        positionBeforeTurn.add(new double[2]);
        currentHeading = 0;
        current_position_covariance_matrix = new double[2][2];
        current_path_variance = 0;
        current_heading_variance = 0;
        pathLengths = new ArrayList<>();
    }

    void toastShow(String s, int n) {
        Toast.makeText(getContext(), s, n).show();
    }

    double determineStepLength(double[] steps) {
        double[] length = this.distanceNN.feedForward(steps);
        return length[0];
    }

    void updateDistanceTravelled(double stepLength) {
        distanceTravelled += stepLength;
    }

    double getBatteryLevel() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        batteryStatus = getContext().registerReceiver(null, ifilter);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        double batteryPct = level / (float) scale;
        return batteryPct;
    }

    void applyKalmanFilter(double[] wifi_position, double[][] wifi_covariance) {
        //calculate the Kalman gain
        double[][] K = OtherUtils.square_matrix_mul(current_position_covariance_matrix, OtherUtils.invert2x2Matrix(OtherUtils.matrixSum(wifi_covariance, current_position_covariance_matrix)));
        //update Kalman position
        double[] Kalman_position = new double[2];
        Kalman_position[0] = currentLocation.coordinateX + K[0][0] * (wifi_position[0] - currentLocation.coordinateX) + K[0][1] * (wifi_position[1] - currentLocation.coordinateY);
        Kalman_position[1] = currentLocation.coordinateY + K[1][0] * (wifi_position[0] - currentLocation.coordinateX) + K[1][1] * (wifi_position[1] - currentLocation.coordinateY);
        currentLocation.coordinateX = Kalman_position[0];
        currentLocation.coordinateY = Kalman_position[1];
        //update the new covariance matrix
        current_position_covariance_matrix = OtherUtils.matrixSum(current_position_covariance_matrix, OtherUtils.square_matrix_mul(OtherUtils.matrix_times_coef(K, -1), current_position_covariance_matrix));
        //update the path length, path variance; similar to beginning a new path
        pathLengths.add(0.0);
        current_path_variance = 0;

        //update the heading
        //TODO: find a way to update the heading
        currentHeading = currentHeading;


    }


}
