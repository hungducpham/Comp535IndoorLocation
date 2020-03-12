package group1.comp535.rice.indoorlocation.utils;

public class ClassificationNeuralNetwork extends NeuralNetwork {

    @Override
    public double predict(double[] inputs) {
        double[] result = feedForward(inputs); //size of result is nn_output_dim, in our case = 8
        int prediction = OtherUtils.getMaxIndex(result);
        return prediction;

    }
    @Override
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
        double sumExp = 0;
        for(int i = 0; i < nn_output_dim; i ++) {
            result[i] = Math.exp(outputNodes[i]);
            sumExp += result[i];
        }

        for(int i = 0; i < nn_output_dim; i ++) {
            result[i] = result[i]/sumExp;
        }

        return result;
    }


}
