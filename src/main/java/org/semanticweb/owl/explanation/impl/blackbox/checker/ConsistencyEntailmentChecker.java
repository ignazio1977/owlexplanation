package org.semanticweb.owl.explanation.impl.blackbox.checker;

import org.semanticweb.owl.explanation.telemetry.DefaultTelemetryInfo;
import org.semanticweb.owl.explanation.telemetry.TelemetryInfo;
import org.semanticweb.owl.explanation.telemetry.TelemetryTimer;
import org.semanticweb.owl.explanation.telemetry.TelemetryTransmitter;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerConfiguration;
import org.semanticweb.owlapi.reasoner.SimpleConfiguration;

import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;

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
 * Author: Matthew Horridge<br>
 * The University Of Manchester<br>
 * Information Management Group<br>
 * Date: 25-Sep-2008<br>
 * <br>
 */
public class ConsistencyEntailmentChecker
		implements org.semanticweb.owl.explanation.impl.blackbox.EntailmentChecker<OWLAxiom> {

	private OWLAxiom entailment;

	private OWLReasonerFactory reasonerFactory;

	private int counter;

	private boolean consistent = false;

	private Supplier<OWLOntologyManager> m;

	private final Supplier<OWLReasonerConfiguration> configurationSupplier;

	public ConsistencyEntailmentChecker(final OWLReasonerFactory reasonerFactory, final Supplier<OWLOntologyManager> man, final OWLDataFactory df, final long timeout) {
       this(reasonerFactory, man, df, () -> new SimpleConfiguration(timeout));
    }

	public ConsistencyEntailmentChecker(final OWLReasonerFactory reasonerFactory,
			final Supplier<OWLOntologyManager> man, final OWLDataFactory df,
			final Supplier<OWLReasonerConfiguration> rC) {
		configurationSupplier = rC;
		this.reasonerFactory = reasonerFactory;
		m = man;
		entailment = df.getOWLSubClassOfAxiom(df.getOWLThing(), df.getOWLNothing());
	}

	@Override
	public int getCounter() {
		return counter;
	}

	@Override
	public void resetCounter() {
		counter = 0;
	}

	@Override
	public OWLAxiom getEntailment() {
		return entailment;
	}

	@Override
	public Set<OWLAxiom> getModule(final Set<OWLAxiom> axioms) {
		return axioms;
	}

	@Override
	public Set<OWLEntity> getEntailmentSignature() {
		return Collections.emptySet();
	}

	@Override
	public Set<OWLEntity> getSeedSignature() {
		return Collections.emptySet();
	}

	@Override
	public boolean isEntailed(final Set<OWLAxiom> axiom) {

		final TelemetryTimer timer = new TelemetryTimer();
		final TelemetryTimer loadTimer = new TelemetryTimer();
		final TelemetryTimer checkTimer = new TelemetryTimer();
		final TelemetryInfo info = new DefaultTelemetryInfo("entailmentcheck", timer, loadTimer, checkTimer);
		final TelemetryTransmitter transmitter = TelemetryTransmitter.getTransmitter();
		transmitter.beginTransmission(info);
		try {
//        System.out.print("Checking entailment....");
			transmitter.recordMeasurement(info, "input size", axiom.size());
			counter++;
			timer.start();
			final OWLOntology ont = m.get().createOntology(axiom);
			final OWLReasonerConfiguration config = configurationSupplier.get();
			timer.start();
			loadTimer.start();
			final OWLReasoner r = reasonerFactory.createReasoner(ont, config);
			loadTimer.stop();
			transmitter.recordTiming(info, "load time", timer);
			checkTimer.start();
			consistent = r.isConsistent();
			checkTimer.stop();
			timer.stop();
			transmitter.recordTiming(info, "check time", checkTimer);
			transmitter.recordTiming(info, "time", timer);
			r.dispose();

//        System.out.println(" done!");
			return !consistent;
		} catch (final OWLOntologyCreationException e) {
			throw new OWLRuntimeException(e);
		} finally {
			transmitter.endTransmission(info);
		}
	}

	@Override
	public String getModularisationTypeDescription() {
		return "none";
	}

	@Override
	public boolean isUseModularisation() {
		return false;
	}

	@Override
	public Set<OWLAxiom> getEntailingAxioms(final Set<OWLAxiom> axioms) {
		return null;
	}
}
