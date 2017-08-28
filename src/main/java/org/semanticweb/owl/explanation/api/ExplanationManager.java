package org.semanticweb.owl.explanation.api;

import org.semanticweb.owl.explanation.impl.blackbox.checker.InconsistentOntologyExplanationGeneratorFactory;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owl.explanation.impl.blackbox.checker.BlackBoxExplanationGeneratorFactory;
import org.semanticweb.owl.explanation.impl.blackbox.checker.SatisfiabilityEntailmentCheckerFactory;

import java.util.function.Supplier;

import org.semanticweb.owl.explanation.impl.blackbox.Configuration;
import org.semanticweb.owl.explanation.impl.blackbox.EntailmentCheckerFactory;
import org.semanticweb.owl.explanation.impl.laconic.LaconicExplanationGeneratorFactory;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

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
 * Author: Matthew Horridge<br> The University of Manchester<br> Information Management Group<br>
 * Date: 19-Nov-2008
 *
 * An explanation utility class that creates the default explanation generators.
 */
public class ExplanationManager {

    /**
     * Creates an explanation generator factory that will produce explanation generators that generate explanations
     * as to why an checker is entailed by a set of axioms.
     * @param reasonerFactory A reasoner factory that can be used for creating new reasoners if necessary
     * @return An explanation generatory factory that creates explanation generators for entailed axioms
     */
    public static ExplanationGeneratorFactory<OWLAxiom> createExplanationGeneratorFactory(OWLReasonerFactory reasonerFactory, Supplier<OWLOntologyManager> m) {
        return createExplanationGeneratorFactory(reasonerFactory, null, m);
    }

    /**
     * Creates an explanation generator factory that will produce explanation generators that generate explanations
     * as to why an checker is entailed by a set of axioms.
     * @param reasonerFactory A reasoner factory that can be used for creating new reasoners if necessary
     * @param progressMonitor An explanation progress monitor
     * @return An explanation generatory factory that creates explanation generators for entailed axioms
     *
     */
    public static ExplanationGeneratorFactory<OWLAxiom> createExplanationGeneratorFactory(OWLReasonerFactory reasonerFactory, ExplanationProgressMonitor<OWLAxiom> progressMonitor, Supplier<OWLOntologyManager> m) {
        EntailmentCheckerFactory<OWLAxiom> checker = new SatisfiabilityEntailmentCheckerFactory(reasonerFactory, m);
        Configuration<OWLAxiom> config = new Configuration<OWLAxiom>(checker, progressMonitor, m);
        return new BlackBoxExplanationGeneratorFactory<OWLAxiom>(config);
    }


    public static ExplanationGeneratorFactory<OWLAxiom> createLaconicExplanationGeneratorFactory(OWLReasonerFactory reasonerFactory, Supplier<OWLOntologyManager> m) {
        return createLaconicExplanationGeneratorFactory(reasonerFactory, null, m);
    }

    public static ExplanationGeneratorFactory<OWLAxiom> createLaconicExplanationGeneratorFactory(OWLReasonerFactory reasonerFactory, ExplanationProgressMonitor<OWLAxiom> progressMonitor, Supplier<OWLOntologyManager> m) {
        return new LaconicExplanationGeneratorFactory<OWLAxiom>(createExplanationGeneratorFactory(reasonerFactory, m), m);
    }

    public static <E> ExplanationGeneratorFactory<E> createExplanationGeneratorFactory(EntailmentCheckerFactory<E> entailmentCheckerFactory, Supplier<OWLOntologyManager> m) {
        Configuration<E> config = new Configuration<E>(entailmentCheckerFactory, m);
        return new BlackBoxExplanationGeneratorFactory<E>(config);
    }

    public static <E> ExplanationGeneratorFactory<E> createLaconicExplanationGeneratorFactory(EntailmentCheckerFactory<E> entailmentCheckerFactory, Supplier<OWLOntologyManager> m) {
        return new LaconicExplanationGeneratorFactory<E>(createExplanationGeneratorFactory(entailmentCheckerFactory, m), m);
    }
}
