package org.semanticweb.owl.explanation.impl.blackbox;

import org.semanticweb.owl.explanation.api.Explanation;
import org.semanticweb.owlapi.model.*;

import static org.semanticweb.owlapi.util.OWLAPIStreamUtils.asList;

import java.util.*;

/**
 * Author: Matthew Horridge<br>
 * The University of Manchester<br>
 * Bio-Health Informatics Group<br>
 * Date: 14/03/2011
 */
public class PatternBasedEntailmentChecker {

    private OWLAxiom entailment;

    protected OWLSubClassOfAxiom subClassOfEntailment;

    private OWLClassAssertionAxiom classAssertionEntailment;

    private Set<Explanation<OWLAxiom>> simpleExplanations = new HashSet<>();

    public PatternBasedEntailmentChecker(OWLAxiom entailment, Set<OWLAxiom> workingAxioms) {
        this.entailment = entailment;
        if (entailment instanceof OWLSubClassOfAxiom) {
            subClassOfEntailment = (OWLSubClassOfAxiom) entailment;
        }
        else {
            subClassOfEntailment = null;
        }
        if (entailment instanceof OWLClassAssertionAxiom) {
            classAssertionEntailment = (OWLClassAssertionAxiom) entailment;
        }
        else {
            classAssertionEntailment = null;
        }
        processAxioms(workingAxioms);
    }

    public Set<Explanation<OWLAxiom>> getSimpleExplanations() {
        return simpleExplanations;
    }

    private void processAxioms(Set<OWLAxiom> axioms) {
        WorkingAxiomVisitor visitor = new WorkingAxiomVisitor();
        for (OWLAxiom ax : axioms) {
            if (entailment.equalsIgnoreAnnotations(ax)) {
                Set<OWLAxiom> axs = Collections.singleton(ax);
                simpleExplanations.add(new Explanation<>(entailment, axs));
            }
            ax.accept(visitor);
        }
    }

    protected void addExplanation(OWLAxiom explanationAxiom) {

    }


    private class WorkingAxiomVisitor implements OWLAxiomVisitor {

        private Map<OWLObjectPropertyExpression, Set<OWLAxiom>> existentials = new HashMap<>();

        public WorkingAxiomVisitor() {}

        @Override
        public void visit(OWLDeclarationAxiom axiom) {
        }

        @Override
        public void visit(OWLSubClassOfAxiom axiom) {
            if (subClassOfEntailment != null) {
                Set<OWLClassExpression> superConjuncts = axiom.getSuperClass().asConjunctSet();
                Set<OWLClassExpression> subDisjuncts = axiom.getSubClass().asDisjunctSet();
                if (superConjuncts.contains(axiom.getSuperClass())) {
                    if (subDisjuncts.contains(axiom.getSubClass())) {
                        addExplanation(axiom);
                    }
                }
            }
        }

        @Override
        public void visit(OWLNegativeObjectPropertyAssertionAxiom axiom) {

        }

        @Override
        public void visit(OWLAsymmetricObjectPropertyAxiom axiom) {
        }

        @Override
        public void visit(OWLReflexiveObjectPropertyAxiom axiom) {
        }

        @Override
        public void visit(OWLDisjointClassesAxiom axiom) {
        }

        @Override
        public void visit(OWLDataPropertyDomainAxiom axiom) {
        }

        @Override
        public void visit(OWLObjectPropertyDomainAxiom axiom) {
        }

        @Override
        public void visit(OWLEquivalentObjectPropertiesAxiom axiom) {
        }

        @Override
        public void visit(OWLNegativeDataPropertyAssertionAxiom axiom) {
        }

        @Override
        public void visit(OWLDifferentIndividualsAxiom axiom) {
        }

        @Override
        public void visit(OWLDisjointDataPropertiesAxiom axiom) {
        }

        @Override
        public void visit(OWLDisjointObjectPropertiesAxiom axiom) {
        }

        @Override
        public void visit(OWLObjectPropertyRangeAxiom axiom) {
        }

        @Override
        public void visit(OWLObjectPropertyAssertionAxiom axiom) {
            OWLObjectPropertyExpression prop = axiom.getProperty().getSimplified();
            Set<OWLAxiom> axioms = existentials.get(prop);
            if (axioms == null) {
                axioms = new HashSet<>();
                existentials.put(prop, axioms);
            }
            axioms.add(axiom);
        }

        @Override
        public void visit(OWLFunctionalObjectPropertyAxiom axiom) {
        }

        @Override
        public void visit(OWLSubObjectPropertyOfAxiom axiom) {
        }

        @Override
        public void visit(OWLDisjointUnionAxiom axiom) {
        }

        @Override
        public void visit(OWLSymmetricObjectPropertyAxiom axiom) {
        }

        @Override
        public void visit(OWLDataPropertyRangeAxiom axiom) {
        }

        @Override
        public void visit(OWLFunctionalDataPropertyAxiom axiom) {
        }

        @Override
        public void visit(OWLEquivalentDataPropertiesAxiom axiom) {
        }

        @Override
        public void visit(OWLClassAssertionAxiom axiom) {

        }

        @Override
        public void visit(OWLEquivalentClassesAxiom axiom) {
            // For SubClassOf(C, D)
            // Handle axioms of the form
            // EquivalentClasses(C, D)
            // EquivalentClasses(C, ObjectIntersectionOf(D, ...))
            // EquivalentClasses(D, ObjectUnionOf(C, ...))
            List<OWLClassExpression> asList = asList(axiom.classExpressions());
            if (subClassOfEntailment != null) {
                OWLClassExpression subClass = subClassOfEntailment.getSubClass();
                OWLClassExpression superClass = subClassOfEntailment.getSuperClass();
                if (axiom.contains(subClass) && axiom.contains(superClass)) {
                    addExplanation(axiom);
                }
                else if (axiom.contains(subClass)) {
                    for (OWLClassExpression ce : asList) {
                        if (!ce.equals(subClass)) {
                            if (ce.asConjunctSet().contains(superClass)) {
                                addExplanation(axiom);
                                break;
                            }
                        }
                    }
                }
                else if (axiom.contains(superClass)) {
                    for (OWLClassExpression ce : asList) {
                        if (!ce.equals(superClass)) {
                            if (ce.asDisjunctSet().contains(subClass)) {
                                addExplanation(axiom);
                                break;
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void visit(OWLDataPropertyAssertionAxiom axiom) {

        }

        @Override
        public void visit(OWLTransitiveObjectPropertyAxiom axiom) {
        }

        @Override
        public void visit(OWLIrreflexiveObjectPropertyAxiom axiom) {
        }

        @Override
        public void visit(OWLSubDataPropertyOfAxiom axiom) {
        }

        @Override
        public void visit(OWLInverseFunctionalObjectPropertyAxiom axiom) {
        }

        @Override
        public void visit(OWLSameIndividualAxiom axiom) {
        }

        @Override
        public void visit(OWLSubPropertyChainOfAxiom axiom) {
        }

        @Override
        public void visit(OWLInverseObjectPropertiesAxiom axiom) {
        }

        @Override
        public void visit(OWLHasKeyAxiom axiom) {
        }

        @Override
        public void visit(OWLDatatypeDefinitionAxiom axiom) {
        }

        @Override
        public void visit(SWRLRule rule) {
        }

        @Override
        public void visit(OWLAnnotationAssertionAxiom axiom) {
        }

        @Override
        public void visit(OWLSubAnnotationPropertyOfAxiom axiom) {
        }

        @Override
        public void visit(OWLAnnotationPropertyDomainAxiom axiom) {
        }

        @Override
        public void visit(OWLAnnotationPropertyRangeAxiom axiom) {
        }
    }
}
