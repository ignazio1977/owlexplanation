package org.semanticweb.owl.explanation.impl.rootderived;

import org.semanticweb.owl.explanation.api.*;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import static org.semanticweb.owlapi.util.OWLAPIStreamUtils.add;

import java.util.*;
import java.util.function.Supplier;
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

/**
 * Author: Matthew Horridge<br>
 * The University of Manchester<br>
 * Information Management Group<br>
 * Date: 20-Oct-2009
 */
public class CompleteRootDerivedReasoner implements RootDerivedReasoner {

    private OWLOntologyManager manager;

    private OWLReasoner baseReasoner;

    private OWLReasonerFactory reasonerFactory;

    private Map<OWLClass, Set<Explanation<OWLAxiom>>> cls2JustificationMap;

    private Set<OWLClass> roots = new HashSet<>();

    private Supplier<OWLOntologyManager> m;


    public CompleteRootDerivedReasoner(OWLOntologyManager manager, OWLReasoner baseReasoner, OWLReasonerFactory reasonerFactory, Supplier<OWLOntologyManager> m) {
        this.manager = manager;
        this.baseReasoner = baseReasoner;
        this.reasonerFactory = reasonerFactory;
        this.m = m;
    }

    /**
     * Gets the root unsatisfiable classes.
     * @return A set of classes that represent the root unsatisfiable classes
     */
    @Override
    public Set<OWLClass> getRootUnsatisfiableClasses() throws ExplanationException {
        StructuralRootDerivedReasoner srd = new StructuralRootDerivedReasoner(manager, baseReasoner);
        Set<OWLClass> estimatedRoots = srd.getRootUnsatisfiableClasses();
        cls2JustificationMap = new HashMap<>();
        Set<OWLAxiom> allAxioms = new HashSet<>();
        baseReasoner.getRootOntology().importsClosure()
            .forEach(ont -> add(allAxioms, ont.logicalAxioms()));

        for (OWLClass cls : estimatedRoots) {
            cls2JustificationMap.put(cls, new HashSet<Explanation<OWLAxiom>>());
            System.out.println("POTENTIAL ROOT: " + cls);
        }
        System.out.println("Finding real roots from " + estimatedRoots.size() + " estimated roots");

        int done = 0;
        roots.addAll(estimatedRoots);
        for (final OWLClass estimatedRoot : estimatedRoots) {
            ExplanationGeneratorFactory<OWLAxiom> genFac = ExplanationManager.createExplanationGeneratorFactory(reasonerFactory, m);
            ExplanationGenerator<OWLAxiom> gen = genFac.createExplanationGenerator(allAxioms);
            OWLDataFactory df = manager.getOWLDataFactory();
            Set<Explanation<OWLAxiom>> expls = gen.getExplanations(df.getOWLSubClassOfAxiom(estimatedRoot, df.getOWLNothing()));
            cls2JustificationMap.get(estimatedRoot).addAll(expls);
            done++;
            System.out.println("Done " + done);
        }
        for(OWLClass clsA : estimatedRoots) {
            for(OWLClass clsB : estimatedRoots) {
                if (!clsA.equals(clsB)) {
                    Set<Explanation<OWLAxiom>> clsAExpls = cls2JustificationMap.get(clsA);
                    Set<Explanation<OWLAxiom>> clsBExpls = cls2JustificationMap.get(clsB);
                    boolean clsARootForClsB = false;
                    boolean clsBRootForClsA = false;
                    // Be careful of cyclic dependencies!
                    for(Explanation<OWLAxiom> clsAExpl : clsAExpls) {
                        for(Explanation<OWLAxiom> clsBExpl : clsBExpls) {
                            if(isRootFor(clsAExpl, clsBExpl)) {
                                // A is a root of B
                                clsARootForClsB = true;
                            }
                            else if(isRootFor(clsBExpl, clsAExpl)) {
                                // B is a root of A
                                clsBRootForClsA = true;
                            }
                        }
                    }
                    if (!clsARootForClsB || !clsBRootForClsA) {
                        if(clsARootForClsB) {
                            roots.remove(clsB);
                        }
                        else if(clsBRootForClsA) {
                            roots.remove(clsA);
                        }
                    }
                }
            }
        }
        return roots;
    }

    private static boolean isRootFor(Explanation<OWLAxiom> explA, Explanation<OWLAxiom> explB) {
        return explB.getAxioms().containsAll(explA.getAxioms()) && !explA.getAxioms().equals(explB.getAxioms());
    }


    @Override
    public Set<OWLClass> getDependentChildClasses(OWLClass cls) {
        return Collections.emptySet();
    }

    @Override
    public Set<OWLClass> getDependentDescendantClasses(OWLClass cls) {
        return Collections.emptySet();
    }
}
