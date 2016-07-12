package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.interfaces.Statistic;
import edu.cmu.tetrad.algcomparison.statistic.utilities.ArrowConfusion;
import edu.cmu.tetrad.graph.Graph;

/**
 * Created by jdramsey on 7/10/16.
 */
public class ArrowPrecisionStat implements Statistic {
    @Override
    public String getAbbreviation() {
        return "OP";
    }

    @Override
    public String getDescription() {
        return "Orientation (Arrow) precision";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph) {
        ArrowConfusion adjConfusion = new ArrowConfusion(trueGraph, estGraph);
        int arrowsTp = adjConfusion.getArrowsTp();
        int arrowsFp = adjConfusion.getArrowsFp();
        int arrowsFn = adjConfusion.getArrowsFn();
        int arrowsTn = adjConfusion.getArrowsTn();
        return arrowsTp / (double) (arrowsTp + arrowsFp);
    }

    @Override
    public double getUtility(double value) {
        return value;
    }
}
