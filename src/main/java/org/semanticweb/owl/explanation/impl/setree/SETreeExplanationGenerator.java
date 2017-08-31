package org.semanticweb.owl.explanation.impl.setree;

import org.semanticweb.owl.explanation.api.Explanation;
import org.semanticweb.owl.explanation.api.ExplanationException;
import org.semanticweb.owl.explanation.api.ExplanationGenerator;
import org.semanticweb.owl.explanation.api.NullExplanationProgressMonitor;
import org.semanticweb.owl.explanation.impl.blackbox.*;
import org.semanticweb.owlapi.model.*;
import uk.ac.manchester.cs.owlapi.modularity.ModuleType;
import uk.ac.manchester.cs.owlapi.modularity.SyntacticLocalityModuleExtractor;

import static org.semanticweb.owlapi.util.OWLAPIStreamUtils.add;
import static org.semanticweb.owlapi.util.OWLAPIStreamUtils.asSet;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Author: Matthew Horridge<br>
 * The University of Manchester<br>
 * Bio-Health Informatics Group<br>
 * Date: 19-Jul-2010
 */
public class SETreeExplanationGenerator implements ExplanationGenerator<OWLAxiom> {

    private EntailmentCheckerFactory<OWLAxiom> entailmentCheckerFactory;

    private Set<OWLAxiom> workingAxioms = new HashSet<>();

    private Set<OWLAxiom> module = new HashSet<>();

    private Supplier<OWLOntologyManager> m;

    public SETreeExplanationGenerator(EntailmentCheckerFactory<OWLAxiom> entailmentCheckerFactory, Set<? extends OWLAxiom> workingAxioms, Supplier<OWLOntologyManager> m) {
        this.workingAxioms = new HashSet<>(workingAxioms);
        this.entailmentCheckerFactory = entailmentCheckerFactory;
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
        OWLOntologyManager manager = m.get();
        SyntacticLocalityModuleExtractor extractor = new SyntacticLocalityModuleExtractor(manager, workingAxioms.stream(), ModuleType.STAR);
        module = extractor.extract(asSet(entailment.signature()));
        BlackBoxExplanationGenerator2<OWLAxiom> gen = new BlackBoxExplanationGenerator2<>(module, entailmentCheckerFactory, new StructuralTypePriorityExpansionStrategy<>(null, m), new DivideAndConquerContractionStrategy<OWLAxiom>(), new NullExplanationProgressMonitor<OWLAxiom>(), m);
        Set<Explanation<OWLAxiom>> expls = gen.getExplanations(entailment, 1);
        Explanation<OWLAxiom> expl = expls.iterator().next();
        Set<OWLAxiom> commonAxioms = new HashSet<>();
        for(OWLAxiom ax : expl.getAxioms()) {
            module.remove(ax);
            EntailmentChecker<OWLAxiom> entailmentChecker = entailmentCheckerFactory.createEntailementChecker(entailment);
            if(!entailmentChecker.isEntailed(module)) {
                commonAxioms.add(ax);
            }
            module.add(ax);
        }
        System.out.println("There are " + commonAxioms.size() + " common axioms");
        Set<OWLEntity> commonAxiomsSig = new HashSet<>();
        for(OWLAxiom ax : commonAxioms) {
            System.out.println("\t" + ax);
            add(commonAxiomsSig, ax.signature());
        }

        Set<OWLAxiom> expansionCandidates = new HashSet<>();
        Set<OWLAxiom> directExpansionCandidates = new HashSet<>();
        for(OWLAxiom ax : module) {
            if (!commonAxioms.contains(ax)) {
                ax.signature()
                    .filter(commonAxiomsSig::contains)
                    .forEach(ent -> extendCandidates(entailment, expansionCandidates, directExpansionCandidates, ax));
            }
        }
        System.out.println("There are " + directExpansionCandidates.size() + " direct expansion candidates");
        for(OWLAxiom ax : new TreeSet<>(directExpansionCandidates)) {
            System.out.println("\t" + ax);
        }
        System.out.println("There are " + expansionCandidates.size() + " expansion candidates");
        for(OWLAxiom ax : new TreeSet<>(expansionCandidates)) {
            System.out.println("\t" + ax);
        }



        return Collections.emptySet();
    }

    protected void extendCandidates(OWLAxiom entailment, Set<OWLAxiom> expansionCandidates,
        Set<OWLAxiom> directExpansionCandidates, OWLAxiom ax) {
        expansionCandidates.add(ax);
        entailment.signature()
            .filter(entailmentEnt->ax.containsEntityInSignature(entailmentEnt))
            .forEach(entailmentEnt->directExpansionCandidates.add(ax));
    }





 

}
