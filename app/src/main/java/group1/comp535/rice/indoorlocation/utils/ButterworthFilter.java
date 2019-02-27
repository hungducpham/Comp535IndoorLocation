package group1.comp535.rice.indoorlocation.utils;

public class ButterworthFilter {

    /**method to apply Butterworth filter of order 10 and cut-off frequency 15 to an input array
     * @param inputs the inputs array(noisy signals)
     * @return the array of de-noised signals
     */
    public static double[] filter( double[] inputs) {

        int NPOLES = 10;
        double GAIN = 2.015579946e+04;
        double[] output = new double[inputs.length];
        double[] xv = new double[NPOLES +1]; double[] yv = new double[NPOLES + 1];
        for(int i = 0; i < inputs.length; i ++) {
            xv[0] = xv[1];
            xv[1] = xv[2];
            xv[2] = xv[3];
            xv[3] = xv[4];
            xv[4] = xv[5];
            xv[5] = xv[6];
            xv[6] = xv[7];
            xv[7] = xv[8];
            xv[8] = xv[9];
            xv[9] = xv[10];
            xv[10] = inputs[i] / GAIN;

            yv[0] = yv[1];
            yv[1] = yv[2];
            yv[2] = yv[3];
            yv[3] = yv[4];
            yv[4] = yv[5];
            yv[5] = yv[6];
            yv[6] = yv[7];
            yv[7] = yv[8];
            yv[8] = yv[9];
            yv[9] = yv[10];
            yv[10] =   (xv[0] + xv[10]) + 10 * (xv[1] + xv[9]) + 45 * (xv[2] + xv[8])
                    + 120 * (xv[3] + xv[7]) + 210 * (xv[4] + xv[6]) + 252 * xv[5]
                    + ( -0.0017696319 * yv[0]) + (  0.0283358587 * yv[1])
                    + ( -0.2089123247 * yv[2]) + (  0.9364034626 * yv[3])
                    + ( -2.8352616543 * yv[4]) + (  6.0842140836 * yv[5])
                    + ( -9.4233371622 * yv[6]) + ( 10.4762753570 * yv[7])
                    + ( -8.0944065927 * yv[8]) + (  3.9876543673 * yv[9]);
            output[i] = yv[10];
        }
        return output;
    }

}
