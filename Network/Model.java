package Network;

import java.io.Serializable;
import java.text.DecimalFormat;

public class Model implements Serializable {
    int[] _sizes;
    int[] _correct;
    int[] _incorrect;
    Layer[] _layers;

    public Model(int[] sizes) {
        _sizes = sizes;
        // minus 1 because of the fake layer (already given)
        _layers = new Layer[sizes.length - 1];
        _correct = new int[sizes[sizes.length - 1]];
        _incorrect = new int[sizes[sizes.length - 1]];

        for (int i = 0; i < _sizes.length - 1; i++) {
            // m -> neurons
            int M = _sizes[i + 1];
            // d -> previous layer dimensionality
            int D = _sizes[i];

            _layers[i] = new Layer(M, D);
        }
    }

    public Model(int[] sizes, double[][][] weights, double[][] biases) {
        _sizes = sizes;
        // minus 1 because of the fake layer (already given)
        _layers = new Layer[sizes.length - 1];
        _correct = new int[sizes[sizes.length - 1]];
        _incorrect = new int[sizes[sizes.length - 1]];

        for (int i = 0; i < _sizes.length - 1; i++) {
            int M = _sizes[i + 1];
            _layers[i] = new Layer(weights[i], biases[i], M);
        }
    }

    // forward pass through all the layers
    public double[] call(double[] xInput) {
        double[] yCurr = xInput;
        for (int i = 0; i < _layers.length; i++)
            yCurr = _layers[i].forwardPass(yCurr);

        return yCurr;
    }

    // all instances
    // start with hidden layers
    public double[][] predict(double[][] inputs) {
        int lastLayerSize = _sizes[_sizes.length - 1];
        double[][] Y = new double[inputs.length][lastLayerSize];
        // first instance
        for (int i = 0; i < inputs.length; i++) {
            Y[i] = call(inputs[i]);
        }
        return Y;
    }

    // loop through each layer in the batch
    // updates the weights and biases all at once
    public void flush(int batchSize) {
        for (int i = 0; i < _layers.length; i++) {
            _layers[i].flush(batchSize);
        }
    }

    // yTrain is the ground truth, everything included
    public void fit(int epochs, int batchSize, double lr, double[][] bigX, double[][] bigY) {

        // for every epoch, loop through the entire training dataset
        for (int i = 0; i < epochs; i++) {
            System.out.println("**** Epoch " + (i + 1) + " ****");

            // initializes bc to zero
            int batchCounter = 0;

            // for every item in the training data
            for (int y = 0; y < bigY.length; y++) {

                batchCounter++;

                // forward pass, computer's answer
                double[] yHat = call(bigX[y]);

                // ground truth (bygY) for this instance
                double[] Ysingle = bigY[y];

                // last layer terms l+1
                // initialized to a len of 1 because it will be overridden by the last layer
                double[] errorRightLayer = new double[1];

                // start with last layer and move left
                for (int currLayerIndex = _layers.length - 1; currLayerIndex >= 0; currLayerIndex--) {
                    // initialize each node's error term
                    double[] errorTerms = new double[_layers[currLayerIndex]._hidden.length];
                    double[] leftLayersHidden;

                    // the 0th layer is the input layer
                    // not included in _layers because it's fake
                    if (currLayerIndex == 0) {
                        leftLayersHidden = bigX[y];
                    } else {
                        leftLayersHidden = _layers[currLayerIndex - 1]._hidden;
                    }

                    // calculate error terms
                    if (currLayerIndex == _layers.length - 1) {
                        // calculate error term for the right-most layer
                        for (int j = 0; j < errorTerms.length; j++) {

                            // (aj - yj) * aj * (1-aj)
                            // calculated for each node in weights and biases
                            errorTerms[j] = (yHat[j] - Ysingle[j]) * yHat[j] * (1 - yHat[j]);
                        }
                    } else {
                        // error for l-1 layers
                        // won't reach here until after the right-most layer has been calculated
                        // each node has error terms
                        for (int k = 0; k < errorTerms.length; k++) {
                            double rightSum = 0;

                            for (int j = 0; j < errorRightLayer.length; j++) {
                                rightSum += _layers[currLayerIndex + 1]._weights[j][k] * errorRightLayer[j];
                            }
                            // error terms for this layer's nodes
                            // rightSum * current hidden layer * (1- hidden)
                            errorTerms[k] = rightSum * _layers[currLayerIndex]._hidden[k]
                                    * (1 - _layers[currLayerIndex]._hidden[k]);
                        }
                    }

                    // accumulate changes
                    for (int j = 0; j < errorTerms.length; j++) {
                        // add to the biasChanges for the batch
                        _layers[currLayerIndex]._biasChanges[j] += errorTerms[j] * lr;

                        for (int k = 0; k < _layers[currLayerIndex]._weightChanges[j].length; k++) {
                            // add to the weightChanges for the batch
                            _layers[currLayerIndex]._weightChanges[j][k] += errorTerms[j] * lr * leftLayersHidden[k];
                        }
                    }

                    // saves right layer error terms for next loop
                    errorRightLayer = errorTerms;
                }

                // once batch are finished, flush w&b for the next batch
                // if y is the last index, flush the unfinished batch
                if (batchCounter == batchSize || y == bigY.length - 1) {
                    // submitting pending changes in the batch
                    flush(batchCounter);
                    batchCounter = 0;
                }
            }
            countCorrect(bigX, bigY);
            printTrainingData();
        }
    }

    public void countCorrect(double[][] bigX, double[][] bigY) {
        // test the model, save the outputs
        double[][] prediction = predict(bigX);

        //
        for (int p = 0; p < bigY.length; p++) {
            int actual = largest(bigY[p]);
            int predicted = largest(prediction[p]);

            if (actual == predicted) {
                _correct[actual]++;
            } else {
                _incorrect[actual]++;
            }
        }
    }

    public void printTrainingData() {
        DecimalFormat numberFormat = new DecimalFormat("0.##");

        int correctSum = 0;
        int totalSum = 0;
        for (int i = 0; i < _correct.length; i++) {
            System.out.print(i + " = " + _correct[i] + "/" + (_correct[i] + _incorrect[i]) + "  ");
            correctSum += _correct[i];
            totalSum += _correct[i] + _incorrect[i];
        }
        double percent = ((100.0 * correctSum) / totalSum);

        System.out.print("Accuracy = " + numberFormat.format(percent) + "%");
        System.out.println();
        System.out.println();
        _correct = new int[_sizes[_sizes.length - 1]];
        _incorrect = new int[_sizes[_sizes.length - 1]];
    }

    // takes in the output array and returns the largest value
    public int largest(double[] arr) {
        double biggest = 0;
        int index = 0;
        for (int i = 0; i < arr.length; i++) {
            if (biggest < arr[i]) {
                biggest = arr[i];
                index = i;
            }
        }
        return index;
    }

    public void printModel(boolean printState) {
        for (int l = 0; l < _layers.length; l++) {
            System.out.println("Layer index: " + l);
            _layers[l].printLayer(printState);
        }
    }
}
