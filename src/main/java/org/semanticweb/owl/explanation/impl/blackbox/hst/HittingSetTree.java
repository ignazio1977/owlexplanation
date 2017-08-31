package org.semanticweb.owl.explanation.impl.blackbox.hst;

import org.semanticweb.owl.explanation.api.Explanation;
import org.semanticweb.owl.explanation.api.ExplanationGeneratorInterruptedException;
import org.semanticweb.owl.explanation.api.ExplanationProgressMonitor;
import org.semanticweb.owl.explanation.telemetry.*;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.reasoner.TimeOutException;

import java.io.OutputStream;
import java.util.*;
/*
 * Copyright (C) 2010, University of Manchester
 *
 * Modifications to the initial code base are copyright of their
 * respective authors, or their employers as appropriate.  Authorship
 * of the modifications may be determined from the ChangeLog placed at
 * the end of this file.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.

 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.

 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

/**
 * Author: Matthew Horridge<br>
 * The University of Manchester<br>
 * Information Management Group<br>
 * Date: 18-Feb-2010
 */
public class HittingSetTree<E> implements TelemetryObject {

    private HittingSetTreeNode<E> root;

    private Set<Set<OWLAxiom>> closedPaths = new HashSet<>();

    private List<Explanation<E>> explanations = new ArrayList<>();

    private ExplanationProgressMonitor<E> progressMonitor;

    private Set<Set<OWLAxiom>> exploredPaths = new HashSet<>();

    private ExplanationComparator<E> explanationComparator = new ExplanationComparator<>();

    private int treeSize = 0;

    private HashSet<Explanation<E>> allFoundExplanations = new HashSet<>();

    private HittingSetTreeConstructionStrategy<E> strategy;

    private int numberOfNodesWithCallsToFindOne = 0;

    private int numberOfNodesWithReusedJustifications = 0;

    private int numberOfEarlyTerminatedPaths = 0;

    private int numberOfEarlyTerminatedClosedPaths = 0;

    private int closedPathMaxLength = 0;

    private int closedPathMinLength = Integer.MAX_VALUE;


    private int exploredPathMaxLength = 0;

    private int summedPathSize;


    private TelemetryInfo info;


    public HittingSetTree(HittingSetTreeConstructionStrategy<E> strategy, ExplanationProgressMonitor<E> progressMonitor) {
        this.progressMonitor = progressMonitor;
        this.strategy = strategy;
    }

    /**
     * The number of calls to find one.  Find one is called when there isn't a justification that can be reused.
     */
    public void incrementNumberOfNodesWithCallsToFindOne() {
        numberOfNodesWithCallsToFindOne++;
    }

    public void incrementNumberOfNodesWithReusedJustifications() {
        numberOfNodesWithReusedJustifications++;
    }


    public void buildHittingSetTree(E entailment, int limit, ExplanationGeneratorMediator<E> generatorMediator) {

        TelemetryTimer hstTimer = new TelemetryTimer();
        info = new DefaultTelemetryInfo("hittingsettree", hstTimer);
        final TelemetryTransmitter transmitter = TelemetryTransmitter.getTransmitter();
        transmitter.beginTransmission(info);
        boolean foundAll = false;
        try {
            transmitter.recordMeasurement(info, "construction strategy", strategy.getClass().getName());
            hstTimer.start();
            numberOfNodesWithCallsToFindOne = 1;
            Explanation<E> firstExplanation = generatorMediator.generateExplanation(entailment);
            root = new HittingSetTreeNode<>(firstExplanation);
            treeSize = 1;
            addExplanation(firstExplanation);
            if (explanations.size() >= limit) {
                return;
            }
            strategy.constructTree(this, limit, generatorMediator);
            foundAll = true;
        }
        catch (TimeOutException e) {
            transmitter.recordMeasurement(info, "reasoner time out", true);
            throw e;
        }
        catch (ExplanationGeneratorInterruptedException e) {
            transmitter.recordMeasurement(info, "hst interrupted", true);
            throw e;
        }
        catch (RuntimeException e) {
            transmitter.recordMeasurement(info, "hst exception", true);
            transmitter.recordMeasurement(info, "hst exception message", e.getMessage());
            transmitter.recordMeasurement(info, "hst exception class", e.getClass().getName());
            throw e;
        }
        finally {
            hstTimer.stop();
            transmitter.recordMeasurement(info, "number of nodes", treeSize);
            transmitter.recordMeasurement(info, "number of nodes with calls to findone", numberOfNodesWithCallsToFindOne);
            transmitter.recordMeasurement(info, "number of nodes with reused justifications", numberOfNodesWithReusedJustifications);
            transmitter.recordMeasurement(info, "number of closed paths", closedPaths.size());
            transmitter.recordMeasurement(info, "number of early terminated paths", numberOfEarlyTerminatedPaths);
            transmitter.recordMeasurement(info, "number of early terminated closed paths", numberOfEarlyTerminatedClosedPaths);
            transmitter.recordMeasurement(info, "closed path min length", closedPathMinLength);
            transmitter.recordMeasurement(info, "closed path max length", closedPathMaxLength);
            transmitter.recordMeasurement(info, "closed path average length", (((double)summedPathSize) / closedPaths.size()));
            transmitter.recordTiming(info, "construction time", hstTimer);
            transmitter.recordMeasurement(info, "found all", foundAll);
            transmitter.endTransmission(info);
        }
    }


    public Set<Set<OWLAxiom>> getExploredPaths() {
        return exploredPaths;
    }

    public ExplanationProgressMonitor<E> getProgressMonitor() {
        return progressMonitor;
    }

    public void addExplanation(Explanation<E> explanation) {
        if (!explanation.isEmpty() && allFoundExplanations.add(explanation)) {
            explanations.add(explanation);
            Collections.sort(explanations, explanationComparator);
            progressMonitor.foundExplanation(null, explanation, allFoundExplanations);
        }
    }

    public List<Explanation<E>> getSortedExplanations() {
        return explanations;
    }

    public Set<Explanation<E>> getExplanations() {
        return allFoundExplanations;
    }

    public HittingSetTreeNode<E> getRoot() {
        return root;
    }

    public boolean containsClosedPath(Set<OWLAxiom> path) {
        for(Set<OWLAxiom> closedPath : closedPaths) {
            if(closedPath.size() <= path.size() && path.containsAll(closedPath)) {
                numberOfEarlyTerminatedPaths++;
                return true;
            }
        }
        return false;
    }

    public boolean addExploredPath(Set<OWLAxiom> currentPath) {
        treeSize++;
        boolean added = exploredPaths.add(currentPath);
        if(added) {
            summedPathSize += currentPath.size();
        }
        else {
            numberOfEarlyTerminatedPaths++;
        }
        if (currentPath.size() > exploredPathMaxLength) {
            exploredPathMaxLength = currentPath.size();
        }
        return added;
    }

    public void removeCurrentPath(Set<OWLAxiom> currentPath) {
        exploredPaths.remove(currentPath);
    }

    public void addClosedPath(Set<OWLAxiom> pathContents) {
        if (closedPaths.add(pathContents)) {
            if (pathContents.size() < closedPathMinLength) {
                closedPathMinLength = pathContents.size();
            }
            if (pathContents.size() > closedPathMaxLength) {
                closedPathMaxLength = pathContents.size();
            }
        }
    }

    @Override
    public String getPreferredSerialisedName() {
        return "hst";
    }

    @Override
    public boolean isSerialisedAsXML() {
        return false;
    }

    @Override
    public void serialise(OutputStream outputStream) {
    }
}
