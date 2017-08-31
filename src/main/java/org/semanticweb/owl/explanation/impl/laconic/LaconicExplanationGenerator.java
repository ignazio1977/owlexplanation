package org.semanticweb.owl.explanation.impl.laconic;

import org.semanticweb.owl.explanation.api.*;
import org.semanticweb.owl.explanation.api.Explanation;
import org.semanticweb.owlapi.model.*;
import uk.ac.manchester.cs.owl.explanation.ordering.MutableTree;

import java.util.*;
import java.util.function.Supplier;

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
 * 15-Sep-2008<br><br>
 */
public class LaconicExplanationGenerator<E> implements ExplanationGenerator<E>, ExplanationProgressMonitor<E> {

    private ExplanationGeneratorFactory<E> explanationGeneratorFactory;

    private OWLOntologyManager man;

    private OWLOntology ont;

    private Set<Explanation<E>> lastRegularJusts;

    private Set<Explanation<E>> allPreviouslyFoundJustifications;

    private Set<Explanation<E>> foundLaconicJustifications;

    private ExplanationProgressMonitor<E> progressMonitor;

    private int limit;

    private Set<? extends OWLAxiom> axioms;


    public LaconicExplanationGenerator(Set<? extends OWLAxiom> axioms, ExplanationGeneratorFactory<E> explanationGeneratorFactory, ExplanationProgressMonitor<E> progressMonitor, Supplier<OWLOntologyManager> m) {
        this.man = m.get();
        this.progressMonitor = progressMonitor;
        this.explanationGeneratorFactory = explanationGeneratorFactory;
        this.limit = Integer.MAX_VALUE;
        this.axioms = axioms;
        if (progressMonitor != null) {
            this.progressMonitor = progressMonitor;
        }
        else {
            this.progressMonitor = new NullExplanationProgressMonitor<>();
        }
        this.foundLaconicJustifications = new HashSet<>();
        try {
            this.ont = man.createOntology(Collections.unmodifiableSet(axioms));
        }
        catch (OWLOntologyCreationException e) {
            e.printStackTrace();
        }
        catch (OWLOntologyChangeException e) {
            e.printStackTrace();
        }
        lastRegularJusts = new HashSet<>();
    }


    public Set<OWLAxiom> computeOPlus(Set<OWLAxiom> axioms) {
        OPlusGenerator oPlusGenerator = new OPlusGenerator(man.getOWLDataFactory(), OPlusSplitting.TOP_LEVEL);
        Set<OWLAxiom> oPlus = new HashSet<>();
        for (OWLAxiom ax : axioms) {
            Set<? extends OWLAxiom> weakenedAxioms = ax.accept(oPlusGenerator);
            oPlus.addAll(weakenedAxioms);
        }
        for(OWLAxiom ax : oPlus) {
            if(ax instanceof OWLEquivalentClassesAxiom) {
                OWLEquivalentClassesAxiom eca = (OWLEquivalentClassesAxiom) ax;
                if(oPlus.containsAll(eca.asOWLSubClassOfAxioms())) {
                    System.out.println("POTENTIAL OPT!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!****************");
                }
            }
        }
//        try {
//            OWLOntologyManager man = OWLManager.createOWLOntologyManager();
//            OWLOntology onto = man.createOntology(oPlus);
//            man.saveOntology(onto, URI.create("file:/tmp/oplus" + System.currentTimeMillis() + ".owl"));
//        }
//        catch (OWLOntologyCreationException e) {
//            e.printStackTrace();
//        }
//        catch (OWLOntologyChangeException e) {
//            e.printStackTrace();
//        }
//        catch (OWLOntologyStorageException e) {
//            e.printStackTrace();
//        }
        return oPlus;
    }


    public List<Integer> getPruningDifferences() {
        return null;
    }

    public MutableTree<Explanation> getHst() {
        return null;
    }

    public Set<Explanation<E>> computePreciseJusts(E entailment, int limit) throws OWLException {
        return computePreciseJustsOptimised(entailment, limit);
    }


    public Set<Explanation<E>> getLastRegularJustifications() {
        return lastRegularJusts;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Implementation of progress monitor that is used to listen to the delegate that generates
    //  explanations.  Each time we get a justification from the delegate, we check to see if it
    //  is laconic.  If it is, then we added it to our set of laconic justifications, so that we can
    //  break out early if we have found enough laconic justifications
    //
    ///////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void foundExplanation(ExplanationGenerator<E> explanationGenerator, Explanation<E> explanation, Set<Explanation<E>> allFoundExplanations) {
        notifyLaconicExplanationGeneratorProgressMonitor(explanation);
    }

    /**
     * Determines if the process of finding laconic justifications is cancelled.  This is called by our delegate and
     * by us as a check to see if we can break out. We don't have to continue finding laconic justifications if we have
     * found enough justifications up to the limit.
     * @return <code>true</code> if cancelled or <code>false</code> if not cancelled
     */
    @Override
    public boolean isCancelled() {
        return isAtFoundLaconicJustificationsLimit() || progressMonitor.isCancelled();
    }

    private boolean isAtFoundLaconicJustificationsLimit() {
        return limit == foundLaconicJustifications.size();
    }

    private void notifyLaconicExplanationGeneratorProgressMonitor(Explanation<E> explanation) {
        try {
            if (isLaconic(explanation)) {
                if (!foundLaconicJustifications.contains(explanation)) {
                    foundLaconicJustifications.add(explanation);
                    progressMonitor.foundExplanation(this, explanation, foundLaconicJustifications);
                }
            }
        }
        catch (ExplanationException e) {
            throw new OWLRuntimeException(e);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // END of progress monitor implementation
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public Set<Explanation<E>> computePreciseJustsOptimised(E entailment, int limit) {
        this.limit = limit;
        foundLaconicJustifications.clear();
        ExplanationGenerator<E> gen = explanationGeneratorFactory.createExplanationGenerator(axioms, this);
        Set<Explanation<E>> regularJusts = null;
        try {
            regularJusts = gen.getExplanations(entailment);
        }
        catch (ExplanationGeneratorInterruptedException e) {
            // We have interrupted the generator via our progress monitor
            return foundLaconicJustifications;
        }
        if (isCancelled()) {
            return foundLaconicJustifications;
        }
        lastRegularJusts.clear();

        // Initialise the current set of justifications with the regular justifications.
        allPreviouslyFoundJustifications = new HashSet<>();
        allPreviouslyFoundJustifications.addAll(regularJusts);

//        Set<Explanation<E>> nonLaconicJusts = new HashSet<Explanation<E>>();
//        Set<Explanation<E>> laconicJusts = new HashSet<Explanation<E>>();

        Set<OWLAxiom> axiomsInPreviousOntology = new HashSet<>();

        // We compute justifications until we can't find anymore
        while (true) {

            if (isCancelled()) {
                return getReconstitutedLaconicJustifications();
            }
            // Now we take the union of our current set of justifications and compute
            // O+ from this set.

            Set<OWLAxiom> unionOfAllJustifications = new HashSet<>();
            for (Explanation<E> exp : allPreviouslyFoundJustifications) {
                unionOfAllJustifications.addAll(exp.getAxioms());
            }
            Set<OWLAxiom> oPlus = computeOPlus(unionOfAllJustifications);

            // Create our new ontology.
            Set<OWLAxiom> augmentedAxioms = new HashSet<>(oPlus);

            ///////////////////////////////////////////////////////////////////////////
            // OPTIMISATION
            //////////////////////////////////////////////////////////////////////////

            // Exactly which axioms to we add the ontology?  We need to
            // limit the axioms that we add to O+ to limit the number of
            // intermediate justifications.  Without limiting the performance impact
            // can be HUGE.
            // Basically, if an checker at some stage appeared in a justification that we
            // found, but it never appears in O+ then we don't add it to the ontology.
            // The rationale behind this is that certain axioms such as equivalent classes
            // checker will never ever appear in O+ or in a precise justification.
            ont.logicalAxioms().forEach(ax-> {
                // If we have found the checker previously in a justification
                // but our O+ doesn't contain it then we don't add it.
                if (unionOfAllJustifications.contains(ax) && !oPlus.contains(ax)) {
                    //continue; do noting
                } else {
                    augmentedAxioms.add(ax);
                }
            });

            //////////////////////////////////////////////////////////////////////////
            //////////////////////////////////////////////////////////////////////////

            // Next optimisation


            if (augmentedAxioms.equals(axiomsInPreviousOntology)) {
                break;
            }

            ExplanationGenerator<E> gen2 = explanationGeneratorFactory.createExplanationGenerator(augmentedAxioms, this);

            axiomsInPreviousOntology.clear();
            axiomsInPreviousOntology.addAll(augmentedAxioms);

            Set<Explanation> allPrevJustsCopy = new HashSet<>(allPreviouslyFoundJustifications);

            // Create a generator that gets ALL explanations from OPlus
            Set<Explanation<E>> currentJustifications = null;
            try {
                currentJustifications = gen2.getExplanations(entailment);
            }
            catch (ExplanationGeneratorInterruptedException e) {
                return getReconstitutedLaconicJustifications();
            }
            if (isCancelled()) {
                return getReconstitutedLaconicJustifications();
            }
            allPreviouslyFoundJustifications.addAll(currentJustifications);
            if (allPreviouslyFoundJustifications.equals(allPrevJustsCopy)) {
                break;
            }
            // The following code used to check if we had found enough laconic justifcations.  We don't need this
            // anymore - I don't think
//            for (Explanation<E> e : currentJustifications) {
//                if (nonLaconicJusts.contains(e)) {
//                    continue;
//                }
//                if (laconicJusts.contains(e)) {
//                    continue;
//                }
//                if (isLaconic(e)) {
//                    laconicJusts.add(e);
//                }
//                else {
//                    nonLaconicJusts.add(e);
//                }
//                if (laconicJusts.size() == limit) {
//                    return getReconstitutedLaconicJustifications(laconicJusts);
//                }
//            }
        }

//        Set<Explanation<E>> laconicJustifications = new HashSet<Explanation<E>>();
//
//        // We should now have our justifications that contain the set of precise justifications.
//        // We just need to know which ones are the precise justifications.
//        for (Explanation<E> justification : allPreviouslyFoundJustifications) {
//            if (nonLaconicJusts.contains(justification)) {
//                continue;
//            }
//            if (laconicJusts.contains(justification)) {
//                laconicJustifications.add(justification);
//            } else {
//                if (isLaconic(justification)) {
//                    laconicJustifications.add(justification);
//                }
//            }
//        }

        return getReconstitutedLaconicJustifications();
    }


    /**
     * Checks to see if a justification for a given entailment is laconic.
     * @param justification The justification to be checked
     * @return <code>true</code> if the justification is laconic, otherwise <code>false</code>
     * @throws ExplanationException If there was a problem.  The details of this are implementation specific.
     */
    public boolean isLaconic(Explanation<E> justification) throws ExplanationException {
        // OBSERVATION:  If a justification is laconic, then given its O+, there should be
        // one justification that is equal to itself.

        // Could optimise more here - we know that a laconic justification won't contain
        // equivalent classes axioms, inverse properties axioms etc.  If an checker doesn't
        // appear in O+ then it's not laconic!
        Set<OWLAxiom> justificationSigmaClosure = computeOPlus(justification.getAxioms());
        ExplanationGenerator<E> gen2 = explanationGeneratorFactory.createExplanationGenerator(justificationSigmaClosure);
        Set<Explanation<E>> exps = gen2.getExplanations(justification.getEntailment(), 2);

        return Collections.singleton(justification).equals(exps);
    }

    /**
     * Reconstitutes axioms in laconic justifications
     * @return A set of justifications whose axioms are reconstituted
     */
    private Set<Explanation<E>> getReconstitutedLaconicJustifications() {
        // Simple, but not particularly efficient

        // What we do is examine the subclass axioms in an explanation
        // We merge axioms that have the same LHS and same source checker.  Although the
        // explanations that we operate on are already laconic, we end up producing explanations
        // that will be closer to the asserted axioms
        Map<OWLAxiom, Set<OWLAxiom>> sourceAxioms2OPlus = new HashMap<>();

        for (Explanation<E> exp : allPreviouslyFoundJustifications) {
            for (OWLAxiom ax : exp.getAxioms()) {
                if (ont.containsAxiom(ax)) {
                    sourceAxioms2OPlus.put(ax, computeOPlus(Collections.singleton(ax)));
                }
            }
        }

        Set<Explanation<E>> reconstituedExplanations = new HashSet<>();
        for (Explanation<E> laconicExp : foundLaconicJustifications) {
            // SubClass -> Source Axiom -> SubClass Axiom
            Map<OWLClass, Map<OWLAxiom, Set<OWLSubClassOfAxiom>>> lhs2SubClassAxiom = new HashMap<>();

            Set<OWLAxiom> reconstituedAxioms = new HashSet<>();
            for (OWLAxiom laconicAx : laconicExp.getAxioms()) {
                if (laconicAx instanceof OWLSubClassOfAxiom) {
                    OWLSubClassOfAxiom sca = (OWLSubClassOfAxiom) laconicAx;
                    if (sca.getSubClass().isAnonymous()) {
                        // We only merge stuff with a common NAMED lhs
                        reconstituedAxioms.add(sca);
                        continue;
                    }
                    Map<OWLAxiom, Set<OWLSubClassOfAxiom>> source2AxiomMap = lhs2SubClassAxiom.get(sca.getSubClass().asOWLClass());
                    if (source2AxiomMap == null) {
                        source2AxiomMap = new HashMap<>();
                        lhs2SubClassAxiom.put(sca.getSubClass().asOWLClass(), source2AxiomMap);
                    }
                    for (OWLAxiom sourceAx : sourceAxioms2OPlus.keySet()) {
                        if (sourceAxioms2OPlus.get(sourceAx).contains(sca)) {
                            Set<OWLSubClassOfAxiom> subClassAxioms = source2AxiomMap.get(sourceAx);
                            if (subClassAxioms == null) {
                                subClassAxioms = new HashSet<>();
                                source2AxiomMap.put(sourceAx, subClassAxioms);
                            }
                            subClassAxioms.add(sca);
                        }
                    }
                }
                else {
                    reconstituedAxioms.add(laconicAx);
                }
            }
            // Right, now we have sorted everything, we merge axioms that have a common LHS and common source
            Set<OWLSubClassOfAxiom> consumedAxioms = new HashSet<>();
            for (OWLClass lhs : lhs2SubClassAxiom.keySet()) {
                Map<OWLAxiom, Set<OWLSubClassOfAxiom>> source2SubClassAxiom = lhs2SubClassAxiom.get(lhs);
                for (OWLAxiom source : source2SubClassAxiom.keySet()) {
                    Set<OWLClassExpression> rightHandSides = new HashSet<>();
                    for (OWLSubClassOfAxiom subClassAx : source2SubClassAxiom.get(source)) {
                        if (!consumedAxioms.contains(subClassAx)) {
                            rightHandSides.add(subClassAx.getSuperClass());
                            consumedAxioms.add(subClassAx);
                        }
                    }
                    if (rightHandSides.size() == 1) {
                        reconstituedAxioms.add(man.getOWLDataFactory().getOWLSubClassOfAxiom(lhs, rightHandSides.iterator().next()));
                    }
                    else if (rightHandSides.size() > 1) {
                        OWLObjectIntersectionOf conjunction = man.getOWLDataFactory().getOWLObjectIntersectionOf(rightHandSides);
                        reconstituedAxioms.add(man.getOWLDataFactory().getOWLSubClassOfAxiom(lhs, conjunction));
                    }
                }
            }
            Explanation<E> explanation = new Explanation<>(laconicExp.getEntailment(), reconstituedAxioms);
            reconstituedExplanations.add(explanation);

        }
        return reconstituedExplanations;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Implementation of interfaces
    //
    ///////////////////////////////////////////////////////////////////////////////////////////////////


    @Override
    public Set<Explanation<E>> getExplanations(E entailment) throws ExplanationException {
        return computePreciseJustsOptimised(entailment, Integer.MAX_VALUE);
    }


    @Override
    public Set<Explanation<E>> getExplanations(E entailment, int limit) throws ExplanationException {
        return computePreciseJustsOptimised(entailment, limit);
    }


}
