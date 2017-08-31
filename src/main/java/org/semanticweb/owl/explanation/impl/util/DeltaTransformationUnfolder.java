package org.semanticweb.owl.explanation.impl.util;

import org.semanticweb.owlapi.model.*;

import static org.semanticweb.owlapi.util.OWLAPIStreamUtils.asSet;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Author: Matthew Horridge<br>
 * The University of Manchester<br>
 * Bio-Health Informatics Group<br>
 * Date: 01/03/2011
 */
public class DeltaTransformationUnfolder {

    protected OWLDataFactory dataFactory;

    private Map<OWLClass, Set<OWLClassExpression>> posName2ClassExpressionMap = new HashMap<>();

    private Map<OWLClass, Set<OWLClassExpression>> negName2ClassExpressionMap = new HashMap<>();

    public DeltaTransformationUnfolder(OWLDataFactory dataFactory) {
        this.dataFactory = dataFactory;
    }

    public Set<OWLAxiom> getUnfolded(Set<OWLAxiom> axioms, Set<OWLEntity> signature) {
        posName2ClassExpressionMap.clear();
        negName2ClassExpressionMap.clear();
        Set<OWLAxiom> toUnfold = new HashSet<>();
        for(OWLAxiom ax : axioms) {
            if(ax instanceof OWLSubClassOfAxiom) {
                OWLSubClassOfAxiom sca = (OWLSubClassOfAxiom) ax;
                OWLClassExpression subCls = sca.getSubClass();
                OWLClassExpression superCls = sca.getSuperClass();
                if(isFreshName(subCls, signature) && !isFreshName(superCls, signature)) {
                    addToIndex(subCls.asOWLClass(), superCls, posName2ClassExpressionMap);
                }
                else if(isFreshName(superCls, signature) && !isFreshName(subCls, signature)) {
                    addToIndex(superCls.asOWLClass(), subCls, negName2ClassExpressionMap);
                }
                else {
                    toUnfold.add(ax);
                }
            }
            else {
                toUnfold.add(ax);
            }
        }
        
        Set<OWLAxiom> unfolded = new HashSet<>();
        AxiomUnfolder axiomUnfolder = new AxiomUnfolder();
        for(OWLAxiom ax : toUnfold) {
            unfolded.add(ax.accept(axiomUnfolder));
        }
        return unfolded;
    }

    private static void addToIndex(OWLClass key, OWLClassExpression val, Map<OWLClass, Set<OWLClassExpression>> map) {
        Set<OWLClassExpression> vals = map.get(key);
        if(vals == null) {
            vals = new HashSet<>();
            map.put(key, vals);
        }
        vals.add(val);
    }

    private boolean isFreshName(OWLClassExpression ce, Set<OWLEntity> signature) {
        return !ce.isOWLThing() && !ce.isOWLNothing() && !ce.isAnonymous() && !signature.contains(ce.asOWLClass());
    }

    private class AxiomUnfolder implements OWLAxiomVisitorEx<OWLAxiom> {

        private ClassExpressionUnfolder positiveClassExpressionUnfolder = new ClassExpressionUnfolder(Polarity.POSITIVE);

        private ClassExpressionUnfolder negativeClassExpressionUnfolder = new ClassExpressionUnfolder(Polarity.NEGATIVE);

        private OWLClassExpression unfold(OWLClassExpression ce, Polarity polarity) {
            ClassExpressionUnfolder classExpressionUnfolder;
            if(polarity.isPositive()) {
                classExpressionUnfolder = positiveClassExpressionUnfolder;
            }
            else {
                classExpressionUnfolder = negativeClassExpressionUnfolder;
            }
            return ce.accept(classExpressionUnfolder);
        }

        private Set<OWLClassExpression> unfold(Stream<OWLClassExpression> classExpressions, Polarity polarity) {
            Set<OWLClassExpression> unfolded = new HashSet<>();
            classExpressions.forEach(ce-> {
                if (polarity.isPositive()) {
                    unfolded.add(ce.accept(positiveClassExpressionUnfolder));
                }
                else {
                    unfolded.add(ce.accept(negativeClassExpressionUnfolder));
                }
            });
            return unfolded;
        }


        @Override
        public OWLAxiom visit(OWLSubClassOfAxiom axiom) {
            return dataFactory.getOWLSubClassOfAxiom(unfold(axiom.getSubClass(), Polarity.NEGATIVE), unfold(axiom.getSuperClass(), Polarity.POSITIVE));
        }

        @Override
        public OWLAxiom visit(OWLNegativeObjectPropertyAssertionAxiom owlNegativeObjectPropertyAssertionAxiom) {
            return owlNegativeObjectPropertyAssertionAxiom;
        }

        @Override
        public OWLAxiom visit(OWLAsymmetricObjectPropertyAxiom owlAsymmetricObjectPropertyAxiom) {
            return owlAsymmetricObjectPropertyAxiom;
        }

        @Override
        public OWLAxiom visit(OWLReflexiveObjectPropertyAxiom owlReflexiveObjectPropertyAxiom) {
            return owlReflexiveObjectPropertyAxiom;
        }

        @Override
        public OWLAxiom visit(OWLDisjointClassesAxiom owlDisjointClassesAxiom) {
            return dataFactory.getOWLDisjointClassesAxiom(unfold(owlDisjointClassesAxiom.classExpressions(), Polarity.POSITIVE));
        }

        @Override
        public OWLAxiom visit(OWLDataPropertyDomainAxiom owlDataPropertyDomainAxiom) {
            return dataFactory.getOWLDataPropertyDomainAxiom(owlDataPropertyDomainAxiom.getProperty(), owlDataPropertyDomainAxiom.getDomain());
        }

        @Override
        public OWLAxiom visit(OWLObjectPropertyDomainAxiom owlObjectPropertyDomainAxiom) {
            return dataFactory.getOWLObjectPropertyDomainAxiom(owlObjectPropertyDomainAxiom.getProperty(), owlObjectPropertyDomainAxiom.getDomain());
        }

        @Override
        public OWLAxiom visit(OWLEquivalentObjectPropertiesAxiom owlEquivalentObjectPropertiesAxiom) {
            return owlEquivalentObjectPropertiesAxiom;
        }

        @Override
        public OWLAxiom visit(OWLNegativeDataPropertyAssertionAxiom owlNegativeDataPropertyAssertionAxiom) {
            return owlNegativeDataPropertyAssertionAxiom;
        }

        @Override
        public OWLAxiom visit(OWLDifferentIndividualsAxiom owlDifferentIndividualsAxiom) {
            return owlDifferentIndividualsAxiom;
        }

        @Override
        public OWLAxiom visit(OWLDisjointDataPropertiesAxiom owlDisjointDataPropertiesAxiom) {
            return owlDisjointDataPropertiesAxiom;
        }

        @Override
        public OWLAxiom visit(OWLDisjointObjectPropertiesAxiom owlDisjointObjectPropertiesAxiom) {
            return owlDisjointObjectPropertiesAxiom;
        }

        @Override
        public OWLAxiom visit(OWLObjectPropertyRangeAxiom owlObjectPropertyRangeAxiom) {
            return dataFactory.getOWLObjectPropertyRangeAxiom(owlObjectPropertyRangeAxiom.getProperty(), unfold(owlObjectPropertyRangeAxiom.getRange(), Polarity.POSITIVE));
        }

        @Override
        public OWLAxiom visit(OWLObjectPropertyAssertionAxiom owlObjectPropertyAssertionAxiom) {
            return owlObjectPropertyAssertionAxiom;
        }

        @Override
        public OWLAxiom visit(OWLFunctionalObjectPropertyAxiom owlFunctionalObjectPropertyAxiom) {
            return owlFunctionalObjectPropertyAxiom;
        }

        @Override
        public OWLAxiom visit(OWLSubObjectPropertyOfAxiom owlSubObjectPropertyOfAxiom) {
            return owlSubObjectPropertyOfAxiom;
        }

        @Override
        public OWLAxiom visit(OWLDisjointUnionAxiom owlDisjointUnionAxiom) {
            return owlDisjointUnionAxiom;
        }

        @Override
        public OWLAxiom visit(OWLDeclarationAxiom owlDeclarationAxiom) {
            return owlDeclarationAxiom;
        }

        @Override
        public OWLAxiom visit(OWLAnnotationAssertionAxiom owlAnnotationAssertionAxiom) {
            return owlAnnotationAssertionAxiom;
        }

        @Override
        public OWLAxiom visit(OWLSymmetricObjectPropertyAxiom owlSymmetricObjectPropertyAxiom) {
            return owlSymmetricObjectPropertyAxiom;
        }

        @Override
        public OWLAxiom visit(OWLDataPropertyRangeAxiom owlDataPropertyRangeAxiom) {
            return owlDataPropertyRangeAxiom;
        }

        @Override
        public OWLAxiom visit(OWLFunctionalDataPropertyAxiom owlFunctionalDataPropertyAxiom) {
            return owlFunctionalDataPropertyAxiom;
        }

        @Override
        public OWLAxiom visit(OWLEquivalentDataPropertiesAxiom owlEquivalentDataPropertiesAxiom) {
            return owlEquivalentDataPropertiesAxiom;
        }

        @Override
        public OWLAxiom visit(OWLClassAssertionAxiom owlClassAssertionAxiom) {
            return dataFactory.getOWLClassAssertionAxiom(unfold(owlClassAssertionAxiom.getClassExpression(), Polarity.POSITIVE), owlClassAssertionAxiom.getIndividual());
        }

        @Override
        public OWLAxiom visit(OWLEquivalentClassesAxiom owlEquivalentClassesAxiom) {
            return dataFactory.getOWLEquivalentClassesAxiom(unfold(owlEquivalentClassesAxiom.classExpressions(), Polarity.POSITIVE));
        }

        @Override
        public OWLAxiom visit(OWLDataPropertyAssertionAxiom owlDataPropertyAssertionAxiom) {
            return owlDataPropertyAssertionAxiom;
        }

        @Override
        public OWLAxiom visit(OWLTransitiveObjectPropertyAxiom owlTransitiveObjectPropertyAxiom) {
            return owlTransitiveObjectPropertyAxiom;
        }

        @Override
        public OWLAxiom visit(OWLIrreflexiveObjectPropertyAxiom owlIrreflexiveObjectPropertyAxiom) {
            return owlIrreflexiveObjectPropertyAxiom;
        }

        @Override
        public OWLAxiom visit(OWLSubDataPropertyOfAxiom owlSubDataPropertyOfAxiom) {
            return owlSubDataPropertyOfAxiom;
        }

        @Override
        public OWLAxiom visit(OWLInverseFunctionalObjectPropertyAxiom owlInverseFunctionalObjectPropertyAxiom) {
            return owlInverseFunctionalObjectPropertyAxiom;
        }

        @Override
        public OWLAxiom visit(OWLSameIndividualAxiom owlSameIndividualAxiom) {
            return owlSameIndividualAxiom;
        }

        @Override
        public OWLAxiom visit(OWLSubPropertyChainOfAxiom owlSubPropertyChainOfAxiom) {
            return owlSubPropertyChainOfAxiom;
        }

        @Override
        public OWLAxiom visit(OWLInverseObjectPropertiesAxiom owlInverseObjectPropertiesAxiom) {
            return owlInverseObjectPropertiesAxiom;
        }

        @Override
        public OWLAxiom visit(OWLHasKeyAxiom owlHasKeyAxiom) {
            return owlHasKeyAxiom;
        }

        @Override
        public OWLAxiom visit(OWLDatatypeDefinitionAxiom owlDatatypeDefinitionAxiom) {
            return owlDatatypeDefinitionAxiom;
        }

        @Override
        public OWLAxiom visit(SWRLRule swrlRule) {
            return swrlRule;
        }

        @Override
        public OWLAxiom visit(OWLSubAnnotationPropertyOfAxiom owlSubAnnotationPropertyOfAxiom) {
            return owlSubAnnotationPropertyOfAxiom;
        }

        @Override
        public OWLAxiom visit(OWLAnnotationPropertyDomainAxiom owlAnnotationPropertyDomainAxiom) {
            return owlAnnotationPropertyDomainAxiom;
        }

        @Override
        public OWLAxiom visit(OWLAnnotationPropertyRangeAxiom owlAnnotationPropertyRangeAxiom) {
            return owlAnnotationPropertyRangeAxiom;
        }
    }

    private OWLClassExpression getNamedClassExpression(Polarity pol, OWLClass namingClass) {
        if(pol.isPositive()) {
            Set<OWLClassExpression> ops = posName2ClassExpressionMap.get(namingClass);
            if(ops != null) {
                if(ops.size() > 1) {
                    return dataFactory.getOWLObjectIntersectionOf(ops);
                }
                else {
                    return ops.iterator().next();
                }
            }
        }
        else {
            Set<OWLClassExpression> ops = negName2ClassExpressionMap.get(namingClass);
            if(ops != null) {
                if(ops.size() > 1) {
                    return dataFactory.getOWLObjectUnionOf(ops);
                }
                else {
                    return ops.iterator().next();
                }
            }
        }
        return null;
    }

    private class ClassExpressionUnfolder implements OWLClassExpressionVisitorEx<OWLClassExpression> {

        private Polarity currentPolarity = Polarity.POSITIVE;

        private ClassExpressionUnfolder(Polarity currentPolarity) {
            this.currentPolarity = currentPolarity;
        }

        @Override
        public OWLClassExpression visit(OWLClass owlClass) {
            OWLClassExpression namedExpression = getNamedClassExpression(currentPolarity, owlClass);
            if(namedExpression != null) {
                if(namedExpression.isAnonymous()) {
                    return namedExpression.accept(this);
                }
                else {
                    return namedExpression;
                }
            }
            else {
                return owlClass;
            }
        }

        private Stream<OWLClassExpression> getUnfoldedExpressions(Stream<OWLClassExpression> classExpressionSet) {
            return classExpressionSet.map(ce->ce.accept(this));
        }


        @Override
        public OWLClassExpression visit(OWLObjectIntersectionOf owlObjectIntersectionOf) {
            return dataFactory.getOWLObjectIntersectionOf(getUnfoldedExpressions(owlObjectIntersectionOf.operands()));
        }

        @Override
        public OWLClassExpression visit(OWLObjectUnionOf owlObjectUnionOf) {
            return dataFactory.getOWLObjectUnionOf(getUnfoldedExpressions(owlObjectUnionOf.operands()));
        }

        @Override
        public OWLClassExpression visit(OWLObjectComplementOf owlObjectComplementOf) {
            currentPolarity = currentPolarity.getReversePolarity();
            OWLClassExpression op = owlObjectComplementOf.getOperand().accept(this);
            currentPolarity = currentPolarity.getReversePolarity();
            return dataFactory.getOWLObjectComplementOf(op);
        }

        @Override
        public OWLClassExpression visit(OWLObjectSomeValuesFrom owlObjectSomeValuesFrom) {
            return dataFactory.getOWLObjectSomeValuesFrom(owlObjectSomeValuesFrom.getProperty(), owlObjectSomeValuesFrom.getFiller().accept(this));
        }

        @Override
        public OWLClassExpression visit(OWLObjectAllValuesFrom owlObjectAllValuesFrom) {
            return dataFactory.getOWLObjectAllValuesFrom(owlObjectAllValuesFrom.getProperty(), owlObjectAllValuesFrom.getFiller().accept(this));
        }

        @Override
        public OWLClassExpression visit(OWLObjectHasValue owlObjectHasValue) {
            return owlObjectHasValue;
        }

        @Override
        public OWLClassExpression visit(OWLObjectMinCardinality owlObjectMinCardinality) {
            return dataFactory.getOWLObjectMinCardinality(owlObjectMinCardinality.getCardinality(), owlObjectMinCardinality.getProperty(), owlObjectMinCardinality.getFiller().accept(this));
        }

        @Override
        public OWLClassExpression visit(OWLObjectExactCardinality owlObjectExactCardinality) {
            return dataFactory.getOWLObjectExactCardinality(owlObjectExactCardinality.getCardinality(), owlObjectExactCardinality.getProperty(), owlObjectExactCardinality.getFiller().accept(this));
        }

        @Override
        public OWLClassExpression visit(OWLObjectMaxCardinality owlObjectMaxCardinality) {
            currentPolarity = currentPolarity.getReversePolarity();
            OWLClassExpression filler = owlObjectMaxCardinality.getFiller().accept(this);
            currentPolarity = currentPolarity.getReversePolarity();
            return dataFactory.getOWLObjectMaxCardinality(owlObjectMaxCardinality.getCardinality(), owlObjectMaxCardinality.getProperty(), filler);
        }

        @Override
        public OWLClassExpression visit(OWLObjectHasSelf owlObjectHasSelf) {
            return owlObjectHasSelf;
        }

        @Override
        public OWLClassExpression visit(OWLObjectOneOf owlObjectOneOf) {
            return owlObjectOneOf;
        }

        @Override
        public OWLClassExpression visit(OWLDataSomeValuesFrom owlDataSomeValuesFrom) {
            return owlDataSomeValuesFrom;
        }

        @Override
        public OWLClassExpression visit(OWLDataAllValuesFrom owlDataAllValuesFrom) {
            return owlDataAllValuesFrom;
        }

        @Override
        public OWLClassExpression visit(OWLDataHasValue owlDataHasValue) {
            return owlDataHasValue;
        }

        @Override
        public OWLClassExpression visit(OWLDataMinCardinality owlDataMinCardinality) {
            return owlDataMinCardinality;
        }

        @Override
        public OWLClassExpression visit(OWLDataExactCardinality owlDataExactCardinality) {
            return owlDataExactCardinality;
        }

        @Override
        public OWLClassExpression visit(OWLDataMaxCardinality owlDataMaxCardinality) {
            return owlDataMaxCardinality;
        }
    }
}
