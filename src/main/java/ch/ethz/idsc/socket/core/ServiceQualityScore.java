/* amodeus - Copyright (c) 2018, ETH Zurich, Institute for Dynamic Systems and Control */
package ch.ethz.idsc.socket.core;

/* package */ class ServiceQualityScore extends LinComScore {
    /* package */ ServiceQualityScore(ScoreParameters scoreParameters) {
        super(scoreParameters.alpha12);
    }
}
