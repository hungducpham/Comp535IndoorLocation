package group1.comp535.rice.indoorlocation.utils;

import java.util.ArrayList;

/**
 * Step detection utility
 * Please note that this is the standalone version. There is a version to detect step on the fly in each fragment class. Any modification
 * to the step detection scheme should be reflected in both this and the fragment's code.
 */
public class StepDetectionUtil {

    /**
     * determine the start of each step
     *
     * @param input the accelerator data recorded and filtered through Butterworth filter.
     * @return the start index of each step in the data array
     */
    public static int[] detectStep(double[] input) {
        ArrayList<Integer> steps = new ArrayList<>();
        double current_slope = input[1] - input[0];
        double current_high = 0;
        double previous_low = 0;
        double current_low = 0;
        int current_low_index = 0;
        int previous_low_index = 0;
        int current_high_index = 0;
        double difference_threshold = 1.5;
        double low_threshold = 1;
        double high_slope_threshold = 1;
        int step_length_threshold = 7;

        boolean newStep = false;
        for (int i = 2; i < input.length; i ++) {
            double next_current_slope = input[i] - input[i-1];
            //detect new previous low at the starting of the movement
            if(next_current_slope > high_slope_threshold && previous_low_index == 0) {
                previous_low = input[i-1];
                previous_low_index = i-1;
            }
            else if (current_slope > 0 && next_current_slope <= 0 && input[i-1] - previous_low >= difference_threshold && input[i-1] > current_high) {
                current_high = input[i-1];
                current_high_index = i-1;

            }
            else if(current_slope < 0 && next_current_slope >= 0 && (current_high-input[i-1]) > difference_threshold && input[i-1] < low_threshold && i-1-previous_low_index > step_length_threshold) {
                current_low = input[i-1];
                current_low_index = i-1;
                newStep = true;
            }
            else if (current_slope < 0 && next_current_slope >= 0 && input[i-1] <= previous_low  && i-1-previous_low_index <= 2*step_length_threshold) {
                previous_low = input[i-1];
                previous_low_index = i-1;
            }
            else if (i == input.length -1 && (current_low_index == previous_low_index) && (current_high_index > current_low_index) && input[i] < low_threshold && current_high - input[i] > difference_threshold) {
                current_low = input[i];
                current_low_index = i;
                newStep = true;
            }
            if(newStep) {
                steps.add(previous_low_index);
                previous_low = current_low;
                previous_low_index = current_low_index;
                newStep = false;
                current_high = 0;

            }
            current_slope = next_current_slope;
        }
        steps.add(previous_low_index);
        int[] returnedSteps = OtherUtils.convertArrayListToIntegerArray(steps);
        return returnedSteps;
    }

    /**
     * Function to detect steps from raw accelerometer input (input not applied Butterworth filter)
     * @param input accelerometer data recorded but not filtered through Butterworth
     * @return the starting indices of each step in the data array
     */
    public static int[] detectStepRaw(double[] input)
    {
        int s = input.length;
        ArrayList<Integer> steps = new ArrayList<>();
        double current_high = 0;
        int previous_low_index = 0;
        int trailing_zero = 0;
        boolean waiting_for_next_step = true;
        int potential_previous_low_index = 0;
        double potential_high = 0;

        for(int i = 0; i < s; i ++) {
            if(trailing_zero > 0  || previous_low_index == 0) {
                if (input[i] == 0) {
                    trailing_zero = trailing_zero + 1;
                    if (steps.isEmpty() && waiting_for_next_step) {
                        previous_low_index  = i;
                    }
                }
                else { //input i > 0
                    if(trailing_zero >= 2 && waiting_for_next_step ) {
                        previous_low_index = i-1;
                        current_high = input[i];
                        waiting_for_next_step = false;
                        potential_previous_low_index = 0;
                    }
                    trailing_zero = 0;
                }
            }
            else //now trailing_zero  = 0
            {
                if(!waiting_for_next_step && input[i] > current_high) {
                    current_high = input[i];
                }
                else if (input[i] > potential_high) {
                    potential_high = input[i];
                }
                else if (input[i] == 0) {

                    if ((i + 1 < s && input[i + 1] == 0 && current_high >= 2 && i - previous_low_index >= 5) || (!waiting_for_next_step && i - previous_low_index >= 10)) {
                        steps.add(previous_low_index);
                        steps.add(i);
                        potential_high = 0;
                        current_high = 0;
                        waiting_for_next_step = true;
                        potential_previous_low_index = i;

                    }
                    else if(i == s-1 && current_high >= 2 && i-previous_low_index >= 5) {
                        steps.add(previous_low_index);
                        steps.add(i);
                    }
                    else if(waiting_for_next_step && potential_previous_low_index > 0 && i - potential_previous_low_index >= 5 && potential_high >= 2) {
                        steps.add(potential_previous_low_index);
                        steps.add(i);
                        potential_high =0;
                        current_high = 0;
                        waiting_for_next_step = true;
                    }
                    trailing_zero = 1;
                }
            }
        }
        int[] returnedSteps = OtherUtils.convertArrayListToIntegerArray(steps);
        return returnedSteps;
    }
}
