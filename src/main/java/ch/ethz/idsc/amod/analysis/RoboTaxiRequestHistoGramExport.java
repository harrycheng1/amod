/* amod - Copyright (c) 2018, ETH Zurich, Institute for Dynamic Systems and Control */
package ch.ethz.idsc.amod.analysis;

import java.io.File;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAnchor;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.plot.CategoryPlot;

import ch.ethz.idsc.amodeus.analysis.AnalysisSummary;
import ch.ethz.idsc.amodeus.analysis.element.AnalysisExport;
import ch.ethz.idsc.amodeus.analysis.plot.AmodeusChartUtils;
import ch.ethz.idsc.amodeus.dispatcher.core.RoboTaxi;
import ch.ethz.idsc.amodeus.util.math.GlobalAssert;
import ch.ethz.idsc.tensor.RationalScalar;
import ch.ethz.idsc.tensor.RealScalar;
import ch.ethz.idsc.tensor.Scalar;
import ch.ethz.idsc.tensor.Scalars;
import ch.ethz.idsc.tensor.Tensor;
import ch.ethz.idsc.tensor.alg.Range;
import ch.ethz.idsc.tensor.fig.Histogram;
import ch.ethz.idsc.tensor.fig.VisualSet;
import ch.ethz.idsc.tensor.img.ColorDataIndexed;
import ch.ethz.idsc.tensor.pdf.BinCounts;
import ch.ethz.idsc.tensor.red.Total;
import ch.ethz.matsim.av.passenger.AVRequest;

/** This class generates a png Histogram image of the number of {@link AVRequest} served by each
 * {@link RoboTaxi} */
/* package */ class RoboTaxiRequestHistoGramExport implements AnalysisExport {
    public final static String FILENAME = "requestsPerRoboTaxi.png";
    private static final int WIDTH = 1000;
    private static final int HEIGHT = 750;
    private final RoboTaxiRequestRecorder roboTaxiRequestRecorder;

    public RoboTaxiRequestHistoGramExport(RoboTaxiRequestRecorder roboTaxiRequestRecorder) {
        this.roboTaxiRequestRecorder = roboTaxiRequestRecorder;
    }

    @Override
    public void summaryTarget(AnalysisSummary analysisSummary, File relativeDirectory, ColorDataIndexed colorScheme) {
        /** the data for the histogram is gathered from the RoboTaxiRequestRecorder, basic
         * information can also be retrieved from the analsysisSummary */
        Tensor requestsPerRoboTaxi = roboTaxiRequestRecorder.getRequestsPerRoboTaxi();
        Scalar numberOfRoboTaxis = RealScalar.of(requestsPerRoboTaxi.length());
        Scalar totalRequestsServed = (Scalar) Total.of(requestsPerRoboTaxi);
        Scalar histoGrambinSize = Scalars.lessThan(RealScalar.ZERO, totalRequestsServed) ? //
                totalRequestsServed.divide(numberOfRoboTaxis.multiply(RealScalar.of(10))) : RealScalar.ONE;

        try {
            /** compute bins */
            Scalar numValues = RationalScalar.of(requestsPerRoboTaxi.length(), 1);
            Tensor bins = BinCounts.of(requestsPerRoboTaxi, histoGrambinSize);
            bins = bins.divide(numValues).multiply(RealScalar.of(100)); // norm

            VisualSet visualSet = new VisualSet(colorScheme);
            visualSet.add(Range.of(0, bins.length()).multiply(histoGrambinSize), bins);
            // ---
            visualSet.setPlotLabel("Number of Requests Served per RoboTaxi");
            visualSet.setAxesLabelY("% of RoboTaxis");
            visualSet.setAxesLabelX("Requests");

            JFreeChart jFreeChart = Histogram.of(visualSet, s -> "[" + s.number() + " , " + s.add(histoGrambinSize).number() + ")");
            CategoryPlot categoryPlot = jFreeChart.getCategoryPlot();
            categoryPlot.getDomainAxis().setLowerMargin(0.0);
            categoryPlot.getDomainAxis().setUpperMargin(0.0);
            categoryPlot.getDomainAxis().setCategoryMargin(0.0);
            categoryPlot.getDomainAxis().setCategoryLabelPositions(CategoryLabelPositions.UP_90);
            categoryPlot.setDomainGridlinePosition(CategoryAnchor.START);

            File file = new File(relativeDirectory, FILENAME);
            AmodeusChartUtils.saveAsPNG(jFreeChart, file.toString(), WIDTH, HEIGHT);
            GlobalAssert.that(file.isFile());
        } catch (Exception exception) {
            System.err.println("Plot of the Number of Requests per RoboTaxi Failed");
            exception.printStackTrace();
        }
    }
}
