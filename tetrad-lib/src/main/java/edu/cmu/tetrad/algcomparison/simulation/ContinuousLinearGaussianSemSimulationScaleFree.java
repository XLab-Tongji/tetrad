package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.interfaces.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.algcomparison.interfaces.Simulation;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jdramsey on 6/4/16.
 */
public class ContinuousLinearGaussianSemSimulationScaleFree implements Simulation {
    private int numDataSets;
    private List<DataSet> dataSets;
    private List<Graph> graphs;

    public ContinuousLinearGaussianSemSimulationScaleFree(Parameters parameters) {
        dataSets = new ArrayList<>();
        graphs = new ArrayList<>();

        for (int i = 0; i < parameters.getInt("numRuns"); i++) {
            Graph graph = GraphUtils.scaleFreeGraph(
                    parameters.getInt("numMeasures"),
                    parameters.getInt("numLatents"),
                    parameters.getDouble("scaleFreeAlpha"),
                    parameters.getDouble("scaleFreeBeta"),
                    parameters.getDouble("scaleFreeDeltaIn"),
                    parameters.getInt("scaleFreeDeltaOut")
            );
            SemPm pm = new SemPm(graph);
            SemIm im = new SemIm(pm);
            dataSets.add(im.simulateData(parameters.getInt("sampleSize"), false));
            graphs.add(graph);
        }
    }

    public DataSet getDataSet(int index) {
        return dataSets.get(index);
    }

    public Graph getTrueGraph(int index) {
        return graphs.get(index);
    }

    public String getDescription() {
        return "Linear, Gaussian SEM simulation";
    }

    @Override
    public int getNumDataSets() {
        return dataSets.size();
    }   private Graph graph;

    @Override
    public DataType getDataType(Parameters parameters) {
        return DataType.Continuous;
    }
}

