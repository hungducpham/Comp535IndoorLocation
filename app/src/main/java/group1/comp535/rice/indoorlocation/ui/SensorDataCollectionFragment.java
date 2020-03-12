package group1.comp535.rice.indoorlocation.ui;


import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.support.v4.app.Fragment;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import group1.comp535.rice.indoorlocation.R;
import group1.comp535.rice.indoorlocation.adapter.WiFiDataAdapter;
import group1.comp535.rice.indoorlocation.data.LocationPoint;
import group1.comp535.rice.indoorlocation.data.WiFiData;
import group1.comp535.rice.indoorlocation.utils.ButterworthFilter;
import group1.comp535.rice.indoorlocation.utils.LinearSVC;
import group1.comp535.rice.indoorlocation.utils.NeuralNetwork;
import group1.comp535.rice.indoorlocation.utils.OtherUtils;
import group1.comp535.rice.indoorlocation.utils.PredictiveModel;
import group1.comp535.rice.indoorlocation.utils.StepDetectionUtil;

public class SensorDataCollectionFragment extends Fragment implements SensorEventListener {
    Button recordButton, finishRecordingButton, saveButton, discardButton, resetButton, startMeasureButton, stopMeasureButton;
    private EditText textX;
    //sensors
    private SensorManager mSensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;

    //variables to indicate mode of the program
    boolean recording = false; //indicate if sensor data (accelerometer of gyroscope) is being recorded
    private int recordingSensorMode = 0; //0: recording for step data, 1 for turning data
    boolean measuring = false; //boolean to indicate if measure button is clicked (i.e. is performing some kind of testing)
    boolean distance_model_recorded = false; //indicate if you have recorded the distance model and have saved the result somewhere in your phone (so it can be reconstructed)
    boolean turning_model_recorded = false; //indicate if you have recorded the turning model and have saved the result somewhere in your phone (so it can be reconstructed)

    //variable to perform acc/gyro signal processing. Subjected to tuning
    int max_report_latency = 20;
    private double acc_filter_threshold = 0.5;
    private double gyro_filter_threshold = 0.5;
    private int frame_time = 20;
    private double turning_buffer_sum_threshold = 2;



    double distanceTravelled = 0; //total distance travelled

    //scalars needed to detect step. also subjected to tuning
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
    //

    /*
    Variables containing the accelerometer data and detected indices of steps
     */
    ArrayList<Integer> steps = new ArrayList();
    ArrayList<Double> currentStep = new ArrayList<>(); //store the acc data of the current (most recent) step
    int stepNum = 0;
    int currentHeading = 0;

    /**Helper variables to control the recording of sensor data
     */
    private long lastRecordedTimeInMillis;
    LinkedList<Double> turning_data_buffer = new LinkedList<>(); //use a queue to store the turning data to determine a turn
    private ArrayList<Double> turningDataRecorded = new ArrayList<>(); //store turning data recorded
    private ArrayList<Double> accelerationSensorDataRecorded = new ArrayList<>(); //use a buffer to store the step data
    private ArrayList<Double> filteredAccDataRecorded = new ArrayList<>(); //the filtered data to detect step on the fly
    //with the current phone position, only gyroscope data in the Z axis is needed
    //However, with different phone positions we might need to consider data of other axis as well
    private ArrayList<Double> gyroscopeZRecorded = new ArrayList<>();
    boolean possible_turn = false;
    double sum_turning_data_buffer = 0; //sum of the current turning data buffer to perform turn detection
    long lastTurningCheckTime = 0; //
    
    private boolean CUMULATIVE_SENSOR_VALUE = false;

    private PredictiveModel distance_model = new PredictiveModel();
    private PredictiveModel turning_model = new PredictiveModel(); //the current model to perform turning classification

    double lastAccX, lastAccY, lastAccZ = 0;
    double lastRotateX, lastRotateY, lastRotateZ = 0;

    public static SensorDataCollectionFragment getInstance() {
        SensorDataCollectionFragment sf = new SensorDataCollectionFragment();
        return sf;
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        super.onCreate(savedInstanceState);
        Context context = getContext();
        requestPermissionCheck(context);
        initSensors(context);
        initLocalVariables(context);
    }

    /**request permission if needed
     *
     * @param context
     */
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

    /**
     * init sensors
     * @param context
     */
    void initSensors(Context context) {
        this.mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.accelerometer = this.mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        this.gyroscope = this.mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI, max_report_latency);
        mSensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_UI, max_report_latency);
    }

    /**
     * init some local variables, especially distance model and turning model if they exists
     * @param context
     */
    void initLocalVariables(Context context) {
        this.lastRecordedTimeInMillis = System.currentTimeMillis();
        if (distance_model_recorded) {
            this.distance_model = new NeuralNetwork();
            this.distance_model.reconstruct_from_file();
        }
        if (turning_model_recorded) {
            this.turning_model = new LinearSVC();
            this.turning_model.reconstruct_from_file();
        }
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        View v = inflater.inflate(R.layout.sensor_data_collecting_fragment, null);
        initButtons(v);

        return v;
    }

    /**
     * init buttons
     * @param v
     */
    void initButtons(View v) {
        recordButton = v.findViewById(R.id.sensor_record2);
        finishRecordingButton = v.findViewById(R.id.sensor_finish2);
        saveButton = v.findViewById(R.id.sensor_save2);
        discardButton = v.findViewById(R.id.sensor_discard2);
        resetButton = v.findViewById(R.id.sensor_reset2);
        textX = v.findViewById(R.id.txtX_sensor2);


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
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(recording) {
            processSensorData(event);
        }
    }
    /**
     * process sensor data based on current event
     * @param sensorEvent
     * @return
     */
    private long processSensorData(SensorEvent sensorEvent) {
        long currentTimeMillis = System.currentTimeMillis();
        // if recording, record the sensor data in buffer
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

            if (CUMULATIVE_SENSOR_VALUE) //if cumulative sensor recording mode is on. From experience this mode works better
            {
                if (currentTimeMillis - lastRecordedTimeInMillis >= frame_time) {
                    //move frame to the next frame
                    lastRecordedTimeInMillis = currentTimeMillis;
                    //update sensor data based on the buffer
                    updateSensorData();
                    //apply butterworth to acc data
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
                    if (possible_turn && currentTimeMillis - lastTurningCheckTime >= 1500) //we assume that the turn can only happen in 1.5 second maximum
                    {
                        double[] lastTurningData = OtherUtils.convertArrayListToArray(turningDataRecorded);
                        int lastTurn = estimateTurning(lastTurningData);
                        perform_turn(lastTurn); //do nothing for now. Should be used to update current heading
                        toastShow("turning detected, type: " + lastTurn + ", current heading: " + currentHeading, 0);
                        //refresh the turning data
                        possible_turn = false;
                        if( recordingSensorMode == 1) {
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

    void updateSensorData() {
        accelerationSensorDataRecorded.add(Math.sqrt(lastAccX * lastAccX + lastAccY * lastAccY + lastAccZ * lastAccZ));
        gyroscopeZRecorded.add(lastRotateZ);

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
     * detect step on the fly from filterAccDataRecorded
     *
     * @return 0 if no step detected, 1 if new step is detected, 2 if the old detected step need to be re-calibrate in length
     */
    private int detectStepOnTheFly() {
        //use the gradient of the filtered acc data to determine a possible step
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
//            if (updatingLocation) {
//                //currentLocation.correction_move(corrected_step_length, currentHeading);
//            }
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
            //toastShow("step detected: " + stepNum + ", step length: " + step_length, Toast.LENGTH_SHORT );

            perform_step(step_length); //do nothing for now. If updating location then this should be used to update location
            return 1;
        }
        current_slope = next_current_slope;
        return 0;
    }


    /**
     * determine a step length
     * @param steps_acc_data the (filtered) accelerometer data of the step
     * @return step length
     */
    double determineStepLength(double[] steps_acc_data) {
        return distance_model.predict(steps_acc_data);
    }

    /**
     * process a step
     * @param step_length
     */
    void perform_step(double step_length) {
        distanceTravelled += step_length;
    }


    int estimateTurning(double[] turningData) {
        return (int) turning_model.predict(OtherUtils.get_features_array(turningData));
    }

    /**
     * process a turn
     * @param turn_type
     */
    void perform_turn(int turn_type) {
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    void recordButtonClicked() {
        recording = true;
        lastRecordedTimeInMillis = System.currentTimeMillis();
        gyroscopeZRecorded = new ArrayList<>();
        accelerationSensorDataRecorded = new ArrayList<>();
        finishRecordingButton.setVisibility(View.VISIBLE);
        recordButton.setVisibility(View.INVISIBLE);
    }

    void finishRecordingButtonClicked() {
        recording = false;
        finishRecordingButton.setVisibility(View.INVISIBLE);
        saveButton.setVisibility(View.VISIBLE);
        textX.setVisibility(View.VISIBLE);
        discardButton.setVisibility(View.VISIBLE);
        if (recordingSensorMode == 0) {
            double[] accData = OtherUtils.convertArrayListToArray(accelerationSensorDataRecorded);
            double[] filteredAccelerationData = ButterworthFilter.filter(accData);
            int[] steps = StepDetectionUtil.detectStep(filteredAccelerationData);
            toastShow("current method discovers " + (steps.length - 1) + " steps", 0);
        }
        else {
            int lastTurn = estimateTurning(OtherUtils.convertArrayListToArray(gyroscopeZRecorded));
            toastShow("turning estimated:" + lastTurn, 0);
        }
    }
    void saveButtonClicked() {
        double X = Double.parseDouble(textX.getText().toString());
        double[] accData = OtherUtils.convertArrayListToArray(accelerationSensorDataRecorded);
        double[] filteredAccelerationData = ButterworthFilter.filter(accData);
        double[] gyroZData = OtherUtils.convertArrayListToArray(gyroscopeZRecorded);
        if(recordingSensorMode == 0) {
            this.writeSensorData(OtherUtils.convertToString(accData), X, "unfiltered_data.txt");
            this.writeSensorData(OtherUtils.convertToString(filteredAccelerationData), X, "step_data.txt");

        }
        else {
            this.writeSensorData(OtherUtils.convertToString(gyroZData), X, "turn_detection_data.txt");
        }

        saveButton.setVisibility(View.INVISIBLE);
        textX.setVisibility(View.INVISIBLE);
        discardButton.setVisibility(View.INVISIBLE);
        recordButton.setVisibility(View.VISIBLE);

    }
    void discardButtonClicked() {
        discardButton.setVisibility(View.INVISIBLE);
        recordButton.setVisibility(View.VISIBLE);
        textX.setVisibility(View.INVISIBLE);
        saveButton.setVisibility(View.INVISIBLE);
        toastShow("discarded newest data", 0);
        resetLocalVariables();

    }
    void resetButtonClicked() {
        File file1 = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "cumulative_sensor_data.txt");
        file1.delete();
        File file2 = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "step_data.txt");
        file2.delete();
        File file3 = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "unfiltered_data.txt");
        file3.delete();
        resetLocalVariables();
        toastShow("all sensor data reseted", 0);
    }

    void resetLocalVariables() {
        filteredAccDataRecorded = new ArrayList<>();
        accelerationSensorDataRecorded = new ArrayList<>();
        gyroscopeZRecorded = new ArrayList<>();

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
        currentHeading = 0;


    }

    void toastShow(String s, int n) {
        Toast.makeText(getContext(), s, n).show();
    }
    private void writeSensorData(String data, double X, String file_name) {
        try {
            File file = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS), file_name);
            if (!file.exists()) Log.v("debug", "File doesn't exist");
            FileOutputStream fOut = new FileOutputStream(file, true);
            OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);
            BufferedWriter myBufferedWriter = new BufferedWriter(myOutWriter);
            myBufferedWriter.append(data);
            myBufferedWriter.append("\n");
            myBufferedWriter.append("" + X);
            myBufferedWriter.append("\n");
            myBufferedWriter.close();
            fOut.close();
        } catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }





    /*Scalars needed to compute Butterworth filter of the accelerometer data on the fly
     */
    int NPOLES = 10;
    double GAIN = 2.015579946e+04;
    double output = 0;
    double[] xv1 = new double[NPOLES + 1];
    double[] yv1 = new double[NPOLES + 1];
    double[] xv2 = new double[NPOLES + 1];
    double[] yv2 = new double[NPOLES + 1];


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

}
