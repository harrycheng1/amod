/* amodeus - Copyright (c) 2018, ETH Zurich, Institute for Dynamic Systems and Control */
package amod.scenario;

import org.matsim.api.core.v01.network.Network;

import amod.scenario.dataclean.DataCorrector;
import amod.scenario.dataclean.TripDataCleaner;
import amod.scenario.fleetconvert.TripFleetConverter;
import ch.ethz.idsc.amodeus.options.ScenarioOptions;

public abstract class DataOperator {

    public final TripFleetConverter fleetConverter;
    public final DataCorrector dataCorrector;
    public final TripDataCleaner cleaner;
    protected final ScenarioOptions scenarioOptions;
    protected final Network network;

    public DataOperator(TripFleetConverter fleetConverter, DataCorrector dataCorrector, //
            TripDataCleaner cleaner, ScenarioOptions scenarioOptions, Network network) {
        this.fleetConverter = fleetConverter;
        this.dataCorrector = dataCorrector;
        this.cleaner = cleaner;
        this.scenarioOptions = scenarioOptions;
        this.network = network;
    }

    public abstract void setFilters();
}
