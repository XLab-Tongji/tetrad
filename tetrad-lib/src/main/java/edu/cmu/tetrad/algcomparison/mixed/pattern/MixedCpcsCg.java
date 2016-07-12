package edu.cmu.tetrad.algcomparison.mixed.pattern;

import edu.cmu.tetrad.algcomparison.interfaces.Algorithm;
import edu.cmu.tetrad.algcomparison.interfaces.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedCpcsCg implements Algorithm {
    public Graph search(DataSet ds, Parameters parameters) {
        IndependenceTest test = new IndTestScore(new ConditionalGaussianScore(ds));
        CpcStable pc = new CpcStable(test);
        return pc.search();
    }

    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
    }

    public String getDescription() {
        return "CPC-Stable with the conditional Gaussian score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

}

