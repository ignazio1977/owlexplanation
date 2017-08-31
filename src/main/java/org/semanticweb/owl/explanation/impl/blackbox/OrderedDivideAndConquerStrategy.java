package org.semanticweb.owl.explanation.impl.blackbox;

import org.semanticweb.owl.explanation.api.ExplanationProgressMonitor;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.util.AxiomSubjectProviderEx;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Author: Matthew Horridge<br>
 * The University of Manchester<br>
 * Bio-Health Informatics Group<br>
 * Date: 13/01/2011
 */
public class OrderedDivideAndConquerStrategy<E> implements ContractionStrategy<E> {

    private DivideAndConquerContractionStrategy<E> delegate = new DivideAndConquerContractionStrategy<>();

    private int count = 0;

    @Override
    public Set<OWLAxiom> doPruning(Set<OWLAxiom> axioms, EntailmentChecker<E> checker, ExplanationProgressMonitor<?> progressMonitor) {
        count = 0;
        LinkedHashSet<OWLAxiom> orderedAxioms = new LinkedHashSet<>();
        Map<OWLObject, Set<OWLAxiom>> axiomsBySubject = new HashMap<>();
        for(OWLAxiom ax : axioms) {
            OWLObject object = AxiomSubjectProviderEx.getSubject(ax);
            Set<OWLAxiom> axiomsSet = axiomsBySubject.get(object);
            axiomsBySubject.put(object, axiomsSet);
        }
        return delegate.doPruning(orderedAxioms, checker, progressMonitor);
    }

    @Override
    public int getNumberOfSteps() {
        return delegate.getNumberOfSteps();
    }
}
