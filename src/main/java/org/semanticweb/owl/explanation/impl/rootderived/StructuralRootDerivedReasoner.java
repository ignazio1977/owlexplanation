package org.semanticweb.owl.explanation.impl.rootderived;

import org.semanticweb.owl.explanation.api.ExplanationException;
import org.semanticweb.owl.explanation.api.RootDerivedReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.search.EntitySearcher;

import static org.semanticweb.owlapi.util.OWLAPIStreamUtils.asList;

import java.util.*;
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
 * Author: Matthew Horridge<br> The University Of Manchester<br> Information Management Group<br> Date:
 * 07-Sep-2008<br><br>
 */
public class StructuralRootDerivedReasoner implements RootDerivedReasoner {

    private OWLOntologyManager man;

    protected OWLReasoner reasoner;

    private OWLOntology mergedOntology;

    private Map<OWLClass, Set<OWLClass>> child2Parent;

    private Map<OWLClass, Set<OWLClass>> parent2Child;

    private Set<OWLClass> roots;

    private boolean dirty;


    public StructuralRootDerivedReasoner(OWLOntologyManager man, OWLReasoner reasoner) {
        this.man = man;
        this.reasoner = reasoner;
        this.child2Parent = new HashMap<>();
        this.parent2Child = new HashMap<>();
        roots = new HashSet<>();

        try {
            getMergedOntology();
        }
        catch (ExplanationException e) {
            e.printStackTrace();
        }
        dirty = true;
    }


    public OWLOntology getMergedOntology() throws ExplanationException {
        try {
            if (mergedOntology == null) {
                mergedOntology = man.createOntology(IRI.create("owlapi:ontology:merge"), reasoner.getRootOntology().importsClosure(), true);
            }
            return mergedOntology;
        }
        catch (OWLOntologyCreationException e) {
            throw new ExplanationException(e);
        }
        catch (OWLOntologyChangeException e) {
            throw new ExplanationException(e);
        }
    }

    private static Set<OWLClass> get(OWLClass c, Map<OWLClass, Set<OWLClass>> map) {
        return map.computeIfAbsent(c, x->new HashSet<>());
    }

    @Override
    public Set<OWLClass> getDependentChildClasses(OWLClass cls) {
        return get(cls, parent2Child);
    }


    @Override
    public Set<OWLClass> getDependentDescendantClasses(OWLClass cls) {
        Set<OWLClass> result = new HashSet<>();
        getDescendants(cls, result);
        return result;
    }

    private void getDescendants(OWLClass cls, Set<OWLClass> result) {
        if (result.contains(cls)) {
            return;
        }
        for (OWLClass child : getDependentChildClasses(cls)) {
            result.add(child);
            getDescendants(child, result);
        }
    }


    @Override
    public Set<OWLClass> getRootUnsatisfiableClasses() throws ExplanationException {
        if (dirty) {
            computeRootDerivedClasses();
        }
        return Collections.unmodifiableSet(roots);
    }


    private void computeRootDerivedClasses() throws ExplanationException {
            computeCandidateRoots();
            roots.remove(man.getOWLDataFactory().getOWLNothing());
            for (OWLClass child : child2Parent.keySet()) {
                for (OWLClass par : get(child, child2Parent)) {
                    get(par, parent2Child).add(child);
                }
            }
            // Now find cycles
            HashSet<OWLClass> processed = new HashSet<>();
            HashSet<Set<OWLClass>> result = new HashSet<>();

            for (OWLClass cls : child2Parent.keySet()) {
                if (!processed.contains(cls)) {
                    tarjan(cls, 0, new LinkedList<OWLClass>(), new HashMap<OWLClass, Integer>(), new HashMap<OWLClass, Integer>(), result, processed, new HashSet<OWLClass>());
                }


            }
            for (Set<OWLClass> res : result) {
                roots.addAll(res);
            }
    }

    public void tarjan(OWLClass cls, int index, Deque<OWLClass> stack, Map<OWLClass, Integer> indexMap, Map<OWLClass, Integer> lowlinkMap, Set<Set<OWLClass>> result, Set<OWLClass> processed, Set<OWLClass> stackClass) {
        processed.add(cls);
        indexMap.put(cls, index);
        lowlinkMap.put(cls, index);
        index = index + 1;
        stack.push(cls);
        stackClass.add(cls);
        for (OWLClass par : child2Parent.get(cls)) {
            if (!indexMap.containsKey(par)) {
                tarjan(par, index, stack, indexMap, lowlinkMap, result, processed, stackClass);
                lowlinkMap.put(cls, Math.min(lowlinkMap.get(cls), lowlinkMap.get(par)));
            }
            else if (stackClass.contains(par)) {
                lowlinkMap.put(cls, Math.min(lowlinkMap.get(cls), indexMap.get(par)));
            }
        }
        if (lowlinkMap.get(cls).equals(indexMap.get(cls))) {
            Set<OWLClass> scc = new HashSet<>();
            while (true) {
                OWLClass clsPrime = stack.pop();
                stackClass.remove(clsPrime);
                scc.add(clsPrime);
                if (clsPrime.equals(cls)) {
                    break;
                }
            }
            if (scc.size() > 1) {
                result.add(scc);
            }
        }
    }



    private void computeCandidateRoots() throws ExplanationException {
        List<OWLClass> unsatisfiableClasses = asList(reasoner.getUnsatisfiableClasses().entities());
        SuperClassChecker checker = new SuperClassChecker();
        for (OWLClass cls : unsatisfiableClasses) {
            checker.reset();
            EntitySearcher.getSuperClasses(cls, reasoner.getRootOntology().importsClosure()).forEach(sup-> sup.accept(checker));
            EntitySearcher.getEquivalentClasses(cls, reasoner.getRootOntology().importsClosure()).forEach(sup-> sup.accept(checker));
            Set<OWLClass> dependencies = checker.getDependencies();
            child2Parent.put(cls, new HashSet<>(dependencies));
            if (dependencies.isEmpty()) {
                // Definite root?
                roots.add(cls);
            }
        }
    }


    private class SuperClassChecker implements OWLClassExpressionVisitor {

        private Set<OWLClass> dependsOn;

        private int modalDepth;

        private Map<Integer, Set<OWLObjectAllValuesFrom>> modalDepth2UniversalRestrictionPropertyMap;

        private Map<Integer, Set<OWLObjectPropertyExpression>> modalDepth2ExistsRestrictionPropertyMap;


        public SuperClassChecker() {
            modalDepth2UniversalRestrictionPropertyMap = new HashMap<>();
            modalDepth2ExistsRestrictionPropertyMap = new HashMap<>();
            dependsOn = new HashSet<>();
            modalDepth = 0;
        }


        public void addUniversalRestrictionProperty(OWLObjectAllValuesFrom r) {
            Set<OWLObjectAllValuesFrom> props = modalDepth2UniversalRestrictionPropertyMap.get(modalDepth);
            if (props == null) {
                props = new HashSet<>();
                modalDepth2UniversalRestrictionPropertyMap.put(modalDepth, props);
            }
            props.add(r);
        }


        public void addExistsRestrictionProperty(OWLObjectPropertyExpression prop) {
            Set<OWLObjectPropertyExpression> props = modalDepth2ExistsRestrictionPropertyMap.get(modalDepth);
            if (props == null) {
                props = new HashSet<>();
                modalDepth2ExistsRestrictionPropertyMap.put(modalDepth, props);
            }
            props.add(prop);
        }


        public Set<OWLClass> getDependencies() {
            return Collections.unmodifiableSet(dependsOn);
        }


        public void reset() {
            dependsOn.clear();
            modalDepth2ExistsRestrictionPropertyMap.clear();
            modalDepth2UniversalRestrictionPropertyMap.clear();
        }


        @Override
        public void visit(OWLClass desc) {
            if (!reasoner.isSatisfiable(desc)) {
                dependsOn.add(desc);
            }
        }


        @Override
        public void visit(OWLDataAllValuesFrom desc) {

        }


        @Override
        public void visit(OWLDataExactCardinality desc) {

        }


        @Override
        public void visit(OWLDataMaxCardinality desc) {
        }


        @Override
        public void visit(OWLDataMinCardinality desc) {
        }


        @Override
        public void visit(OWLDataSomeValuesFrom desc) {
        }


        @Override
        public void visit(OWLDataHasValue desc) {
        }


        @Override
        public void visit(OWLObjectAllValuesFrom desc) {
            if (desc.getFiller().isAnonymous()) {
                modalDepth++;
                desc.getFiller().accept(this);
                modalDepth--;
            }
            else {
                if (!reasoner.isSatisfiable(desc.getFiller())) {
                    addUniversalRestrictionProperty(desc);
                    dependsOn.add(desc.getFiller().asOWLClass());
                }
            }
        }


        @Override
        public void visit(OWLObjectComplementOf desc) {

        }


        @Override
        public void visit(OWLObjectExactCardinality desc) {
            if (desc.getFiller().isAnonymous()) {
                modalDepth++;
                desc.getFiller().accept(this);
                modalDepth--;
            }
            else {
                if (!reasoner.isSatisfiable(desc.getFiller()) && !desc.getFiller().isAnonymous()) {
                    dependsOn.add(desc.getFiller().asOWLClass());
                }
            }
            addExistsRestrictionProperty(desc.getProperty());
        }


        @Override
        public void visit(OWLObjectIntersectionOf desc) {
            desc.operands().forEach(this::updateUnsatisfiableDependents);
        }


        protected void updateUnsatisfiableDependents(OWLClassExpression op) {
            if (op.isAnonymous()) {
                op.accept(this);
            }
            else {
                if (!reasoner.isSatisfiable(op)) {
                    dependsOn.add(op.asOWLClass());
                }
            }
        }


        @Override
        public void visit(OWLObjectMaxCardinality desc) {

        }


        @Override
        public void visit(OWLObjectMinCardinality desc) {
            if (desc.getFiller().isAnonymous()) {
                modalDepth++;
                desc.getFiller().accept(this);
                modalDepth--;
            }
            else {
                if (!reasoner.isSatisfiable(desc.getFiller())) {
                    dependsOn.add(desc.getFiller().asOWLClass());
                }
            }
            addExistsRestrictionProperty(desc.getProperty());
        }


        @Override
        public void visit(OWLObjectOneOf desc) {
        }


        @Override
        public void visit(OWLObjectHasSelf desc) {
            addExistsRestrictionProperty(desc.getProperty());
        }


        @Override
        public void visit(OWLObjectSomeValuesFrom desc) {
            if (desc.getFiller().isAnonymous()) {
                modalDepth++;
                desc.getFiller().accept(this);
                modalDepth--;
            }
            else {
                if (!reasoner.isSatisfiable(desc.getFiller())) {
                    dependsOn.add(desc.getFiller().asOWLClass());
                }
            }
            addExistsRestrictionProperty(desc.getProperty());
        }


        @Override
        public void visit(OWLObjectUnionOf desc) {
            if(desc.operands().anyMatch(op->reasoner.isSatisfiable(op))) {
                return;
            }
            desc.operands().forEach(this::updateDependents);
        }


        protected void updateDependents(OWLClassExpression op) {
            if (op.isAnonymous()) {
                op.accept(this);
            }
            else {
                dependsOn.add(op.asOWLClass());
            }
        }


        @Override
        public void visit(OWLObjectHasValue desc) {
            addExistsRestrictionProperty(desc.getProperty());
        }
    }
}
