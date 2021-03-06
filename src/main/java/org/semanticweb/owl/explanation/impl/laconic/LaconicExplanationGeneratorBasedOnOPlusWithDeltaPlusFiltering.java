package org.semanticweb.owl.explanation.impl.laconic;

import org.semanticweb.owl.explanation.api.*;
import org.semanticweb.owl.explanation.impl.blackbox.EntailmentCheckerFactory;
import org.semanticweb.owl.explanation.telemetry.DefaultTelemetryInfo;
import org.semanticweb.owl.explanation.telemetry.TelemetryInfo;
import org.semanticweb.owl.explanation.telemetry.TelemetryTimer;
import org.semanticweb.owl.explanation.telemetry.TelemetryTransmitter;
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
 * Date: 26/04/2011
 */
public class LaconicExplanationGeneratorBasedOnOPlusWithDeltaPlusFiltering implements ExplanationGenerator<OWLAxiom> {

    private Set<OWLAxiom> inputAxioms;

    private EntailmentCheckerFactory<OWLAxiom> entailmentCheckerFactory;

    private ExplanationGeneratorFactory<OWLAxiom> delegateFactory;

    protected ExplanationProgressMonitor<OWLAxiom> progressMonitor;

    private OPlusSplitting oplusSplitting = OPlusSplitting.TOP_LEVEL;

    private ModularityTreatment modularityTreatment = ModularityTreatment.MODULE;

    protected int numberOfOPlusJustificationsFound = 0;

    private Supplier<OWLOntologyManager> m;

    public LaconicExplanationGeneratorBasedOnOPlusWithDeltaPlusFiltering(Set<? extends OWLAxiom> inputAxioms, EntailmentCheckerFactory<OWLAxiom> entailmentCheckerFactory, ExplanationGeneratorFactory<OWLAxiom> delegateFactory, ExplanationProgressMonitor<OWLAxiom> progressMonitor, OPlusSplitting spiltting, ModularityTreatment inputType, Supplier<OWLOntologyManager> m) {
        this.inputAxioms = new HashSet<>(inputAxioms);
        this.entailmentCheckerFactory = entailmentCheckerFactory;
        this.delegateFactory = delegateFactory;
        this.progressMonitor = progressMonitor;
        this.oplusSplitting = spiltting;
        this.modularityTreatment = inputType;
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

        numberOfOPlusJustificationsFound = 0;
        
        TelemetryTimer computeOplusTimer = new TelemetryTimer();
        TelemetryTimer computeOplusJustificationsTimer = new TelemetryTimer();
        TelemetryTimer islaconicTimer = new TelemetryTimer();
        TelemetryTimer reconstituteTimer = new TelemetryTimer();
        TelemetryTimer ispreferredCheckTimer = new TelemetryTimer();

        TelemetryInfo info = new DefaultTelemetryInfo(getClass().getSimpleName(), computeOplusTimer, computeOplusJustificationsTimer, islaconicTimer, reconstituteTimer, ispreferredCheckTimer);
        TelemetryTransmitter transmitter = TelemetryTransmitter.getTransmitter();
        transmitter.beginTransmission(info);

        transmitter.recordMeasurement(info, "oplus splitting", oplusSplitting.toString());

        transmitter.recordMeasurement(info, "input type", modularityTreatment.toString());

        final Set<Explanation<OWLAxiom>> preferredLaconicExplanations;
        try {
            OWLOntologyManager man = m.get();
            OWLDataFactory dataFactory = man.getOWLDataFactory();

            OPlusGenerator transformation = new OPlusGenerator(dataFactory, oplusSplitting);
            transmitter.recordMeasurement(info, "input axioms size", inputAxioms.size());

            Set<OWLAxiom> oplusInput;
            if (modularityTreatment.equals(ModularityTreatment.MODULE)) {
                SyntacticLocalityModuleExtractor extractor = new SyntacticLocalityModuleExtractor(man, inputAxioms.stream(), ModuleType.STAR);
                oplusInput = extractor.extract(asSet(entailment.signature()));
            }
            else {
                oplusInput = new HashSet<>(inputAxioms);
            }

            transmitter.recordMeasurement(info, "module size", oplusInput.size());

            computeOplusTimer.start();
            Set<OWLAxiom> oplusAxiomsRaw = transformation.transform(oplusInput);
            computeOplusTimer.stop();
            transmitter.recordTiming(info, "time to compute oplus", computeOplusTimer);


            transmitter.recordMeasurement(info, "oplus axioms size", oplusAxiomsRaw.size());

            Set<OWLAxiom> oplusAxioms = new HashSet<>(oplusAxiomsRaw);

            ExplanationGenerator<OWLAxiom> gen = delegateFactory.createExplanationGenerator(oplusAxioms, new MediatingProgresssMonitor());

            computeOplusJustificationsTimer.start();
            Set<Explanation<OWLAxiom>> oplusExpls = gen.getExplanations(entailment);
            computeOplusJustificationsTimer.stop();
            transmitter.recordTiming(info, "time to compute oplus justifications", computeOplusJustificationsTimer);




            IsLaconicChecker checker = new IsLaconicChecker(dataFactory, entailmentCheckerFactory, LaconicCheckerMode.EARLY_TERMINATING);
            Set<Explanation<OWLAxiom>> laconicExplanations = new HashSet<>();

            islaconicTimer.start();
            for (Explanation<OWLAxiom> expl : oplusExpls) {
                if(progressMonitor.isCancelled()) {
                    throw new ExplanationGeneratorInterruptedException();
                }
                if (checker.isLaconic(expl)) {
                    laconicExplanations.add(expl);
                }
            }
            islaconicTimer.stop();
            transmitter.recordTiming(info, "time to check laconic oplus justifications", islaconicTimer);

            transmitter.recordMeasurement(info, "number of laconic oplus justifications", laconicExplanations.size());


            reconstituteTimer.start();
            Set<Explanation<OWLAxiom>> reconstitutedLaconicExpls = getReconstitutedExplanations(dataFactory, transformation, laconicExplanations);
            reconstituteTimer.stop();
            transmitter.recordTiming(info, "time to reconstitute justifications", reconstituteTimer);

            transmitter.recordMeasurement(info, "number of reconstituted justifications", reconstitutedLaconicExpls.size());


            preferredLaconicExplanations = new HashSet<>();

            Map<Explanation<OWLAxiom>, Set<OWLAxiom>> oplusCache = new HashMap<>();


            ispreferredCheckTimer.start();
            Set<Explanation<OWLAxiom>> toFilter = new HashSet<>();
            List<Explanation<OWLAxiom>> explsList = new ArrayList<>(reconstitutedLaconicExpls);
            for (int i = 0; i < explsList.size(); i++) {
                for (int j = i + 1; j < explsList.size(); j++) {
                    Explanation<OWLAxiom> explI = explsList.get(i);
                    Explanation<OWLAxiom> explJ = explsList.get(j);
                    Set<OWLAxiom> oplusI = oplusCache.get(explI);
                    if (oplusI == null) {
                        OPlusGenerator generator = new OPlusGenerator(dataFactory, OPlusSplitting.NONE);
                        oplusI = generator.transform(explI.getAxioms());
                        oplusCache.put(explI, oplusI);
                    }
                    Set<OWLAxiom> oplusJ = oplusCache.get(explJ);
                    if (oplusJ == null) {
                        OPlusGenerator generator = new OPlusGenerator(dataFactory, OPlusSplitting.NONE);
                        oplusJ = generator.transform(explJ.getAxioms());
                        oplusCache.put(explJ, oplusJ);
                    }
                    if (oplusI.containsAll(oplusJ) || oplusJ.containsAll(oplusI)) {
                        toFilter.add(explI);
                        toFilter.add(explJ);
                    }
                }
            }

            preferredLaconicExplanations.addAll(reconstitutedLaconicExpls);
            preferredLaconicExplanations.removeAll(toFilter);

            transmitter.recordMeasurement(info, "number of non-check preferred laconic justifications", preferredLaconicExplanations.size());
            transmitter.recordMeasurement(info, "number of check laconic justifications", toFilter.size());

            for (final Explanation<OWLAxiom> laconicExpl : toFilter) {
                Set<OWLAxiom> sources = getSourceAxioms(laconicExpl, transformation);
                ExplanationGenerator<OWLAxiom> explanationGenerator = delegateFactory.createExplanationGenerator(sources);
                Set<Explanation<OWLAxiom>> regularExpls = explanationGenerator.getExplanations(laconicExpl.getEntailment());
                try {
                    for (Explanation<OWLAxiom> regularExpl : regularExpls) {

                        int size = preferredLaconicExplanations.size();
                        LaconicExplanationGeneratorBasedOnDeltaPlus lacGen = new LaconicExplanationGeneratorBasedOnDeltaPlus(regularExpl.getAxioms(), entailmentCheckerFactory, delegateFactory, new ExplanationProgressMonitor<OWLAxiom>() {

                            private boolean cancelled = false;

                            @Override
                            public void foundExplanation(ExplanationGenerator<OWLAxiom> owlAxiomExplanationGenerator, Explanation<OWLAxiom> owlAxiomExplanation, Set<Explanation<OWLAxiom>> allFoundExplanations) {
                                if (owlAxiomExplanation.equals(laconicExpl)) {
                                    preferredLaconicExplanations.add(laconicExpl);
                                    cancelled = true;
                                }
                            }

                            @Override
                            public boolean isCancelled() {
                                return cancelled;
                            }
                        }, m);
                        lacGen.getExplanations(laconicExpl.getEntailment());


                        if (preferredLaconicExplanations.size() != size) {
                            break;
                        }
                    }
                }
                catch (ExplanationGeneratorInterruptedException e) {
                    System.out.println("Early termination");
                }
            }
            ispreferredCheckTimer.stop();

            final Set<Explanation<OWLAxiom>> progressFound = new HashSet<>();
            for (Explanation<OWLAxiom> expl : preferredLaconicExplanations) {
                progressFound.add(expl);
                progressMonitor.foundExplanation(this, expl, progressFound);
            }


            transmitter.recordTiming(info, "time to check preferred laconic justifications", ispreferredCheckTimer);

            transmitter.recordMeasurement(info, "number of preferred laconic justifications", preferredLaconicExplanations.size());
        }
        finally {
            transmitter.recordMeasurement(info, "number of oplus justifications", numberOfOPlusJustificationsFound);
            transmitter.endTransmission(info);
        }
        return preferredLaconicExplanations;

    }

    private Set<Explanation<OWLAxiom>> getReconstitutedExplanations(OWLDataFactory dataFactory, OPlusGenerator transformation, Set<Explanation<OWLAxiom>> laconicExplanations) {
        Set<Explanation<OWLAxiom>> reconstitutedLaconicExpls = new HashSet<>();
        for (Explanation<OWLAxiom> expl : laconicExplanations) {
            reconstitutedLaconicExpls.addAll(getReconstitutedExplanations(expl, transformation, dataFactory));
        }
        return reconstitutedLaconicExpls;
    }


    private static Set<OWLAxiom> getSourceAxioms(Explanation<OWLAxiom> expl, OPlusGenerator oPlusGenerator) {
        Set<OWLAxiom> result = new HashSet<>();
        for (OWLAxiom ax : expl.getAxioms()) {
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

        OPlusGenerator strictOPlusGenerator = new OPlusGenerator(dataFactory, OPlusSplitting.NONE);
        Set<OWLAxiom> strictOPlus = strictOPlusGenerator.transform(getSourceAxioms(expl, oPlusGenerator));

        for (OWLAxiom explAx : expl.getAxioms()) {
            if (explAx instanceof OWLSubClassOfAxiom) {
                OWLSubClassOfAxiom sca = (OWLSubClassOfAxiom) explAx;
                Set<OWLAxiom> sameSourceAxioms = oPlusGenerator.getSameSourceAxioms(sca, expl.getAxioms());
                if (!sameSourceAxioms.isEmpty()) {
                    Set<OWLClassExpression> superClassConjuncts = new HashSet<>();
                    Set<OWLClassExpression> subClassDisjuncts = new HashSet<>();
                    for (OWLAxiom ax : sameSourceAxioms) {
                        // We only reconstitute SubClassOfAxioms
                        if (ax instanceof OWLSubClassOfAxiom) {
                            OWLSubClassOfAxiom sameSourceSCA = (OWLSubClassOfAxiom) ax;
                            superClassConjuncts.addAll(sameSourceSCA.getSuperClass().asConjunctSet());
                            subClassDisjuncts.addAll(sameSourceSCA.getSubClass().asDisjunctSet());
                            if (oPlusGenerator.hasMultipleSources(ax)) {
                                reconstitutedAxiomSourcesWithMultipleSources.add(ax);
                            }
                        }
                    }
                    subClassDisjuncts.addAll(sca.getSubClass().asDisjunctSet());
                    superClassConjuncts.addAll(sca.getSuperClass().asConjunctSet());
                    OWLSubClassOfAxiom mergedAxiom = createSubClassAxiom(dataFactory, subClassDisjuncts, superClassConjuncts);
                    if (strictOPlus.contains(mergedAxiom)) {
                        reconstitutedAxioms.add(mergedAxiom);
                        oPlusGenerator.addSources(mergedAxiom, oPlusGenerator.getSources(explAx));
                    }
                }
                else {
                    uniqueSourceSubClassAxioms.add(sca);
                }
            }
            else {
                nonSubClassOfAxioms.add(explAx);
            }
        }
        if (reconstitutedAxioms.isEmpty()) {
            return Collections.singleton(expl);
        }
        else {
            Set<OWLAxiom> pool = new HashSet<>();
            pool.addAll(nonSubClassOfAxioms);
            pool.addAll(uniqueSourceSubClassAxioms);
            pool.addAll(reconstitutedAxioms);
            pool.addAll(reconstitutedAxiomSourcesWithMultipleSources);
            if (reconstitutedAxiomSourcesWithMultipleSources.isEmpty()) {
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
        if (subClassDisjuncts.size() == 1) {
            mergedSubClass = subClassDisjuncts.iterator().next();
        }
        else {
            mergedSubClass = dataFactory.getOWLObjectUnionOf(subClassDisjuncts);
        }
        OWLClassExpression mergedSuperClass;
        if (superClassConjuncts.size() == 1) {
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
            System.out.println("\tFound " + allFoundExplanations.size() + " OPlus Justifications");
            numberOfOPlusJustificationsFound = allFoundExplanations.size();
            if(isCancelled()) {
                throw new ExplanationGeneratorInterruptedException();
            }
        }

        @Override
        public boolean isCancelled() {
            return progressMonitor.isCancelled();
        }
    }
}
