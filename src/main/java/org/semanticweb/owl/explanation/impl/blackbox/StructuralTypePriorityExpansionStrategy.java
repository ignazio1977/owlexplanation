package org.semanticweb.owl.explanation.impl.blackbox;

import org.semanticweb.owl.explanation.api.ExplanationGeneratorInterruptedException;
import org.semanticweb.owl.explanation.api.ExplanationProgressMonitor;
import org.semanticweb.owlapi.model.*;

import static org.semanticweb.owlapi.util.OWLAPIStreamUtils.add;
import static org.semanticweb.owlapi.util.OWLAPIStreamUtils.asList;

import java.util.ArrayList;
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
import java.util.stream.Stream;


/**
 * Author: Matthew Horridge<br> The University Of Manchester<br> Information Management Group<br> Date:
 * 16-Sep-2008<br><br>
 */
public class StructuralTypePriorityExpansionStrategy implements ExpansionStrategy {

    private int count = 0;

    private InitialEntailmentCheckStrategy initialEntailmentCheckStrategy = InitialEntailmentCheckStrategy.PERFORM;

    private Supplier<OWLOntologyManager> m;

    public StructuralTypePriorityExpansionStrategy(InitialEntailmentCheckStrategy initialEntailmentCheckStrategy, Supplier<OWLOntologyManager> m) {
        this.initialEntailmentCheckStrategy = initialEntailmentCheckStrategy;
        this.m = m;
    }

    @Override
    public Set<OWLAxiom> doExpansion(Set<OWLAxiom> axioms, EntailmentChecker checker, ExplanationProgressMonitor<?> progressMonitor) {

        Set<OWLAxiom> expansion;
        try {
            count = 0;

            if(progressMonitor.isCancelled()) {
                throw new ExplanationGeneratorInterruptedException();
            }

            count++;
            if (initialEntailmentCheckStrategy.equals(InitialEntailmentCheckStrategy.PERFORM)) {
                if(!checker.isEntailed(axioms)) {
                    return Collections.emptySet();
                }
            }

            OWLOntology ont = m.get().createOntology(axioms);
//            createOntology(axioms, checker);


            expansion = new HashSet<>();
            Set<OWLEntity> entailmentSignature = checker.getEntailmentSignature();
            EntityFilteredDefinitionExpander expander = new EntityFilteredDefinitionExpander(ont);
            Set<OWLEntity> expandedWithDefinition = new HashSet<>();
            Set<OWLAxiom> addedAxioms = new HashSet<>();
            OWLDataFactory df = ont.getOWLOntologyManager().getOWLDataFactory();
            for(OWLEntity ent : entailmentSignature) {
                OWLDeclarationAxiom declAx = df.getOWLDeclarationAxiom(ent);
                expansion.add(declAx);
                addedAxioms.add(declAx);
            }


//        // Initial expansion
            for (OWLEntity ent : entailmentSignature) {
                expandedWithDefinition.add(ent);
                add(expansion, ent.accept(expander));
            }
            int size = 0;
            Set<OWLDisjointClassesAxiom> disjointClassesAxioms = new HashSet<>();
            Set<OWLEntity> expansionSig = new HashSet<>();
            while (size != expansion.size()) {
                if(progressMonitor.isCancelled()) {
                    return Collections.emptySet();
                }
                size = expansion.size();

                // Add in
                Set<OWLAxiom> combined = new HashSet<>(disjointClassesAxioms.size() + expansion.size() + 50);
                combined.addAll(expansion);
                for(OWLDisjointClassesAxiom disjointAx : disjointClassesAxioms) {
                    for(OWLClassExpression desc : asList(disjointAx.classExpressions())) {
                        if(desc.isAnonymous()) {
                            combined.add(disjointAx);
                            break;
                        }
                        else {
                            if(!expansionSig.contains(desc.asOWLClass())) {
                                break;
                            }
                        }
                        combined.add(disjointAx);
                    }
                }
                count++;
                if (checker.isEntailed(combined)) {
                    Set<OWLAxiom> result = new HashSet<OWLAxiom>(checker.getEntailingAxioms(combined));
                    result.removeAll(addedAxioms);
                    return result;
                }
                // Expand more

                for (OWLAxiom ax : new ArrayList<>(expansion)) {
                    ax.signature().forEach(ent-> {
                        if (!expandedWithDefinition.contains(ent)) {
                            ent.accept(expander).forEach(expAx -> dealWithDisjoints(expansion, disjointClassesAxioms, expansionSig, expAx));
                            expandedWithDefinition.add(ent);
                        }
                    });
                }
            }
            for(OWLEntity ent : expansionSig) {
                if (ent.isOWLClass()) {
                    add(expansion, ont.disjointClassesAxioms(ent.asOWLClass()));
                }
            }
            count++;
            if(checker.isEntailed(expansion)) {
                Set<OWLAxiom> result = new HashSet<OWLAxiom>(checker.getEntailingAxioms(expansion));
                result.removeAll(addedAxioms);
                return result;
            }
            // Not worked ... now we fall back
            // TODO: Optimise more
            while (!expansion.containsAll(asList(ont.logicalAxioms()))) {
                if(progressMonitor.isCancelled()) {
                    return Collections.emptySet();
                }
                // Expand more
                for (OWLAxiom ax : new ArrayList<>(expansion)) {
                    ax.signature()
                        .forEach(ent -> add(expansion, ont.referencingAxioms(ent)));
                }
                count++;
                if (checker.isEntailed(expansion)) {
                    Set<OWLAxiom> result = new HashSet<OWLAxiom>(checker.getEntailingAxioms(expansion));
                    result.removeAll(addedAxioms);
                    return result;
                }
            }
        }
        catch (OWLOntologyCreationException e) {
            throw new RuntimeException(e);
        }
        return expansion;
    }

    protected void dealWithDisjoints(Set<OWLAxiom> expansion,Set<OWLDisjointClassesAxiom> disjointClassesAxioms,Set<OWLEntity> expansionSig, OWLAxiom expAx) {
        if (!expAx.getAxiomType().equals(AxiomType.DISJOINT_CLASSES) && !expAx.isOfType(AxiomType.CLASS_ASSERTION, AxiomType.OBJECT_PROPERTY_ASSERTION, AxiomType.DATA_PROPERTY_ASSERTION, AxiomType.SAME_INDIVIDUAL, AxiomType.DIFFERENT_INDIVIDUALS)) {
            expansion.add(expAx);
            add(expansionSig, expAx.signature());
        } else {
            if (expAx instanceof OWLDisjointClassesAxiom) {
                disjointClassesAxioms.add((OWLDisjointClassesAxiom) expAx);
            }
        }
    }

    @Override
    public int getNumberOfSteps() {
        return count;
    }

    private class EntityFilteredDefinitionExpander implements OWLEntityVisitorEx<Stream<? extends OWLAxiom>> {

        private OWLOntology theOnt;

        private EntityFilteredDefinitionExpander(OWLOntology theOnt) {
            this.theOnt = theOnt;
        }

        @Override
        public Stream<OWLClassAxiom> visit(OWLClass cls) {
            // Return axioms that define the class
            return theOnt.axioms(cls);
//            for(OWLAxiom ax : theOnt.getReferencingAxioms(cls)) {
//                if (axioms.contains(ax)) {
//                    if(ax.getAxiomType().equals(AxiomType.EQUIVALENT_CLASSES)) {
//                        OWLEquivalentClassesAxiom eqClsesAx = (OWLEquivalentClassesAxiom) ax;
//                        for(OWLClassExpression desc : eqClsesAx.getClassExpressions()) {
//                            if(desc instanceof OWLObjectUnionOf) {
//                                if(((OWLObjectUnionOf) desc).getOperands().contains(cls)) {
//                                    axioms.add(ax);
//                                    break;
//                                }
//                            }
//                        }
//                    }
//                }
//            }
        }


        @Override
        public Stream<OWLAxiom> visit(OWLAnnotationProperty owlAnnotationProperty) {
            return Stream.empty();
        }


        @Override
        public Stream<? extends OWLAxiom> visit(OWLObjectProperty property) {
            return theOnt.axioms(property);
        }


        @Override
        public Stream<? extends OWLAxiom> visit(OWLDataProperty property) {
            return theOnt.axioms(property);
        }


        @Override
        public Stream<? extends OWLAxiom> visit(OWLNamedIndividual individual) {
            return Stream.concat(
                theOnt.axioms(individual),
                theOnt.axioms(AxiomType.OBJECT_PROPERTY_ASSERTION)
                .filter(ax -> ax.getObject().equals(individual)));
        }


        @Override
        public Stream<? extends OWLAxiom> visit(OWLDatatype dataType) {
            return Stream.empty();
        }
    }
}
