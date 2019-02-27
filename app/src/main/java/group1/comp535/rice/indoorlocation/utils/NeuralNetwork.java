package group1.comp535.rice.indoorlocation.utils;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;

public class NeuralNetwork {
    int nn_input_dim;
    int nn_hidden_dim;
    int nn_hidden_layer;
    int nn_output_dim;
    String nn_actFun_type;

    double[][] WIn;
    double[][] b1;
    double[][][] WHidden;
    double[][] bHidden;
    double[][] WOut;
    double[][] b2;

    public NeuralNetwork() {

    }
    public NeuralNetwork(int nn_input_dim, int nn_hidden_dim, int nn_hidden_layer, int nn_output_dim, String nn_actFun_type) {
        this.nn_input_dim = nn_input_dim;
        this.nn_hidden_dim = nn_hidden_dim;
        this.nn_hidden_layer = nn_hidden_layer;
        this.nn_output_dim = nn_output_dim;
        this.nn_actFun_type = nn_actFun_type;

    }

    public void importFromFile(int mode) {
        try {

            File model_info = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS), "model" + mode+"_info.txt");
            FileReader fileReader = new FileReader(model_info);
            BufferedReader bfReader = new BufferedReader(fileReader);
            String info = bfReader.readLine();
            this.nn_input_dim = Integer.parseInt(info.split(" ")[0]);
            this.nn_output_dim = Integer.parseInt(info.split(" ")[1]);
            this.nn_hidden_dim = Integer.parseInt(info.split(" ")[2]);
            this.nn_hidden_layer = Integer.parseInt(info.split(" ")[3]);
            this.nn_actFun_type = info.split(" ")[4];


            this.WIn = importArrayFromFile(this.nn_input_dim, this.nn_hidden_dim, "model" + mode + "_WIn.txt");
            this.b1 = importArrayFromFile(1, this.nn_hidden_dim, "model" + mode + "_b1.txt");

            double[][] WHiddenTemp = importArrayFromFile(this.nn_hidden_layer-1, nn_hidden_dim* nn_hidden_dim, "model" + mode + "_WHidden.txt");
            //convert the 2-dimensional WHiddenTemp to 3-dimensional WHidden
            this.WHidden = new double[this.nn_hidden_layer-1][this.nn_hidden_dim][this.nn_hidden_dim];
            for (int i = 0; i <this.nn_hidden_layer-1; i ++ ) {
                double[] temp = WHiddenTemp[i];
                for (int j = 0; j < nn_hidden_dim; j ++ ) {
                    for (int k =0; k < nn_hidden_dim; k ++) {
                        WHidden[i][j][k] = temp[j*nn_hidden_dim + k];
                    }
                }
            }

            this.bHidden = importArrayFromFile(nn_hidden_layer-1, nn_hidden_dim, "model" + mode+"_bHidden.txt");
            this.WOut = importArrayFromFile(nn_hidden_dim, nn_output_dim, "model" + mode +"_WOut.txt");
            this.b2 = importArrayFromFile(1, nn_output_dim, "model" + mode+"_b2.txt");
            Log.v("Load", "Finish loading");

        }
        catch(IOException e) {
            Log.e("Error", "Error opening file ");
        }
    }

    double[][] importArrayFromFile(int dimension1, int dimension2, String fileName) {
        double[][] result = new double[dimension1][dimension2];
        try {
            File file = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS), fileName);
            Scanner sc = new Scanner(file);

            for (int i = 0; i < dimension1; i ++) {
                for (int j =0; j < dimension2; j ++) {
                    result[i][j] = Double.parseDouble(sc.next());
                }
            }

        }
        catch(IOException e) {
            Log.e("Error", "Error opening file ");
        }
        return result;
    }

    public double[] feedForward(double[] inputs) {
        double[] result = new double[nn_output_dim];
        //first pad 0 until the input has nn_input_dim size
        double[] inputs2 = new double[this.nn_input_dim];
        for(int i = 0; i < this.nn_input_dim; i ++) {
            inputs2[i] = (i < inputs.length)?inputs[i]:0;
        }
        inputs = inputs2;
        //feed into input layer
        double[] firstHiddenNodes = feedForwardLayer(inputs, this.WIn);
        firstHiddenNodes = sum2Arrays(firstHiddenNodes, this.b1);
        firstHiddenNodes = actFun(firstHiddenNodes);

        //hidden layers
        double[] hiddenNodes = firstHiddenNodes;
        for(int i = 1; i < this.nn_hidden_layer; i ++) {
            hiddenNodes = feedForwardLayer(hiddenNodes, this.WHidden[i-1]);
            hiddenNodes = sum2Arrays(hiddenNodes, this.bHidden[i-1]);
            hiddenNodes = actFun(hiddenNodes);
        }
        //output layers
        double[] outputNodes = feedForwardLayer(hiddenNodes, this.WOut);
        outputNodes = sum2Arrays(outputNodes, this.b2);
        result = outputNodes;

        return result;


    }

    public double[] actFun(double[] inputs) {
        double[] result = new double[inputs.length];
        if (this.nn_actFun_type.equalsIgnoreCase("tanh")) {
            for(int i = 0; i < inputs.length; i ++) {
                double z = Math.exp(2*inputs[i]);
                result[i] = (z -1/z)/(z + 1/z);
            }
        }
        else if (this.nn_actFun_type.equalsIgnoreCase("sigmoid")) {
            for(int i = 0; i< inputs.length; i ++ ) {
                double z = Math.exp(inputs[i]);
                result[i] = 1/(1+1/z);
            }
        }
        else if (this.nn_actFun_type.equalsIgnoreCase("relu")) {
            for (int i =0; i < inputs.length; i ++) {
                result[i] = Math.max(0,inputs[i]);
            }
        }
        else {
            Log.e("Error", "Wrong input activation type");
        }
        return result;
    }

    public double[] feedForwardLayer(double[] nodes, double[][] weights) {
        int input_dim = nodes.length;
        int output_dim = weights[0].length;
        double[] result = new double[output_dim];
        for(int i = 0; i < input_dim; i ++){
            for (int j = 0; j < output_dim; j ++) {
                result[j] += nodes[i]*weights[i][j];
            }

        }
        return result;
    }
    public double[] sum2Arrays(double[] a, double[] b) {
        double[] c = new double[a.length];
        for(int i = 0; i < c.length; i ++) {
            c[i]  = a[i] + b[i];
        }
        return c;
    }

    public double[] sum2Arrays(double[] a, double[][] b) {
        double[] c = new double[a.length];
        for(int i = 0; i < c.length; i ++) {
            c[i]  = a[i] + b[0][i];
        }
        return c;
    }


}
