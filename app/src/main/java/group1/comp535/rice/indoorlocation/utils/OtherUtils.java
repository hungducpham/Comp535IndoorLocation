package group1.comp535.rice.indoorlocation.utils;
/*@author: Hung Pham
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public class OtherUtils {
    public static int[] findKMinIndexArrayList(ArrayList<Double> inputs, int k) {
        int[] result = new int[k];
        boolean[] used = new boolean[inputs.size()];
        for (int i = 0; i < inputs.size(); i++) {
            used[i] = false;
        }
        for (int j = 0; j < k; j++) {
            double min = Double.MAX_VALUE;
            for (int i = 0; i < inputs.size(); i++) {
                double d = inputs.get(i);
                if (!used[i] && d < min) {
                    min = d;
                    result[j] = i;
                }
            }
            used[result[j]] = true;
        }
        return result;
    }

    public static String convertToString(ArrayList<Double> a) {
        Double[] input = new Double[1];
        input = a.toArray(input);
        String output = "";
        for (Double i : input) {
            output += i.toString() + " ";
        }
        return output;

    }
    public static String convertToString(double[] input) {
        String output = "";
        for (double i : input) {
            output += i + " ";
        }
        return output;
    }
    public static int[] generateRandomNumbersInRange(int num, int range) {
        Random rand = new Random();
        rand.setSeed(System.currentTimeMillis());
        int[] result = new int[num];
        for (int i =0; i <num; i ++) {
            result[i] = -1;
        }
        for (int i = 0; i< num; i ++) {
            int temp_number = rand.nextInt(range);
            while (search(result, temp_number) != -1) //result already contains the newly generated random num
            {
                temp_number = rand.nextInt(range);
            }
            result[i] = temp_number;
        }
        return result;

    }

    public static int search(int a[], int b) {
        for (int i = 0; i < a.length; i ++) {
            if (a[i] == b) {
                return i;
            }
        }
        return -1;
    }

    public static double[] convertArrayListToArray(ArrayList<Double> inputs) {
        Object[] a = inputs.toArray();
        double[] output = new double[a.length];
        for(int i = 0; i < a.length; i ++) {
            output[i] = (double) a[i];
        }
        return output;
    }

    public static int[] convertArrayListToIntegerArray(ArrayList<Integer> inputs) {
        Object[] a = inputs.toArray();
        int[] output = new int[a.length];
        for(int i = 0; i < a.length; i ++) {
            output[i] = (int) a[i];
        }
        return output;
    }



    /**
     * find the most common value of an int array. If all the values in the array have the same frequency then return the first value
     * @param a input array
     *
     * @return the most common value
     */
    public static int findMostCommonValue(int[] a) {
        int[] temp = new int[a.length];
        for (int i = 0; i < a.length; i ++) {
            temp[i] = a[i];
        }
        Arrays.sort(temp);
        int maxCount = 1;
        int count = 1;
        int result = a[0];
        for (int i = 1; i < temp.length; i ++) {
            if (temp[i] == temp[i-1]) {
                count ++;
            }
            else {
                count  = 1;
            }
            if (count > maxCount) {
                result = temp[i];
                maxCount = count;
            }
        }
        return result;
    }

    public static double[] mapHeadingToMatrix(int heading) {
        double[] result = new double[2];
        double a = 0.7071;
        switch (heading) {
            case 0: {
                result[0] = 1;
                result[1] = 0;
                break;
            }
            case 1: {
                result[0] = a;
                result[1] = -a;
                break;
            }
            case 2: {
                result[0] = 0;
                result[1] = -1;
                break;
            }
            case 3: {
                result[0] = -a;
                result[1] = -a;
                break;
            }
            case 4: {
                result[0] = -1;
                result[1] = 0;
                break;
            }
            case 5: {
                result[0] = -a;
                result[1] = a;
                break;
            }
            case 6: {
                result[0] = 0;
                result[1] = 1;
                break;
            }
            case 7: {
                result[0] = a;
                result[1] = a;
                break;
            }
            default: {
                System.out.println("Something wrong with the direction");
                break;
            }
        }
    return result;
    }

    public static int getMaxIndex(double[] input) {
        int result = 0;
        double currentMax = input[0];
        if(input.length >= 2) {
            for(int i = 1; i < input.length; i ++) {
                if (input[i] > currentMax) {
                    currentMax = input[i];
                    result = i;
                }
            }
        }
        return result;
    }
}


