package org.semanticweb.owl.explanation.impl.blackbox.checker;

import java.util.function.Supplier;

import org.semanticweb.owl.explanation.impl.blackbox.Configuration;
import org.semanticweb.owl.explanation.impl.blackbox.ContractionStrategy;
import org.semanticweb.owl.explanation.impl.blackbox.ExpansionStrategy;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.model.OWLAxiom;
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
import org.semanticweb.owlapi.model.OWLOntologyManager;


/**
 * Author: Matthew Horridge<br> The University Of Manchester<br> Information Management Group<br> Date:
 * 03-Sep-2008<br><br>
 *
 * A default black box configuration that uses an entailment checker that reduced entailment checking to
 * satisfiability checking using an OWLReasoner.
 */
public class DefaultBlackBoxConfiguration extends Configuration<OWLAxiom> {


    public DefaultBlackBoxConfiguration(OWLReasonerFactory reasonerFactory, Supplier<OWLOntologyManager> m) {
        super(new SatisfiabilityEntailmentCheckerFactory(reasonerFactory, m), m);
    }

    public DefaultBlackBoxConfiguration(OWLReasonerFactory reasonerFactory,
                                      ExpansionStrategy<OWLAxiom> expansionStrategy,
                                      ContractionStrategy<OWLAxiom> contractionStrategy,
                                      Supplier<OWLOntologyManager> m) {
        super(new SatisfiabilityEntailmentCheckerFactory(reasonerFactory, m), expansionStrategy, contractionStrategy, m);
    }
}
