package amod.demo.dispatcher.SMPC;

import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.collections.QuadTree;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import amod.demo.dispatcher.RebalanceCarSelector;
import amod.demo.dispatcher.claudioForDejan.ClaudioForDejanDispatcher;
import amod.demo.dispatcher.claudioForDejan.ClaudioForDejanUtils;
import amod.demo.dispatcher.claudioForDejan.TravelTimeCalculatorClaudioForDejan;
import ch.ethz.idsc.amodeus.dispatcher.core.PartitionedDispatcher;
import ch.ethz.idsc.amodeus.dispatcher.core.RoboTaxi;
import ch.ethz.idsc.amodeus.dispatcher.core.RoboTaxiStatus;
import ch.ethz.idsc.amodeus.dispatcher.util.AbstractRoboTaxiDestMatcher;
import ch.ethz.idsc.amodeus.dispatcher.util.AbstractVirtualNodeDest;
import ch.ethz.idsc.amodeus.dispatcher.util.BipartiteMatchingUtils;
import ch.ethz.idsc.amodeus.dispatcher.util.DistanceFunction;
import ch.ethz.idsc.amodeus.dispatcher.util.DistanceHeuristics;
import ch.ethz.idsc.amodeus.dispatcher.util.EuclideanDistanceFunction;
import ch.ethz.idsc.amodeus.dispatcher.util.GlobalBipartiteMatching;
import ch.ethz.idsc.amodeus.dispatcher.util.RandomVirtualNodeDest;
import ch.ethz.idsc.amodeus.matsim.SafeConfig;
import ch.ethz.idsc.amodeus.virtualnetwork.VirtualLink;
import ch.ethz.idsc.amodeus.virtualnetwork.VirtualNetwork;
import ch.ethz.idsc.amodeus.virtualnetwork.VirtualNode;
import ch.ethz.idsc.jmex.Container;
import ch.ethz.idsc.jmex.DoubleArray;
import ch.ethz.idsc.jmex.java.JavaContainerSocket;
import ch.ethz.idsc.jmex.matlab.MfileContainerServer;
import ch.ethz.idsc.tensor.Tensor;
import ch.ethz.idsc.tensor.Tensors;
import ch.ethz.matsim.av.config.AVDispatcherConfig;
import ch.ethz.matsim.av.config.AVGeneratorConfig;
import ch.ethz.matsim.av.dispatcher.AVDispatcher;
import ch.ethz.matsim.av.framework.AVModule;
import ch.ethz.matsim.av.passenger.AVRequest;
import ch.ethz.matsim.av.plcpc.ParallelLeastCostPathCalculator;
import ch.ethz.matsim.av.router.AVRouter;

public class SMPCRebalancer extends PartitionedDispatcher {
    private final int rebalancingPeriod;
    private final int dispatchPeriod;
    private final AbstractVirtualNodeDest virtualNodeDest;
    private final AbstractRoboTaxiDestMatcher vehicleDestMatcher;
    private final int numRobotaxi;
    private int total_rebalanceCount = 0;
    private Tensor printVals = Tensors.empty();
//    private final LPVehicleRebalancing lpVehicleRebalancing;
    private final DistanceFunction distanceFunction;
    private final DistanceHeuristics distanceHeuristics;
    private final Network network;
    private final Coord coordNode = new Coord(-122.4322514,37.78096848);
    private final Coord coordNode1 = new Coord(-122.473764,37.778801);
    private final Coord coordNode2 = new Coord(-122.467423,37.693303);
    private final Coord coordNode3 = new Coord(-122.437587,37.774576);
    private final Coord coordNode4 = new Coord(-122.422480,37.798656);
    private final Coord coordNode5 = new Coord(-122.415639,37.758814);
    private final Coord coordNode6 = new Coord(-122.399455,37.681558);
    private final Coord coordNode7 = new Coord(-122.400386,37.784959);
    private final Coord coordNode8 = new Coord(-122.406597,37.627151);
    private final Coord coordNode9 = new Coord(-122.369284,37.824465);
    private final Coord coordNode10 = new Coord(-122.364220,37.587794);
    private final QuadTree<Link> pendingLinkTree;
    private final double[] networkBounds;
    private final Config config;
    private final int timeStep;
    private RebalanceCarSelector rebalanceSelector;
    private double dispatchTime;
    private final BipartiteMatchingUtils bipartiteMatchingEngine;
    
//    final JavaContainerSocket javaContainerSocket;

    public SMPCRebalancer( //
            Config config, AVDispatcherConfig avconfig, //
            AVGeneratorConfig generatorConfig, //
            TravelTime travelTime, //
            ParallelLeastCostPathCalculator router, //
            EventsManager eventsManager, //
            Network network, //
            VirtualNetwork<Link> virtualNetwork, //
            AbstractVirtualNodeDest abstractVirtualNodeDest, //
            AbstractRoboTaxiDestMatcher abstractVehicleDestMatcher) {
        super(config, avconfig, travelTime, router, eventsManager, virtualNetwork);
        virtualNodeDest = abstractVirtualNodeDest;
        vehicleDestMatcher = abstractVehicleDestMatcher;
        numRobotaxi = (int) generatorConfig.getNumberOfVehicles();
        networkBounds = NetworkUtils.getBoundingBox(network.getNodes().values());
        pendingLinkTree = new QuadTree<>(networkBounds[0], networkBounds[1], networkBounds[2], networkBounds[3]);
        for(Link link: network.getLinks().values()) {
            pendingLinkTree.put(link.getCoord().getX(), link.getCoord().getY(), link);
        }       
//        lpVehicleRebalancing = new LPVehicleRebalancing(virtualNetwork);
        SafeConfig safeConfig = SafeConfig.wrap(avconfig);
        dispatchPeriod = safeConfig.getInteger("dispatchPeriod", 30);
        rebalancingPeriod = safeConfig.getInteger("rebalancingPeriod", 300);
        this.network = network;
        distanceHeuristics = DistanceHeuristics.valueOf(safeConfig.getString("distanceHeuristics", //
                DistanceHeuristics.EUCLIDEAN.name()).toUpperCase());
        System.out.println("Using DistanceHeuristics: " + distanceHeuristics.name());
        this.distanceFunction = distanceHeuristics.getDistanceFunction(network);
        this.config = config;
        this.timeStep = 5;
        this.bipartiteMatchingEngine = new BipartiteMatchingUtils(network);

    }

    @Override
    public void redispatch(double now) {

        // PART I: rebalance all vehicles periodically
        final long round_now = Math.round(now);
        

        if (round_now % rebalancingPeriod == 0 && round_now>=rebalancingPeriod) {
            
            // available idle vehicles at virtual nodes
            Map<VirtualNode<Link>,List<RoboTaxi>> idleVehicles = getVirtualNodeDivertableNotRebalancingRoboTaxis(); //TODO is this what you want Dejan?
            
            List<RoboTaxi> taxiWithCustomer = getRoboTaxiSubset(RoboTaxiStatus.DRIVETOCUSTOMER);
            List<RoboTaxi> taxiRebalancing = getRoboTaxiSubset(RoboTaxiStatus.REBALANCEDRIVE);
            
            // travel times
            Map<VirtualLink<Link>, Double> travelTimes = TravelTimeCalculatorClaudioForDejan.computeTravelTimes(virtualNetwork.getVirtualLinks());
            
            // planning horizon for SMPC
            int PlanningHorizon = 50;
            
            Collection<AVRequest> avRequests = getAVRequests();
            
            // prepare inputs for SMPC in MATLAB
            double[][] networkSMPC = SMPCutils.getVirtualNetworkForMatlab(virtualNetwork);
            double [][] travelTimesSMPC = SMPCutils.getTravelTimesVirtualNetworkForMatlab(virtualNetwork, timeStep, travelTimes);
            double[][] availableCarsSMP = SMPCutils.getAvailableCars(round_now, PlanningHorizon, timeStep, avRequests, idleVehicles, taxiWithCustomer, taxiRebalancing, virtualNetwork, travelTimes);                              
            
            try {
                // initialize server
                JavaContainerSocket javaContainerSocket = new JavaContainerSocket(new Socket("localhost", MfileContainerServer.DEFAULT_PORT));

                { // add inputs to server
                Container container = SMPCutils.getContainerInit();
                
                // add network to container
                double[] networkNode = new double[networkSMPC.length];
                for(int index = 0; index<networkSMPC.length; ++index) {
                    networkNode = networkSMPC[index];
                    container.add((new DoubleArray("roadGraph" + index, new int[] { networkSMPC.length }, networkNode)));
                }
                
                // add travel times to container
                double[] travelTimeskNode = new double[travelTimesSMPC.length];
                for(int index = 0; index<travelTimesSMPC.length; ++index) {
                    travelTimeskNode = travelTimesSMPC[index];
                    container.add((new DoubleArray("travelTimes" + index, new int[] { travelTimesSMPC.length }, travelTimeskNode)));
                }
                
                // add available cars to container
                double[] availableCarsNode = new double[availableCarsSMP.length];
                int indexCar = 0;
                for(double[] CarsAtTime: availableCarsSMP) {
                    indexCar = indexCar + 1;
                    availableCarsNode = CarsAtTime;
                    container.add((new DoubleArray("availableCars" + indexCar, new int[] { availableCarsNode.length }, availableCarsNode)));
                }
                                
                // add planning horizon to container
                double[] PlanningHorizonDouble = new double[] {PlanningHorizon};
                container.add((new DoubleArray("PlanningHorizon", new int[] { 1 }, PlanningHorizonDouble)));
                
             // add planning horizon to container
                double[] currentTime = new double[] {round_now};
                container.add((new DoubleArray("currentTime", new int[] { 1 }, currentTime)));
                
                System.out.println("Sending to server");
                javaContainerSocket.writeContainer(container);
                
                }
                
                { // get outputs from server
                System.out.println("Waiting for server");
                Container container = javaContainerSocket.blocking_getContainer();
                System.out.println("received: " + container);
                
                // get control inputs for rebalancing from container
                List<double[]> ControlLaw = new ArrayList<>();        
                for(int i=1; i<=container.size(); ++i) {
                    ControlLaw.add(SMPCutils.getArray(container, "solution"+i));
                }
                
                // apply rebalancing commands
                rebalanceSelector = new RebalanceCarSelector(ControlLaw);
                
                dispatchTime = round_now;
                }
                
                javaContainerSocket.close();
                
            } catch (Exception exception) {
                exception.printStackTrace();
                throw new RuntimeException(); // dispatcher will not work if
                                              // constructor has issues
            }        
                                 
            System.out.println("Finished rebalancing");
            
                      
        }

     // Rebalancing
        if (round_now % 10 == 0 && round_now > rebalancingPeriod && round_now >= dispatchTime && round_now < (dispatchTime + timeStep * 60)) {
            Map<VirtualNode<Link>, List<RoboTaxi>> StayRoboTaxi = getVirtualNodeDivertableNotRebalancingRoboTaxis();
            for (VirtualNode<Link> fromNode : virtualNetwork.getVirtualNodes()) {
                try {
                    List<Pair<RoboTaxi, Link>> controlPolicy = rebalanceSelector.getRebalanceCommands(fromNode,
                            StayRoboTaxi, virtualNetwork);
                    if (controlPolicy != null) {
                        for (Pair<RoboTaxi, Link> pair : controlPolicy) {
                            setRoboTaxiRebalance(pair.getLeft(), pair.getRight());
                        }

                    }
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
        

        if (round_now % dispatchPeriod == 0) {
            printVals = bipartiteMatchingEngine.executePickup(this, getDivertableNotRebalancingRoboTaxis(), //
                    getAVRequests(), distanceFunction, network, false);
        }
    }

    @Override
    protected String getInfoLine() {
        return String.format("%s RV=%s H=%s", //
                super.getInfoLine(), //
                total_rebalanceCount, //
                printVals.toString() //
        );
    }

    public static class Factory implements AVDispatcherFactory {
        @Inject
        @Named(AVModule.AV_MODE)
        private TravelTime travelTime;

        @Inject
        private EventsManager eventsManager;

        @Inject
        @Named(AVModule.AV_MODE)
        private Network network;

        @Inject(optional = true)
        private VirtualNetwork<Link> virtualNetwork;

        @Inject
        private Config config;

        @Override
        public AVDispatcher createDispatcher(AVDispatcherConfig avconfig, AVRouter router) {
            AVGeneratorConfig generatorConfig = avconfig.getParent().getGeneratorConfig();

            AbstractVirtualNodeDest abstractVirtualNodeDest = new RandomVirtualNodeDest();
            AbstractRoboTaxiDestMatcher abstractVehicleDestMatcher = new GlobalBipartiteMatching(new EuclideanDistanceFunction());

            return new SMPCRebalancer(config, avconfig, generatorConfig, travelTime, router, eventsManager, network, virtualNetwork, abstractVirtualNodeDest,
                    abstractVehicleDestMatcher);
        }
    }
}

