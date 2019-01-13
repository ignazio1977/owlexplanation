package org.semanticweb.owl.explanation.impl.blackbox.checker;

import static org.semanticweb.owlapi.util.OWLAPIStreamUtils.add;

import java.util.HashSet;
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
import java.util.Set;
import java.util.function.Supplier;

import org.semanticweb.owl.explanation.api.ExplanationGenerator;
import org.semanticweb.owl.explanation.api.ExplanationGeneratorFactory;
import org.semanticweb.owl.explanation.api.ExplanationProgressMonitor;
import org.semanticweb.owl.explanation.api.NullExplanationProgressMonitor;
import org.semanticweb.owl.explanation.impl.blackbox.BlackBoxExplanationGenerator2;
import org.semanticweb.owl.explanation.impl.blackbox.ExpansionStrategy;
import org.semanticweb.owl.explanation.impl.blackbox.InconsistentOntologyContractionStrategy;
import org.semanticweb.owl.explanation.impl.blackbox.InconsistentOntologyExpansionStrategy;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.reasoner.OWLReasonerConfiguration;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.SimpleConfiguration;

/**
 * Author: Matthew Horridge<br>
 * The University of Manchester<br>
 * Information Management Group<br>
 * Date: 14-May-2009
 */
public class InconsistentOntologyExplanationGeneratorFactory implements ExplanationGeneratorFactory<OWLAxiom> {

	private final ConsistencyEntailmentCheckerFactory consistencyEntailmentCheckerFactory;

	private final InconsistentOntologyContractionStrategy<OWLAxiom> contractionStrategy;

	private final ExpansionStrategy<OWLAxiom> expansionStrategy;

	private final Supplier<OWLOntologyManager> m;

	public InconsistentOntologyExplanationGeneratorFactory(final OWLReasonerFactory reasonerFactory,
			final OWLDataFactory df, final Supplier<OWLOntologyManager> m, final long entailmentCheckingTimeout) {
		this(reasonerFactory, df, m, () -> new SimpleConfiguration(entailmentCheckingTimeout));
	}

	public InconsistentOntologyExplanationGeneratorFactory(final OWLReasonerFactory reasonerFactory,
			final OWLDataFactory df, final Supplier<OWLOntologyManager> m,
			final Supplier<OWLReasonerConfiguration> rC) {
		expansionStrategy = new InconsistentOntologyExpansionStrategy<>();
		contractionStrategy = new InconsistentOntologyContractionStrategy<>();
		this.m = m;
		consistencyEntailmentCheckerFactory = new ConsistencyEntailmentCheckerFactory(reasonerFactory, m, df, rC);
	}

	@Override
	public ExplanationGenerator<OWLAxiom> createExplanationGenerator(final OWLOntology ontology) {
		return createExplanationGenerator(ontology, new NullExplanationProgressMonitor<OWLAxiom>());
	}

	@Override
	public ExplanationGenerator<OWLAxiom> createExplanationGenerator(final OWLOntology ontology,
			final ExplanationProgressMonitor<OWLAxiom> progressMonitor) {
		final Set<OWLAxiom> axioms = new HashSet<>(ontology.getLogicalAxiomCount(Imports.INCLUDED));
		ontology.importsClosure().forEach(ont -> add(axioms, ont.logicalAxioms()));
		return new BlackBoxExplanationGenerator2<>(axioms, consistencyEntailmentCheckerFactory, expansionStrategy,
				contractionStrategy, progressMonitor, m);
	}

	@Override
	public ExplanationGenerator<OWLAxiom> createExplanationGenerator(final Set<? extends OWLAxiom> axioms) {
		return createExplanationGenerator(axioms, new NullExplanationProgressMonitor<OWLAxiom>());
	}

	@Override
	public ExplanationGenerator<OWLAxiom> createExplanationGenerator(final Set<? extends OWLAxiom> axioms,
			final ExplanationProgressMonitor<OWLAxiom> progressMonitor) {
		return new BlackBoxExplanationGenerator2<>(axioms, consistencyEntailmentCheckerFactory, expansionStrategy,
				contractionStrategy, progressMonitor, m);
	}
}
