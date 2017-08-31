package org.semanticweb.owl.explanation.impl.laconic;

import org.semanticweb.owl.explanation.api.*;
import org.semanticweb.owl.explanation.impl.blackbox.EntailmentCheckerFactory;
import org.semanticweb.owl.explanation.impl.util.AxiomTransformation;
import org.semanticweb.owl.explanation.impl.util.DeltaPlusTransformation;
import org.semanticweb.owl.explanation.impl.util.DeltaTransformationUnfolder;
import org.semanticweb.owlapi.model.*;
import uk.ac.manchester.cs.owlapi.modularity.ModuleType;
import uk.ac.manchester.cs.owlapi.modularity.SyntacticLocalityModuleExtractor;

import static org.semanticweb.owlapi.util.OWLAPIStreamUtils.add;
import static org.semanticweb.owlapi.util.OWLAPIStreamUtils.asSet;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Author: Matthew Horridge<br>
 * The University of Manchester<br>
 * Bio-Health Informatics Group<br>
 * Date: 11/04/2011
 */
public class LaconicExplanationGeneratorBasedOnDeltaPlus implements ExplanationGenerator<OWLAxiom> {

    private Set<OWLAxiom> inputAxioms;

    private EntailmentCheckerFactory<OWLAxiom> entailmentCheckerFactory;

    private ExplanationGeneratorFactory<OWLAxiom> delegateFactory;

    private ExplanationProgressMonitor<OWLAxiom> progressMonitor;

    private Supplier<OWLOntologyManager> m;

    public LaconicExplanationGeneratorBasedOnDeltaPlus(Set<? extends OWLAxiom> inputAxioms, EntailmentCheckerFactory<OWLAxiom> entailmentCheckerFactory, ExplanationGeneratorFactory<OWLAxiom> delegateFactory, ExplanationProgressMonitor<OWLAxiom> progressMonitor, Supplier<OWLOntologyManager> m) {
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
        final Set<OWLEntity> signature = new HashSet<>();
        for(OWLAxiom ax : inputAxioms) {
            add(signature, ax.signature());
        }
        OWLOntologyManager man = m.get();
        final OWLDataFactory dataFactory = man.getOWLDataFactory();
        AxiomTransformation transformation = new DeltaPlusTransformation(dataFactory);
        SyntacticLocalityModuleExtractor extractor = new SyntacticLocalityModuleExtractor(man, inputAxioms.stream(), ModuleType.STAR);
        Set<OWLAxiom> moduleAxioms = extractor.extract(asSet(entailment.signature()));

        Set<OWLAxiom> flattenedAxioms = transformation.transform(moduleAxioms);
        ExplanationGenerator<OWLAxiom> gen = delegateFactory.createExplanationGenerator(flattenedAxioms);
        Set<Explanation<OWLAxiom>> expls = gen.getExplanations(entailment);

        IsLaconicChecker checker = new IsLaconicChecker(dataFactory, entailmentCheckerFactory, LaconicCheckerMode.EARLY_TERMINATING);
        Set<Explanation<OWLAxiom>> laconicExplanations = new HashSet<>();
        Set<Explanation<OWLAxiom>> nonLaconicExplanations = new HashSet<>();
        for(Explanation<OWLAxiom> expl : expls) {
            DeltaTransformationUnfolder unfolder = new DeltaTransformationUnfolder(dataFactory);
            Set<OWLAxiom> unfoldedAxioms = unfolder.getUnfolded(expl.getAxioms(), signature);
            Explanation<OWLAxiom> unfoldedExpl = new Explanation<>(entailment, unfoldedAxioms);
            if(checker.isLaconic(unfoldedExpl)) {
                boolean added = laconicExplanations.add(unfoldedExpl);
                if (added) {
                    progressMonitor.foundExplanation(this, unfoldedExpl, new HashSet<>(laconicExplanations));
                }
            }
            else {
                nonLaconicExplanations.add(unfoldedExpl);
            }
        }
        if(laconicExplanations.isEmpty()) {
            System.out.println("NOT-FOUND-ANY!");
            for(Explanation<OWLAxiom> nonLaconic : nonLaconicExplanations) {
                System.out.println("NON-LACONIC:");
                System.out.println(nonLaconic);
            }
        }
        return laconicExplanations;

    }




}
