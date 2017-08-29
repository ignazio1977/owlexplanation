package org.semanticweb.owl.explanation.impl.blackbox;

import org.semanticweb.owl.explanation.api.ExplanationProgressMonitor;
import org.semanticweb.owlapi.model.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
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
import java.util.function.Supplier;


/**
 * Author: Matthew Horridge<br> The University Of Manchester<br> Information Management Group<br> Date:
 * 03-Sep-2008<br><br>
 */
public class StructuralExpansionStrategy implements ExpansionStrategy {

    private int count = 0;
    private Supplier<OWLOntologyManager> m;

    public StructuralExpansionStrategy(Supplier<OWLOntologyManager> m) {
        this.m = m;
    }

    @Override
    public Set<OWLAxiom> doExpansion(final Set<OWLAxiom> axioms, EntailmentChecker checker, ExplanationProgressMonitor<?> progressMonitor) {

        count = 0;
        try {
            OWLOntology ont = m.get().createOntology(axioms);

            Set<OWLEntity> entailmentSignature = new HashSet<>();
            entailmentSignature.addAll(checker.getEntailmentSignature());


            Set<OWLAxiom> expansion = new HashSet<>();
            for (OWLEntity ent : entailmentSignature) {
                expansion.addAll(ont.getReferencingAxioms(ent));
            }

            while (true) {
                count++;
                if (checker.isEntailed(expansion)) {
                    return expansion;
                }
                else if(expansion.equals(axioms)) {
                    return Collections.emptySet();
                }

                // Add some more
                for (OWLAxiom ax : new HashSet<>(expansion)) {
                    for (OWLEntity ent : ax.getSignature()) {
                        Set<OWLAxiom> owlAxioms = ont.getReferencingAxioms(ent);
                        expansion.addAll(owlAxioms);
                    }
                }
            }
        }
        catch (OWLOntologyCreationException e) {
            throw new OWLRuntimeException(e);
        }
    }

    @Override
    public int getNumberOfSteps() {
        return count;
    }
}
