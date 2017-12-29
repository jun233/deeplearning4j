package org.deeplearning4j.samediff;

import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.TestUtils;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.params.DefaultParamInitializer;
import org.deeplearning4j.samediff.testlayers.SameDiffDense;
import org.deeplearning4j.samediff.testlayers.SameDiffOutput;
import org.junit.Test;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.util.Map;

import static org.junit.Assert.*;

@Slf4j
public class TestSameDiffOutput {

    @Test
    public void testSameDiffOutputBasic() {

        int nIn = 3;
        int nOut = 4;

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .list()
                .layer(new SameDiffOutput.Builder().nIn(nIn).nOut(nOut)
                        .activation(Activation.TANH).lossFunction(LossFunctions.LossFunction.MSE).build())
                .build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();

        Map<String, INDArray> pt1 = net.getLayer(0).paramTable();
        assertNotNull(pt1);
        assertEquals(2, pt1.size());
        assertNotNull(pt1.get(DefaultParamInitializer.WEIGHT_KEY));
        assertNotNull(pt1.get(DefaultParamInitializer.BIAS_KEY));

        assertArrayEquals(new int[]{nIn, nOut}, pt1.get(DefaultParamInitializer.WEIGHT_KEY).shape());
        assertArrayEquals(new int[]{1, nOut}, pt1.get(DefaultParamInitializer.BIAS_KEY).shape());

        INDArray in = Nd4j.create(3, nIn);
        INDArray out = net.output(in);
        assertArrayEquals(new int[]{3, nOut}, out.shape());
    }

    @Test
    public void testPlaceholderReduceSimple(){

        SameDiff sd = SameDiff.create();
        SDVariable v = sd.var("in", new int[]{-1,10});
        SDVariable vSum = sd.sum(v, 1);

    }

    @Test
    public void testSameDiffOutputForward() {

        for (int minibatch : new int[]{5, 1}) {
            int nIn = 3;
            int nOut = 4;

            LossFunctions.LossFunction[] lossFns = new LossFunctions.LossFunction[]{
                    LossFunctions.LossFunction.MSE,
//                    LossFunctions.LossFunction.MCXENT,
//                    LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD,
//                    LossFunctions.LossFunction.L2,
//                    LossFunctions.LossFunction.SQUARED_LOSS,
//                    LossFunctions.LossFunction.KL_DIVERGENCE,
//                    LossFunctions.LossFunction.MEAN_ABSOLUTE_ERROR,
//                    LossFunctions.LossFunction.XENT,
//                    LossFunctions.LossFunction.MEAN_SQUARED_LOGARITHMIC_ERROR
            };

            Activation[] afns = new Activation[]{
                    Activation.TANH,        //MSE
//                    Activation.SOFTMAX,     //MCXENT
//                    Activation.SOFTMAX,     //NLL
//                    Activation.SOFTPLUS,    //L2
//                    Activation.TANH,        //Squared loss
//                    Activation.SIGMOID,     //KLD
//                    Activation.TANH,        //Squared loss
//                    Activation.SIGMOID      //MSLE
            };

            for( int i=0; i<lossFns.length; i++ ){
                LossFunctions.LossFunction lf = lossFns[i];
                Activation a = afns[i];
                log.info("Starting test - " + lf + ", " + a);
                MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                        .list()
                        .layer(new SameDiffOutput.Builder().nIn(nIn).nOut(nOut)
                                .lossFunction(lf)
                                .activation(a)
                                .build())
                        .build();

                MultiLayerNetwork net = new MultiLayerNetwork(conf);
                net.init();

                assertNotNull(net.paramTable());

                MultiLayerConfiguration conf2 = new NeuralNetConfiguration.Builder()
                        .list()
                        .layer(new DenseLayer.Builder().activation(a).nIn(nIn).nOut(nOut).build())
                        .build();

                MultiLayerNetwork net2 = new MultiLayerNetwork(conf2);
                net2.init();

                net.params().assign(net2.params());

                //Check params:
                assertEquals(net2.params(), net.params());
                Map<String, INDArray> params1 = net.paramTable();
                Map<String, INDArray> params2 = net2.paramTable();
                assertEquals(params2, params1);

                INDArray in = Nd4j.rand(minibatch, nIn);
                INDArray out = net.output(in);
                INDArray outExp = net2.output(in);

                assertEquals(outExp, out);

                //Also check serialization:
                MultiLayerNetwork netLoaded = TestUtils.testModelSerialization(net);
                INDArray outLoaded = netLoaded.output(in);

                assertEquals(outExp, outLoaded);
            }
        }
    }
}