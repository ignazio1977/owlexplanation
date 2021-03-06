package org.semanticweb.owl.explanation.impl.laconic;

import org.semanticweb.owl.explanation.api.*;
import org.semanticweb.owl.explanation.impl.blackbox.EntailmentCheckerFactory;
import org.semanticweb.owlapi.model.*;
import uk.ac.manchester.cs.owlapi.modularity.ModuleType;
import uk.ac.manchester.cs.owlapi.modularity.SyntacticLocalityModuleExtractor;

import static org.semanticweb.owlapi.util.OWLAPIStreamUtils.asSet;

import java.util.*;
import java.util.function.Supplier;

/**
 * Author: Matthew Horridge<br>
 * The University of Manchester<br>
 * Bio-Health Informatics Group<br>
 * Date: 23/04/2011
 */
public class LaconicExplanationGeneratorBasedOnOPlus implements ExplanationGenerator<OWLAxiom> {

    private Set<OWLAxiom> inputAxioms;

    private EntailmentCheckerFactory<OWLAxiom> entailmentCheckerFactory;

    private ExplanationGeneratorFactory<OWLAxiom> delegateFactory;

    protected ExplanationProgressMonitor<OWLAxiom> progressMonitor;

    private OPlusSplitting oplusSplitting = OPlusSplitting.TOP_LEVEL;

    private ModularityTreatment modularityTreatment = ModularityTreatment.MODULE;

    private Supplier<OWLOntologyManager> m;

    public LaconicExplanationGeneratorBasedOnOPlus(Set<? extends OWLAxiom> inputAxioms, EntailmentCheckerFactory<OWLAxiom> entailmentCheckerFactory, ExplanationGeneratorFactory<OWLAxiom> delegateFactory, ExplanationProgressMonitor<OWLAxiom> progressMonitor, Supplier<OWLOntologyManager> m) {
        this.inputAxioms = new HashSet<>(inputAxioms);
        this.entailmentCheckerFactory = entailmentCheckerFactory;
        this.delegateFactory = delegateFactory;
        this.progressMonitor = progressMonitor;
        this.m = m;
    }

    /**
     * Gets explanations for an entailment.  All explanations for the entailment will be returned.
     * @param entailment The entailment for which explanations will be generated.
     * @return A set containing all of the explanations.  The set will be empty if the entailment does not hold.
     * @throws org.semanticweb.owl.explanation.api.ExplanationException
     *          if there was a problem generating the explanation.
     */
    @Override
    public Set<Explanation<OWLAxiom>> getExplanations(OWLAxiom entailment) throws ExplanationException {
        return getExplanations(entailment, Integer.MAX_VALUE);
    }

    /**
     * Gets explanations for an entailment, with limit on the number of explanations returned.
     * @param entailment The entailment for which explanations will be generated.
     * @param limit The maximum number of explanations to generate. This should be a positive integer.
     * @return A set containing explanations.  The maximum cardinality of the set is specified by the limit parameter.
     *         The set may be empty if the entailment does not hold, or if a limit of zero or less is supplied.
     * @throws org.semanticweb.owl.explanation.api.ExplanationException
     *          if there was a problem generating the explanation.
     */
    @Override
    public Set<Explanation<OWLAxiom>> getExplanations(OWLAxiom entailment, int limit) throws ExplanationException {

        OWLOntologyManager man = m.get();
        OWLDataFactory dataFactory = man.getOWLDataFactory();

        OPlusGenerator transformation = new OPlusGenerator(dataFactory, oplusSplitting);

        Set<OWLAxiom> oplusInput;
        if(modularityTreatment.equals(ModularityTreatment.MODULE)) {
            SyntacticLocalityModuleExtractor extractor = new SyntacticLocalityModuleExtractor(man, inputAxioms.stream(), ModuleType.STAR);
            oplusInput = extractor.extract(asSet(entailment.signature()));
        }
        else {
            oplusInput = new HashSet<>(inputAxioms);
        }

        Set<OWLAxiom> oplusAxioms = transformation.transform(oplusInput);
        ExplanationGenerator<OWLAxiom> gen = delegateFactory.createExplanationGenerator(oplusAxioms, new MediatingProgresssMonitor());
        Set<Explanation<OWLAxiom>> oplusExpls = gen.getExplanations(entailment);


        IsLaconicChecker checker = new IsLaconicChecker(dataFactory, entailmentCheckerFactory, LaconicCheckerMode.EARLY_TERMINATING);
        Set<Explanation<OWLAxiom>> laconicExplanations = new HashSet<>();

        for (Explanation<OWLAxiom> expl : oplusExpls) {
            if (checker.isLaconic(expl)) {
                laconicExplanations.add(expl);
            }
        }

        Set<Explanation<OWLAxiom>> reconstitutedLaconicExpls = getReconstitutedExplanations(dataFactory, transformation, laconicExplanations);

        removeWeakerExplanations(dataFactory, transformation, reconstitutedLaconicExpls);

        Set<Explanation<OWLAxiom>> progressMonitorExplanations = new HashSet<>();
        for (Explanation<OWLAxiom> expl : reconstitutedLaconicExpls) {
            progressMonitorExplanations.add(expl);
            progressMonitor.foundExplanation(this, expl, progressMonitorExplanations);
        }
        return laconicExplanations;

    }

    private Set<Explanation<OWLAxiom>> getReconstitutedExplanations(OWLDataFactory dataFactory, OPlusGenerator transformation, Set<Explanation<OWLAxiom>> laconicExplanations) {
        Set<Explanation<OWLAxiom>> reconstitutedLaconicExpls = new HashSet<>();
        for(Explanation<OWLAxiom> expl : laconicExplanations) {
            reconstitutedLaconicExpls.addAll(getReconstitutedExplanations(expl, transformation, dataFactory));
        }
        return reconstitutedLaconicExpls;
    }

    private static void removeWeakerExplanations(OWLDataFactory dataFactory, OPlusGenerator transformation, Set<Explanation<OWLAxiom>> laconicExplanations) {
        for(Explanation<OWLAxiom> explA : new ArrayList<>(laconicExplanations)) {
            for(Explanation<OWLAxiom> explB : new ArrayList<>(laconicExplanations)) {
                if(explA != explB && laconicExplanations.contains(explA) && laconicExplanations.contains(explB)) {
                    Set<OWLAxiom> sourceAxiomsA = getSourceAxioms(explA, transformation);
                    Set<OWLAxiom> sourceAxiomsB = getSourceAxioms(explB, transformation);
                    OPlusGenerator gen2 = new OPlusGenerator(dataFactory, OPlusSplitting.NONE);
                    Set<OWLAxiom> oPlusA = gen2.transform(explA.getAxioms());
                    Set<OWLAxiom> oPlusB = gen2.transform(explB.getAxioms());

                    if(oPlusA.containsAll(oPlusB) && sourceAxiomsA.containsAll(sourceAxiomsB)) {
                        // We can get B from A
                        laconicExplanations.remove(explB);
                    }
                    else if(oPlusB.containsAll(oPlusA) && sourceAxiomsB.containsAll(sourceAxiomsA)) {
                        // A is weaker than B
                        laconicExplanations.remove(explA);
                    }
                }
            }
        }
    }


    private static Set<OWLAxiom> getSourceAxioms(Explanation<OWLAxiom> expl, OPlusGenerator oPlusGenerator) {
        Set<OWLAxiom> result = new HashSet<>();
        for(OWLAxiom ax : expl.getAxioms()) {
            Set<OWLAxiom> sourceAxioms = oPlusGenerator.getAxiom2SourceMap().get(ax);
            if (sourceAxioms != null) {
                result.addAll(sourceAxioms);
            }
        }
        return result;
    }

    private Set<Explanation<OWLAxiom>> getReconstitutedExplanations(Explanation<OWLAxiom> expl, OPlusGenerator oPlusGenerator, OWLDataFactory dataFactory) {
        // Axioms that aren't SubClassOf axioms
        Set<OWLAxiom> nonSubClassOfAxioms = new HashSet<>();
        // SubClassOf axioms that don't share a source axiom with any other axiom
        Set<OWLSubClassOfAxiom> uniqueSourceSubClassAxioms = new HashSet<>();
        // SubClassOf axioms that were merged
        Set<OWLSubClassOfAxiom> reconstitutedAxioms = new HashSet<>();
        // SubClassOf axiom sources that were reconstituted, but have multiple sources
        Set<OWLAxiom> reconstitutedAxiomSourcesWithMultipleSources = new HashSet<>();

        for(OWLAxiom explAx : expl.getAxioms()) {
            if(explAx instanceof OWLSubClassOfAxiom) {
                OWLSubClassOfAxiom sca = (OWLSubClassOfAxiom) explAx;
                Set<OWLAxiom> sameSourceAxioms = oPlusGenerator.getSameSourceAxioms(sca, expl.getAxioms());
                if(!sameSourceAxioms.isEmpty()) {
                    Set<OWLClassExpression> superClassConjuncts = new HashSet<>();
                    Set<OWLClassExpression> subClassDisjuncts = new HashSet<>();
                    for(OWLAxiom ax : sameSourceAxioms) {
                        // We only reconstitute SubClassOfAxioms
                        if(ax instanceof OWLSubClassOfAxiom) {
                            OWLSubClassOfAxiom sameSourceSCA = (OWLSubClassOfAxiom) ax;
                            superClassConjuncts.addAll(sameSourceSCA.getSuperClass().asConjunctSet());
                            subClassDisjuncts.addAll(sameSourceSCA.getSubClass().asDisjunctSet());
                            if(oPlusGenerator.hasMultipleSources(ax)) {
                                reconstitutedAxiomSourcesWithMultipleSources.add(ax);
                            }
                        }
                    }
                    subClassDisjuncts.addAll(sca.getSubClass().asDisjunctSet());
                    superClassConjuncts.addAll(sca.getSuperClass().asConjunctSet());
                    OWLSubClassOfAxiom mergedAxiom = createSubClassAxiom(dataFactory, subClassDisjuncts, superClassConjuncts);
                    reconstitutedAxioms.add(mergedAxiom);
                }
                else {
                    uniqueSourceSubClassAxioms.add(sca);
                }
            }
            else {
                nonSubClassOfAxioms.add(explAx);
            }
        }
        if(reconstitutedAxioms.isEmpty()) {
            return Collections.singleton(expl);
        }
        else {
            Set<OWLAxiom> pool = new HashSet<>();
            pool.addAll(nonSubClassOfAxioms);
            pool.addAll(uniqueSourceSubClassAxioms);
            pool.addAll(reconstitutedAxioms);
            pool.addAll(reconstitutedAxiomSourcesWithMultipleSources);
            if(reconstitutedAxiomSourcesWithMultipleSources.isEmpty()) {
                // We safely reconstituted axioms without any ambiguity
                return Collections.singleton(new Explanation<>(expl.getEntailment(), pool));
            }
            else {
                // We need to compute justifications!
                ExplanationGenerator<OWLAxiom> expGen = delegateFactory.createExplanationGenerator(pool);
                return expGen.getExplanations(expl.getEntailment());
            }
        }
    }

    private static OWLSubClassOfAxiom createSubClassAxiom(OWLDataFactory dataFactory, Set<OWLClassExpression> subClassDisjuncts, Set<OWLClassExpression> superClassConjuncts) {
        OWLClassExpression mergedSubClass;
        if(subClassDisjuncts.size() == 1) {
            mergedSubClass = subClassDisjuncts.iterator().next();
        }
        else {
            mergedSubClass = dataFactory.getOWLObjectUnionOf(subClassDisjuncts);
        }
        OWLClassExpression mergedSuperClass;
        if(superClassConjuncts.size() == 1) {
            mergedSuperClass = superClassConjuncts.iterator().next();
        }
        else {
            mergedSuperClass = dataFactory.getOWLObjectIntersectionOf(superClassConjuncts);
        }
        return dataFactory.getOWLSubClassOfAxiom(mergedSubClass, mergedSuperClass);
    }


    private class MediatingProgresssMonitor implements ExplanationProgressMonitor<OWLAxiom> {
        public MediatingProgresssMonitor() {}

        @Override
        public void foundExplanation(ExplanationGenerator<OWLAxiom> owlAxiomExplanationGenerator, Explanation<OWLAxiom> owlAxiomExplanation, Set<Explanation<OWLAxiom>> allFoundExplanations) {

        }

        @Override
        public boolean isCancelled() {
            return progressMonitor.isCancelled();
        }
    }
}
