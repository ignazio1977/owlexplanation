/*
 * Copyright (C) 2009, University of Manchester
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
package org.semanticweb.owl.explanation.impl.blackbox.checker;

import org.semanticweb.owl.explanation.impl.blackbox.EntailmentCheckerFactory;

import java.util.function.Supplier;

import org.semanticweb.owl.explanation.impl.blackbox.EntailmentChecker;
import org.semanticweb.owlapi.reasoner.OWLReasonerConfiguration;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.SimpleConfiguration;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntologyManager;

/**
 * Author: Matthew Horridge<br>
 * The University of Manchester<br>
 * Information Management Group<br>
 * Date: 01-May-2009
 */
public class ConsistencyEntailmentCheckerFactory implements EntailmentCheckerFactory<OWLAxiom> {

    private final OWLReasonerFactory reasonerFactory;

    private final OWLDataFactory df;

    private final Supplier<OWLOntologyManager> m;

    private final Supplier<OWLReasonerConfiguration> rC;

    public ConsistencyEntailmentCheckerFactory(OWLReasonerFactory reasonerFactory, Supplier<OWLOntologyManager> m, OWLDataFactory df, long timeout) {
        this(reasonerFactory, m, df, () -> new SimpleConfiguration(timeout));
    }
    public ConsistencyEntailmentCheckerFactory(OWLReasonerFactory reasonerFactory,
        Supplier<OWLOntologyManager> m, OWLDataFactory df, Supplier<OWLReasonerConfiguration> rC) {
        this.reasonerFactory = reasonerFactory;
        this.df = df;
        this.m = m;
        this.rC = rC;
    }

    @Override
    public EntailmentChecker<OWLAxiom> createEntailementChecker(OWLAxiom entailment) {
        return new ConsistencyEntailmentChecker(reasonerFactory, m, df, rC);
    }
}
