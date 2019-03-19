package group1.comp535.rice.indoorlocation.utils;

import android.util.Log;

public class LinearSVC {

    private double[][] coef;
    private double[][] intercept;
    private int n_features;
    private int n_class;
    public LinearSVC() {

    }

    public void reconstruct(double[][] coef, double[][] intercept) {
        this.coef = coef;
        this.intercept = intercept;
        this.n_features = coef[0].length;
        int k = intercept.length;
        this.n_class = (int)Math.floor(Math.sqrt(k*2)) + 1;
        Log.v("DEBUG","Reconstruct SVC, n_class: " + n_class + ", n_features: " + n_features);
    }

    public int predict(double[] input_features) {
        int result = -1;
        int[] scores = new int[this.n_class];
        for( int i = 0; i < this.n_class; i ++) {
            for(int j = i+1; j < this.n_class;j ++) {
                int index = ((this.n_class*2-1-i)*i/2) + j-i-1;
                double score = OtherUtils.dot(this.coef[index], input_features) + this.intercept[index][0];
                if(score >= 0){
                    scores[i] += 1;
                }
                else{
                    scores[j] += 1;
                }
            }
        }
        result = OtherUtils.argmax(scores);
        return result;
    }

    public void reconstruct_from_file() {
        int length = 28;
        int n_features = 6;
        int n_class = 8;
        double[][] coef = OtherUtils.importArrayFromFile(length, n_features, "turning_linear_svm_coef.txt");
        double[][] intercept = OtherUtils.importArrayFromFile(length, 1, "tuninng_linear_svm_intercept.txt");
        reconstruct(coef, intercept);

    }



}
