package org.semanticweb.owl.explanation.impl.blackbox.checker;

import java.util.function.Supplier;

import org.semanticweb.owl.explanation.impl.blackbox.EntailmentChecker;
import org.semanticweb.owl.explanation.impl.blackbox.EntailmentCheckerFactory;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

/**
 * Author: Matthew Horridge<br>
 * Stanford University<br>
 * Bio-Medical Informatics Research Group<br>
 * Date: 14/08/2012
 */
public class PatternBasedConsistencyEntailmentCheckerFactory implements EntailmentCheckerFactory<OWLAxiom> {

    private OWLReasonerFactory rf;

    private long timeout;

    private Supplier<OWLOntologyManager> m;

    public PatternBasedConsistencyEntailmentCheckerFactory(OWLReasonerFactory rf, Supplier<OWLOntologyManager> m, long timeout) {
        this.rf = rf;
        this.timeout = timeout;
        this.m = m;
    }

    @Override
    public EntailmentChecker<OWLAxiom> createEntailementChecker(OWLAxiom entailment) {
        return new PatternBasedConsistencyEntailmentChecker(rf, m, m.get().getOWLDataFactory(), timeout);
    }
}
