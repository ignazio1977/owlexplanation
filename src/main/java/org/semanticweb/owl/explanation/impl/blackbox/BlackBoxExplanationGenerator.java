package org.semanticweb.owl.explanation.impl.blackbox;

import org.semanticweb.owl.explanation.api.*;
import org.semanticweb.owl.explanation.api.Explanation;
import org.semanticweb.owlapi.model.*;

import gnu.trove.list.array.TIntArrayList;
import uk.ac.manchester.cs.owl.explanation.ordering.*;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
/*
 * Copyright (C) 2008, University of Manchester
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
 * Author: Matthew Horridge<br> The University Of Manchester<br> Information Management Group<br> Date:
 * 03-Sep-2008<br><br>
 *
 * An explanation generator that uses black box techniques to compute explanations.  The
 * generation technique consists of two phases: first and expansion phase, and then a
 * contraction phase.  These phases are implemented as plugin strategies.
 *
 * @deprecated Use {@link BlackBoxExplanationGenerator2}
 */
@Deprecated
public class BlackBoxExplanationGenerator<E> implements ExplanationGenerator<E> {

    public static Logger logger = Logger.getLogger("BlackBoxExplanationGenerator");

    public static Level LEVEL = Level.FINEST;

    private ExpansionStrategy<E> expansionStrategy;

    private ContractionStrategy<E> contractionStrategy;

    private EntailmentCheckerFactory<E> checkerFactory;

    private Set<OWLAxiom> workingAxioms;

    private Set<OWLAxiom> module = null;

    private ExplanationProgressMonitor<E> progressMonitor;

    private TIntArrayList prunningDifferences = new TIntArrayList();

    /**
     * Constructs a blackbox explanation generator.
     *
     * @param axioms              The ontologies that provide the source axioms for the explanation
     * @param checkerFactory      A factory that creates the appropriate entailment checkers for the
     *                            type of entailment being explained.
     * @param expansionStrategy   The strategy used during the expansion phase
     * @param contractionStrategy The strategy to be used during the contraction phase
     * @param progressMonitor     A progress monitor - may be <code>null</code>
     */
    public BlackBoxExplanationGenerator(Set<? extends OWLAxiom> axioms, EntailmentCheckerFactory<E> checkerFactory, ExpansionStrategy<E> expansionStrategy, ContractionStrategy<E> contractionStrategy, ExplanationProgressMonitor<E> progressMonitor) {
        workingAxioms = new HashSet<>(axioms);
        this.checkerFactory = checkerFactory;
        this.expansionStrategy = expansionStrategy;
        this.contractionStrategy = contractionStrategy;
        if (progressMonitor != null) {
            this.progressMonitor = progressMonitor;
        } else {
            this.progressMonitor = new NullExplanationProgressMonitor<>();
        }
    }

    protected void addPruningDifference(int diff) {
        prunningDifferences.add(diff);
    }


    public int[] getPruningDifferences() {
        return prunningDifferences.toArray();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Implementation of interfaces
    //
    //////////////////////////////////////////////////////////////////////////////////////////


    @Override
    public Set<Explanation<E>> getExplanations(E entailment) throws ExplanationException {
        return getExplanations(entailment, Integer.MAX_VALUE);
    }


    @Override
    public Set<Explanation<E>> getExplanations(E entailment, int limit) throws ExplanationException {
        try {
            module = extractModule(workingAxioms, checkerFactory.createEntailementChecker(entailment));
            prunningDifferences.clear();
            Set<Explanation<E>> explanations = new HashSet<>();
            Explanation<E> expl = computeExplanation(entailment);
            explanations.add(expl);
            progressMonitor.foundExplanation(this, expl, Collections.unmodifiableSet(explanations));
            if (progressMonitor.isCancelled()) {
                return Collections.singleton(expl);
            }
            if (expl.isEmpty()) {
                return Collections.emptySet();
            }
            if (limit > 1) {
                constructHittingSetTree(entailment, expl, explanations, new HashSet<Set<OWLAxiom>>(), new HashSet<OWLAxiom>(), limit);
            }

            if (explanations.isEmpty()) {
                throw new NotEntailedException(entailment);
            }

            return explanations;
        }
        catch (OWLException e) {
            throw new ExplanationException(e);
        }
    }

    public Set<OWLAxiom> getWorkingAxioms() {
        return workingAxioms;
    }


    private int counter = 0;
    /**
     * Computes a single justification for an entailment.
     *
     * @param entailment The entailment
     * @return The justification or an empty set if the entailment does not hold.
     */
    protected Explanation<E> computeExplanation(E entailment) {
        if (isLoggable()) {
            log("Computing explanation");
        }
        counter++;
                
        // We gradually expand until the entailment holds
        // and then we prune

        // Module?
        EntailmentChecker<E> checker = checkerFactory.createEntailementChecker(entailment);

        // We should
        // Pre-check that the entailment actually holds
        if (!checker.isEntailed(module)) {
            return Explanation.getEmptyExplanation(entailment);
        }

        if(progressMonitor.isCancelled()) {
            return Explanation.getEmptyExplanation(entailment);
        }

        Set<OWLAxiom> expandedAxioms = expansionStrategy.doExpansion(module, checker, progressMonitor);

        if(progressMonitor.isCancelled()) {
            return Explanation.getEmptyExplanation(entailment);
        }


        handlePostExpansion();

        Set<OWLAxiom> expandedAxiomsModule = extractModule(expandedAxioms, checker);
        int expandedSize = expandedAxioms.size();
        final Set<OWLAxiom> justification = contractionStrategy.doPruning(expandedAxiomsModule, checker, progressMonitor);
        int contractionSize = justification.size();
        int prunningDifference = expandedSize - contractionSize;
        prunningDifferences.add(prunningDifference);


        Explanation<E> expl = new Explanation<>(entailment, justification);


        handlePostContraction(checker, expandedAxioms, justification);

        return expl;
    }


    protected Set<OWLAxiom> extractModule(Set<OWLAxiom> axioms, EntailmentChecker<E> checker) throws
            OWLOntologyChangeException {
        return checker.getModule(axioms);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Hitting Set Stuff
    //
    ///////////////////////////////////////////////////////////////////////////////////////////


    /**
     * Orders the axioms in a single MUPS by the frequency of which they appear
     * in all MUPS.
     *
     * @param mups              The MUPS containing the axioms to be ordered
     * @param allJustifications The set of all justifications which is used to calculate the ordering
     * @return The ordered axioms
     */
    private static <E> List<OWLAxiom> getOrderedJustifications(List<OWLAxiom> mups, final Set<Explanation<E>> allJustifications) {
        Comparator<OWLAxiom> mupsComparator = new Comparator<OWLAxiom>() {
            @Override
            public int compare(OWLAxiom o1, OWLAxiom o2) {
                // The checker that appears in most MUPS has the lowest index
                // in the list
                int occ1 = getOccurrences(o1, allJustifications);
                int occ2 = getOccurrences(o2, allJustifications);
                return -occ1 + occ2;
            }
        };
        Collections.sort(mups, mupsComparator);
        return mups;
    }


    /**
     * Given an checker and a set of explanations this method determines how many explanations
     * contain the checker.
     *
     * @param ax        The checker that will be counted.
     * @param axiomSets The explanations to count from
     * @return the number of occurrences of the specified checker in the explanation
     */
    protected static <E> int getOccurrences(OWLAxiom ax, Set<Explanation<E>> axiomSets) {
        int count = 0;
        for (Explanation<E> explanation : axiomSets) {
            if (explanation.getAxioms().contains(ax)) {
                count++;
            }
        }
        return count;
    }

    /**
     * This is a recursive method that builds a hitting set tree to obtain all
     * justifications for an unsatisfiable class.
     *
     * @param justification       The current justification for the current entailment. This
     *                            corresponds to a node in the hitting set tree.
     * @param allJustifications   All of the MUPS that have been found - this set gets populated
     *                            over the course of the tree building process. Initially this
     *                            should just contain the first justification
     * @param satPaths            Paths that have been completed.
     * @param currentPathContents The contents of the current path. Initially this should be an
     *                            empty set.
     */
    private void constructHittingSetTree(E entailment, Explanation<E> justification, Set<Explanation<E>> allJustifications,
                                         Set<Set<OWLAxiom>> satPaths, Set<OWLAxiom> currentPathContents,
                                         int maxExplanations) throws OWLException {

        // We go through the current justifications, checker by checker, and extend the tree
        // with edges for each checker
        List<OWLAxiom> orderedJustification = getOrderedJustifications(new ArrayList<>(justification.getAxioms()), allJustifications);

        while (!orderedJustification.isEmpty()) {
            OWLAxiom axiom = orderedJustification.get(0);
            orderedJustification.remove(0);
            if (allJustifications.size() == maxExplanations) {
                return;
            }

            // Remove the current checker from all the ontologies it is included
            // in

            module.remove(axiom);
            currentPathContents.add(axiom);

            boolean earlyTermination = false;
            // Early path termination. If our path contents are the superset of
            // the contents of a path then we can terminate here.
            for (Set<OWLAxiom> satPath : satPaths) {
                if (currentPathContents.containsAll(satPath)) {
                    earlyTermination = true;
                    break;
                }
            }

            if (!earlyTermination) {
                Explanation<E> newJustification = null;
                for (Explanation<E> foundJustification : allJustifications) {
                    Set<OWLAxiom> foundMUPSCopy = new HashSet<>(foundJustification.getAxioms());
                    foundMUPSCopy.retainAll(currentPathContents);
                    if (foundMUPSCopy.isEmpty()) {
                        // Justification reuse
                        newJustification = foundJustification;
                        break;
                    }
                }
                if (newJustification == null) {
                    newJustification = computeExplanation(entailment);
                }
                // Generate a new node - i.e. a new justification set
                if (axiom.isLogicalAxiom() && newJustification.contains(axiom)) {
                    // How can this be the case???
                    throw new OWLRuntimeException("Explanation contains removed axiom: " + axiom + " (Working axioms contains axiom: " + module.contains(axiom) + ")");
                }

                if (!newJustification.isEmpty()) {
                    // Note that getting a previous justification does not mean
                    // we
                    // can stop. stopping here causes some justifications to be
                    // missed
                    boolean added = allJustifications.add(newJustification);
                    if (added) {
                        progressMonitor.foundExplanation(this, newJustification, allJustifications);
                    }
                    if (progressMonitor.isCancelled()) {
                        return;
                    }

                    // Recompute priority here?
                    constructHittingSetTree(entailment,
                            newJustification,
                            allJustifications,
                            satPaths,
                            currentPathContents,
                            maxExplanations);
                    // We have found a new MUPS, so recalculate the ordering
                    // axioms in the MUPS at the current level
                    orderedJustification = getOrderedJustifications(orderedJustification, allJustifications);
                } else {
                    // End of current path - add it to the list of paths
                    satPaths.add(new HashSet<>(currentPathContents));
                }
            }

            // Back track - go one level up the tree and run for the next checker
            currentPathContents.remove(axiom);

            // Done with the checker that was removed. Add it back in
            module.add(axiom);
        }
    }

    private static boolean isLoggable() {
        return logger.isLoggable(LEVEL);
    }

    private static void log(String s) {
        logger.log(LEVEL, s);
    }

    private static void handlePostExpansion() {
        if (isLoggable()) {
            log("Completed expansion");
        }
    }


    private void handlePostContraction(EntailmentChecker<E> checker, Set<OWLAxiom> expandedAxioms, final Set<OWLAxiom> justification) {
        if (isLoggable()) {
            log("Expanding axioms");
            StringBuilder sb = new StringBuilder();
            ExplanationOrderer orderer = new ExplanationOrdererImplNoManager();
            Tree<OWLAxiom> tree = orderer.getOrderedExplanation((OWLAxiom) checker.getEntailment(), expandedAxioms);
            List<OWLAxiom> axiomList = tree.fillDepthFirst();
            for (OWLAxiom ax : axiomList) {
                if (justification.contains(ax)) {
                    sb.append("*\t");
                    sb.append(ax);
                    sb.append("\n");
                } else {
                    sb.append(" \t");
                    sb.append(ax);
                    sb.append("\n");
                }
            }
            sb.append("----------------------------\n");

            tree.setNodeRenderer(new NodeRenderer<OWLAxiom>() {
                @Override
                public String render(Tree<OWLAxiom> node) {
                    if (justification.contains(node.getUserObject())) {
                        return "*\t" + node;
                    } else {
                        return " \t" + node;
                    }
                }
            });
            StringWriter sw = new StringWriter();
            tree.dump(new PrintWriter(sw));
            sb.append(sw);
            log(sb.toString());
        }
    }


}

