package org.semanticweb.owl.explanation.impl.laconic;

import org.semanticweb.owl.explanation.api.*;
import org.semanticweb.owl.explanation.impl.blackbox.EntailmentCheckerFactory;
import org.semanticweb.owl.explanation.telemetry.DefaultTelemetryInfo;
import org.semanticweb.owl.explanation.telemetry.TelemetryInfo;
import org.semanticweb.owl.explanation.telemetry.TelemetryTimer;
import org.semanticweb.owl.explanation.telemetry.TelemetryTransmitter;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;

import java.util.*;
import java.util.function.Supplier;

/**
 * Author: Matthew Horridge<br>
 * The University of Manchester<br>
 * Bio-Health Informatics Group<br>
 * Date: 06/05/2011
 */
public class LaconicExplanationGeneratorBasedOnIncrementalOPlusWithDeltaPlusFiltering implements ExplanationGenerator<OWLAxiom> {

    private Set<OWLAxiom> inputAxioms;

    private ExplanationGeneratorFactory<OWLAxiom> delegate;

    private EntailmentCheckerFactory<OWLAxiom> entailmentCheckerFactory;

    private OWLDataFactory dataFactory;

    protected ExplanationProgressMonitor<OWLAxiom> progressMonitor = new NullExplanationProgressMonitor<>();

    protected int numberOfOPlusJustificationsFound;

    private Supplier<OWLOntologyManager> m;

    public LaconicExplanationGeneratorBasedOnIncrementalOPlusWithDeltaPlusFiltering(Set<OWLAxiom> inputAxioms, EntailmentCheckerFactory<OWLAxiom> cf, ExplanationGeneratorFactory<OWLAxiom> delegate, ExplanationProgressMonitor<OWLAxiom> progressMonitor, OWLDataFactory df, Supplier<OWLOntologyManager> m) {
        this.dataFactory=df;
        this.inputAxioms = new HashSet<>(inputAxioms.size());
        for (OWLAxiom in : inputAxioms) {
            this.inputAxioms.add(in.getAxiomWithoutAnnotations());
        }
        this.delegate = delegate;
        this.entailmentCheckerFactory = cf;
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

        final Set<Explanation<OWLAxiom>> preferredLaconicExplanations = new HashSet<>();

        TelemetryTimer computeOplusTimer = new TelemetryTimer();
        TelemetryTimer computeOplusJustificationsTimer = new TelemetryTimer();
        TelemetryTimer islaconicTimer = new TelemetryTimer();
        TelemetryTimer reconstituteTimer = new TelemetryTimer();
        TelemetryTimer ispreferredCheckTimer = new TelemetryTimer();
        TelemetryTimer regularJustificationsTimer = new TelemetryTimer();

        TelemetryInfo info = new DefaultTelemetryInfo(getClass().getSimpleName(), computeOplusTimer, computeOplusJustificationsTimer, islaconicTimer, reconstituteTimer, ispreferredCheckTimer);
        TelemetryTransmitter transmitter = TelemetryTransmitter.getTransmitter();

        Set<Explanation<OWLAxiom>> regularJustifications = new HashSet<>();

        try {
            transmitter.beginTransmission(info);

            Set<Explanation<OWLAxiom>> computedExplanations = new HashSet<>();
            Set<Explanation<OWLAxiom>> tmp = new HashSet<>();
            OPlusGenerator transformation = new OPlusGenerator(dataFactory, OPlusSplitting.TOP_LEVEL);


            computeOplusJustificationsTimer.start();
            int rounds = 0;

            boolean foundNew;
            boolean foundExternal;
            boolean foundExternallyMaskedJustifications = false;

            do {
                if (progressMonitor.isCancelled()) {
                    throw new ExplanationGeneratorInterruptedException();
                }
                rounds++;

                Set<OWLAxiom> lastRoundAxioms = new HashSet<>();
                for (Explanation<OWLAxiom> expl : computedExplanations) {
                    for(OWLAxiom ax : expl.getAxioms()) {
                        if(inputAxioms.contains(ax)) {
                            lastRoundAxioms.add(ax);
                        }
                    }
                }

                Set<OWLAxiom> workingAxioms = new HashSet<>(inputAxioms);
                Set<OWLAxiom> oplusedAxioms = transformation.transform(lastRoundAxioms);
                for (OWLAxiom ax : lastRoundAxioms) {
                    if (!oplusedAxioms.contains(ax)) {
                        workingAxioms.remove(ax);
                    }
                }
                workingAxioms.addAll(oplusedAxioms);
                ExplanationGenerator<OWLAxiom> gen = delegate.createExplanationGenerator(workingAxioms, new MediatingProgresssMonitor());
                if (rounds == 1) {
                    regularJustificationsTimer.start();
                }
                tmp = gen.getExplanations(entailment);
                if (rounds == 1) {
                    regularJustificationsTimer.stop();
                }
                if(rounds == 1) {
                    System.out.println("Found " + tmp.size() + " regular justifications");
                    transmitter.recordMeasurement(info, "number of regular justifications", tmp.size());
                    regularJustifications.addAll(tmp);
                    transmitter.recordTiming(info, "time to find regular justifications", regularJustificationsTimer);
                }
                foundNew = computedExplanations.addAll(tmp);
                foundExternal = false;
                foundExternallyMaskedJustifications = false;
                if(foundNew) {
                    for(Explanation<OWLAxiom> expl : computedExplanations) {
                        for(OWLAxiom ax : expl.getAxioms()) {
                            if(inputAxioms.contains(ax) && !lastRoundAxioms.contains(ax)) {
                                foundExternal = true;
                                if(rounds > 1) {
                                    foundExternallyMaskedJustifications = true;
                                }
                                break;
                            }
                        }
                        if(foundExternal) {
                            break;
                        }
                    }
                }
                if(foundNew && !foundExternal) {
                    System.out.println("Early termination.");
                    transmitter.recordMeasurement(info, "early round termination", true);
                    transmitter.recordMeasurement(info, "found externally masked justifications", foundExternallyMaskedJustifications);
                }
            } while (foundNew && foundExternal);


            transmitter.recordMeasurement(info, "number of compute oplus justifications rounds", rounds);
            computeOplusJustificationsTimer.stop();
            transmitter.recordTiming(info, "time to compute oplus justifications", computeOplusJustificationsTimer);

            Set<Explanation<OWLAxiom>> oplusExpls = new HashSet<>(computedExplanations);
            IsLaconicChecker checker = new IsLaconicChecker(dataFactory, entailmentCheckerFactory, LaconicCheckerMode.EARLY_TERMINATING);
            Set<Explanation<OWLAxiom>> laconicExplanations = new HashSet<>();

            islaconicTimer.start();
            for (Explanation<OWLAxiom> expl : oplusExpls) {
                if (progressMonitor.isCancelled()) {
                    throw new ExplanationGeneratorInterruptedException();
                }
                if (checker.isLaconic(expl)) {
                    laconicExplanations.add(expl);
                }
            }
            islaconicTimer.stop();
            transmitter.recordTiming(info, "time to check laconic oplus justifications", islaconicTimer);

            transmitter.recordMeasurement(info, "number of laconic oplus justifications", laconicExplanations.size());

            if (laconicExplanations.isEmpty()) {
                System.out.println("I didn't find any oplus explanations that were laconic!!!");
                System.out.println("Here's what I found:");
                for (Explanation<OWLAxiom> expl : oplusExpls) {
                    System.out.println(expl);
                }
            }

            reconstituteTimer.start();
            Set<Explanation<OWLAxiom>> reconstitutedLaconicExpls = getReconstitutedExplanations(transformation, laconicExplanations);
            reconstituteTimer.stop();
            transmitter.recordTiming(info, "time to reconstitute justifications", reconstituteTimer);

            transmitter.recordMeasurement(info, "number of reconstituted justifications", reconstitutedLaconicExpls.size());


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
                    if (!oplusI.equals(oplusJ) && (oplusI.containsAll(oplusJ) || oplusJ.containsAll(oplusI))) {
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
                ExplanationGenerator<OWLAxiom> explanationGenerator = delegate.createExplanationGenerator(sources);
                Set<Explanation<OWLAxiom>> regularExpls = explanationGenerator.getExplanations(laconicExpl.getEntailment());
                try {
                    for (Explanation<OWLAxiom> regularExpl : regularExpls) {

                        int size = preferredLaconicExplanations.size();
                        LaconicExplanationGeneratorBasedOnDeltaPlus lacGen = new LaconicExplanationGeneratorBasedOnDeltaPlus(regularExpl.getAxioms(), entailmentCheckerFactory, delegate, new ExplanationProgressMonitor<OWLAxiom>() {

                            private boolean cancelled = false;

                            @Override
                            public void foundExplanation(ExplanationGenerator<OWLAxiom> owlAxiomExplanationGenerator, Explanation<OWLAxiom> owlAxiomExplanation, Set<Explanation<OWLAxiom>> allFoundExplanations) {
                                System.out.println(owlAxiomExplanation);
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
        recordJustifications(regularJustifications, "regularjustifications");
        return preferredLaconicExplanations;

    }

    private void recordJustifications(Set<Explanation<OWLAxiom>> expls, String name) {
        TelemetryInfo info = new DefaultTelemetryInfo(name);
        TelemetryTransmitter transmitter = TelemetryTransmitter.getTransmitter();
        try {
            transmitter.beginTransmission(info);
            for(Explanation<OWLAxiom> expl : expls) {
                transmitter.recordObject(info, "justification", "", new ExplanationTelemetryWrapper(expl, m));
            }
        }
        finally {
            transmitter.endTransmission(info);
        }

    }

    private Set<Explanation<OWLAxiom>> getReconstitutedExplanations(OPlusGenerator transformation, Set<Explanation<OWLAxiom>> laconicExplanations) {
        Set<Explanation<OWLAxiom>> reconstitutedLaconicExpls = new HashSet<>();
        for (Explanation<OWLAxiom> expl : laconicExplanations) {
            reconstitutedLaconicExpls.addAll(getReconstitutedExplanations(expl, transformation));
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

    private Set<Explanation<OWLAxiom>> getReconstitutedExplanations(Explanation<OWLAxiom> expl, OPlusGenerator oPlusGenerator) {
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
                    OWLSubClassOfAxiom mergedAxiom = createSubClassAxiom(subClassDisjuncts, superClassConjuncts);
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
                ExplanationGenerator<OWLAxiom> expGen = delegate.createExplanationGenerator(pool);
                return expGen.getExplanations(expl.getEntailment());
            }
        }
    }

    private OWLSubClassOfAxiom createSubClassAxiom(Set<OWLClassExpression> subClassDisjuncts, Set<OWLClassExpression> superClassConjuncts) {
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
            if (isCancelled()) {
                throw new ExplanationGeneratorInterruptedException();
            }
        }

        @Override
        public boolean isCancelled() {
            return progressMonitor.isCancelled();
        }
    }

}
