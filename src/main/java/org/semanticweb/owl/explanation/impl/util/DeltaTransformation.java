package org.semanticweb.owl.explanation.impl.util;

import org.semanticweb.owlapi.model.*;

import gnu.trove.map.hash.TObjectIntHashMap;

import static org.semanticweb.owlapi.util.OWLAPIStreamUtils.asSet;

import java.util.*;
import java.util.stream.Stream;

/**
 * Author: Matthew Horridge<br>
 * The University of Manchester<br>
 * Bio-Health Informatics Group<br>
 * Date: 14/02/2011
 */
public class DeltaTransformation implements AxiomTransformation {

    private int freshIRICounter = 0;

    protected OWLDataFactory dataFactory;

    private Set<OWLEntity> freshEntities = new HashSet<>();

    private Set<OWLAxiom> transformedAxioms = new HashSet<>();

    private TObjectIntHashMap<OWLAxiom> namingAxiom2ModalDepth = new TObjectIntHashMap<>(16, 0.75f, 0);

    protected int modalDepth = 0;

    public DeltaTransformation(OWLDataFactory dataFactory) {
        this.dataFactory = dataFactory;
    }

    protected boolean isFreshEntity(OWLEntity entity) {
        return freshEntities.contains(entity);
    }

    protected OWLClass getFreshClass() {
        OWLClass freshClass = dataFactory.getOWLClass(getNextFreshIRI());
        freshEntities.add(freshClass);
        return freshClass;
    }

    private OWLNamedIndividual getFreshIndividual() {
        OWLNamedIndividual freshIndividual = dataFactory.getOWLNamedIndividual(getNextFreshIRI());
        freshEntities.add(freshIndividual);
        return freshIndividual;
    }

    private IRI getNextFreshIRI() {
        freshIRICounter++;
        return IRI.create("http://owlapi.sourceforge.net/transform/flattening#X" + freshIRICounter);
    }

    @Override
    public Set<OWLAxiom> transform(Set<OWLAxiom> axioms) {
        transformedAxioms.clear();
        namingAxiom2ModalDepth.clear();
        AxiomTransformer transformer = new AxiomTransformer();
        for (OWLAxiom ax : axioms) {
            transformedAxioms.addAll(ax.accept(transformer));
        }
        return transformedAxioms;
    }

    public int getModalDepth(OWLAxiom renamingAxiom) {
        return namingAxiom2ModalDepth.get(renamingAxiom);
    }


    private boolean isFreshClass(OWLClassExpression ce) {
        return !ce.isAnonymous() && freshEntities.contains(ce.asOWLClass());
    }

    protected OWLNamedIndividual assignName(OWLIndividual individual) {
        OWLNamedIndividual freshIndividual = getFreshIndividual();
        Set<OWLIndividual> individuals = new HashSet<>();
        individuals.add(individual);
        individuals.add(freshIndividual);
        OWLSameIndividualAxiom namingAxiom = dataFactory.getOWLSameIndividualAxiom(individuals);
        namingAxiom2ModalDepth.put(namingAxiom, modalDepth);
        transformedAxioms.add(namingAxiom);
        return freshIndividual;
    }

    protected OWLClass assignName(OWLClassExpression classExpression, Polarity polarity) {
        if(polarity.isPositive()) {
            if(classExpression.isOWLThing()) {
                return classExpression.asOWLClass();
            }
        }
        else {
            if(classExpression.isOWLNothing()) {
                return classExpression.asOWLClass();
            }
        }
        if(isFreshClass(classExpression)) {
            return classExpression.asOWLClass();
        }
        OWLClass freshClass = getFreshClass();
        return assignName(classExpression, polarity, freshClass);
    }

    protected OWLClass assignName(OWLClassExpression classExpression, Polarity polarity, OWLClass freshClass) {
        OWLSubClassOfAxiom namingAxiom;
        Set<OWLAnnotation> axiomId = Collections.emptySet();
        if (polarity.isPositive()) {
            namingAxiom = dataFactory.getOWLSubClassOfAxiom(freshClass, classExpression, axiomId);
        }
        else {
            namingAxiom = dataFactory.getOWLSubClassOfAxiom(classExpression, freshClass, axiomId);
        }
        namingAxiom2ModalDepth.put(namingAxiom, modalDepth);
        transformedAxioms.add(namingAxiom);
        return freshClass;
    }


    /////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////

    private class AxiomTransformer implements OWLAxiomVisitorEx<Set<OWLAxiom>> {

        private ClassExpressionTransformer positiveTransformer = new ClassExpressionTransformer(Polarity.POSITIVE);

        private ClassExpressionTransformer negativeTransformer = new ClassExpressionTransformer(Polarity.NEGATIVE);

        public AxiomTransformer() {}

        private Set<OWLAxiom> visit(Collection<? extends OWLAxiom> axioms) {
            Set<OWLAxiom> result = new HashSet<>();
            for (OWLAxiom ax : axioms) {
                result.addAll(ax.accept(this));
            }
            return result;
        }

        @Override
        public Set<OWLAxiom> visit(OWLSubClassOfAxiom axiom) {
            OWLClassExpression subClass = axiom.getSubClass().accept(negativeTransformer);
            OWLClass freshSub = assignName(subClass, Polarity.NEGATIVE);

            OWLClassExpression superClass = axiom.getSuperClass().accept(positiveTransformer);
            OWLClass freshSuper = assignName(superClass, Polarity.POSITIVE);

            Set<OWLAxiom> result = new HashSet<>();
            result.add(dataFactory.getOWLSubClassOfAxiom(freshSub, freshSuper));
            return result;

        }

        @Override
        public Set<OWLAxiom> visit(OWLNegativeObjectPropertyAssertionAxiom axiom) {
            return axiom.asOWLSubClassOfAxiom().accept(this);
        }

        @Override
        public Set<OWLAxiom> visit(OWLAsymmetricObjectPropertyAxiom axiom) {
            return Collections.<OWLAxiom>singleton(axiom);
        }

        @Override
        public Set<OWLAxiom> visit(OWLReflexiveObjectPropertyAxiom axiom) {
            return Collections.<OWLAxiom>singleton(axiom);
        }

        @Override
        public Set<OWLAxiom> visit(OWLDisjointClassesAxiom axiom) {
            // TODO: FIX!
//            for(OWLAxiom ax : axiom.asOWLSubClassOfAxioms()) {
//                result.addAll(ax.accept(this));
//            }
            return Collections.<OWLAxiom>singleton(axiom);
        }

        @Override
        public Set<OWLAxiom> visit(OWLDataPropertyDomainAxiom axiom) {
            return axiom.asOWLSubClassOfAxiom().accept(this);
        }

        @Override
        public Set<OWLAxiom> visit(OWLObjectPropertyDomainAxiom axiom) {
            OWLClassExpression renamedDomain = axiom.getDomain().accept(positiveTransformer);
            OWLAxiom transformed = dataFactory.getOWLObjectPropertyDomainAxiom(axiom.getProperty(), renamedDomain);
            return Collections.singleton(transformed);
        }

        @Override
        public Set<OWLAxiom> visit(OWLEquivalentObjectPropertiesAxiom axiom) {
            return visit(axiom.asSubObjectPropertyOfAxioms());
        }

        @Override
        public Set<OWLAxiom> visit(OWLNegativeDataPropertyAssertionAxiom axiom) {
            return axiom.asOWLSubClassOfAxiom().accept(this);
        }

        @Override
        public Set<OWLAxiom> visit(OWLDifferentIndividualsAxiom axiom) {
            Set<OWLIndividual> renamed = asSet(axiom.individuals().map(ind -> assignName(ind)));
            return Collections.<OWLAxiom>singleton(dataFactory.getOWLDifferentIndividualsAxiom(renamed));
        }

        @Override
        public Set<OWLAxiom> visit(OWLDisjointDataPropertiesAxiom axiom) {
            return Collections.<OWLAxiom>singleton(axiom);
        }

        @Override
        public Set<OWLAxiom> visit(OWLDisjointObjectPropertiesAxiom axiom) {
            return Collections.<OWLAxiom>singleton(axiom);
        }

        @Override
        public Set<OWLAxiom> visit(OWLObjectPropertyRangeAxiom axiom) {
            modalDepth++;
            OWLClassExpression renamedRange = axiom.getRange().accept(positiveTransformer);
            modalDepth--;
            OWLAxiom transformed = dataFactory.getOWLObjectPropertyRangeAxiom(axiom.getProperty(), renamedRange);
            return Collections.singleton(transformed);
        }

        @Override
        public Set<OWLAxiom> visit(OWLObjectPropertyAssertionAxiom axiom) {
            OWLNamedIndividual renamedSubject = assignName(axiom.getSubject());
            OWLNamedIndividual renamedObject = assignName(axiom.getObject());
            OWLObjectPropertyAssertionAxiom flattendAx = dataFactory.getOWLObjectPropertyAssertionAxiom(axiom.getProperty(), renamedSubject, renamedObject);
            return Collections.<OWLAxiom>singleton(flattendAx);
        }

        @Override
        public Set<OWLAxiom> visit(OWLFunctionalObjectPropertyAxiom axiom) {
            return Collections.<OWLAxiom>singleton(axiom);
        }

        @Override
        public Set<OWLAxiom> visit(OWLSubObjectPropertyOfAxiom axiom) {
            return Collections.<OWLAxiom>singleton(axiom);
        }

        @Override
        public Set<OWLAxiom> visit(OWLDisjointUnionAxiom axiom) {
            Set<OWLAxiom> result = new HashSet<>();
            result.addAll(axiom.getOWLDisjointClassesAxiom().accept(this));
            result.addAll(axiom.getOWLEquivalentClassesAxiom().accept(this));
            return result;
        }

        @Override
        public Set<OWLAxiom> visit(OWLDeclarationAxiom axiom) {
            return Collections.<OWLAxiom>singleton(axiom);
        }

        @Override
        public Set<OWLAxiom> visit(OWLAnnotationAssertionAxiom axiom) {
            return Collections.<OWLAxiom>singleton(axiom);
        }

        @Override
        public Set<OWLAxiom> visit(OWLSymmetricObjectPropertyAxiom axiom) {
            return Collections.<OWLAxiom>singleton(axiom);
        }

        @Override
        public Set<OWLAxiom> visit(OWLDataPropertyRangeAxiom axiom) {
            return Collections.<OWLAxiom>singleton(axiom);
        }

        @Override
        public Set<OWLAxiom> visit(OWLFunctionalDataPropertyAxiom axiom) {
            return Collections.<OWLAxiom>singleton(axiom);
        }

        @Override
        public Set<OWLAxiom> visit(OWLEquivalentDataPropertiesAxiom axiom) {
            return visit(axiom.asSubDataPropertyOfAxioms());
        }

        @Override
        public Set<OWLAxiom> visit(OWLClassAssertionAxiom axiom) {
            OWLClass renamedCls = assignName(axiom.getClassExpression(), Polarity.POSITIVE);
            OWLNamedIndividual renamedInd = assignName(axiom.getIndividual());
            OWLClassAssertionAxiom flattenedAx = dataFactory.getOWLClassAssertionAxiom(renamedCls, renamedInd);
            return Collections.<OWLAxiom>singleton(flattenedAx);
        }

        @Override
        public Set<OWLAxiom> visit(OWLEquivalentClassesAxiom axiom) {
            return visit(axiom.asOWLSubClassOfAxioms());
        }

        @Override
        public Set<OWLAxiom> visit(OWLDataPropertyAssertionAxiom axiom) {
            return axiom.asOWLSubClassOfAxiom().accept(this);
        }

        @Override
        public Set<OWLAxiom> visit(OWLTransitiveObjectPropertyAxiom axiom) {
            return Collections.<OWLAxiom>singleton(axiom);
        }

        @Override
        public Set<OWLAxiom> visit(OWLIrreflexiveObjectPropertyAxiom axiom) {
            return Collections.<OWLAxiom>singleton(axiom);
        }

        @Override
        public Set<OWLAxiom> visit(OWLSubDataPropertyOfAxiom axiom) {
            return Collections.<OWLAxiom>singleton(axiom);
        }

        @Override
        public Set<OWLAxiom> visit(OWLInverseFunctionalObjectPropertyAxiom axiom) {
            return Collections.<OWLAxiom>singleton(axiom);
        }

        @Override
        public Set<OWLAxiom> visit(OWLSameIndividualAxiom axiom) {
            Set<OWLNamedIndividual> renamed = asSet(axiom.individuals().map(ind -> assignName(ind)));
            return Collections.<OWLAxiom>singleton(dataFactory.getOWLSameIndividualAxiom(renamed));

        }

        @Override
        public Set<OWLAxiom> visit(OWLSubPropertyChainOfAxiom axiom) {
            return Collections.<OWLAxiom>singleton(axiom);
        }

        @Override
        public Set<OWLAxiom> visit(OWLInverseObjectPropertiesAxiom axiom) {
            return visit(axiom.asSubObjectPropertyOfAxioms());
        }

        @Override
        public Set<OWLAxiom> visit(OWLHasKeyAxiom axiom) {
            return Collections.<OWLAxiom>singleton(axiom);
        }

        @Override
        public Set<OWLAxiom> visit(OWLDatatypeDefinitionAxiom axiom) {
            return Collections.<OWLAxiom>singleton(axiom);
        }

        @Override
        public Set<OWLAxiom> visit(SWRLRule rule) {
            return Collections.<OWLAxiom>singleton(rule);
        }

        @Override
        public Set<OWLAxiom> visit(OWLSubAnnotationPropertyOfAxiom axiom) {
            return Collections.<OWLAxiom>singleton(axiom);
        }

        @Override
        public Set<OWLAxiom> visit(OWLAnnotationPropertyDomainAxiom axiom) {
            return Collections.<OWLAxiom>singleton(axiom);
        }

        @Override
        public Set<OWLAxiom> visit(OWLAnnotationPropertyRangeAxiom axiom) {
            return Collections.<OWLAxiom>singleton(axiom);
        }
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////

    private class ClassExpressionTransformer implements OWLClassExpressionVisitorEx<OWLClassExpression> {

        private Polarity polarity;

        protected ClassExpressionTransformer(Polarity polarity) {
            this.polarity = polarity;
        }

        private Set<OWLClassExpression> getRenamedClasses(Stream<OWLClassExpression> classes, boolean useSameName) {
            if(useSameName) {
                OWLClass name = getFreshClass();
                classes.forEach(ce -> assignByPolarity(name, ce));
                return Collections.<OWLClassExpression>singleton(name);
            }
            return asSet(
                classes
                    .map(cls->cls.accept(this))
                    .map(transCls->assignName(transCls, polarity)));
        }

        protected void assignByPolarity(OWLClass name, OWLClassExpression ce) {
            if(polarity.isPositive()) {
                if(!ce.isOWLThing()) {
                    OWLClassExpression ceP = ce.accept(this);
                    assignName(ceP, Polarity.POSITIVE, name);
                }
            }
            else {
                if(!ce.isOWLNothing()) {
                    OWLClassExpression ceP = ce.accept(this);
                    assignName(ceP, Polarity.NEGATIVE, name);
                }
            }
        }

        @Override
        public OWLClass visit(OWLClass ce) {
            return ce;
        }

        @Override
        public OWLClassExpression visit(OWLObjectIntersectionOf ce) {
            Set<OWLClassExpression> renamedOperands = getRenamedClasses(ce.operands(), polarity.isPositive());
            if (renamedOperands.size() == 1) {
                return renamedOperands.iterator().next();
            }
            else {
                return dataFactory.getOWLObjectIntersectionOf(renamedOperands);
            }
        }

        @Override
        public OWLClassExpression visit(OWLObjectUnionOf ce) {
            Set<OWLClassExpression> renamedOperands = getRenamedClasses(ce.operands(), !polarity.isPositive());
            if(renamedOperands.size() == 1) {
                return renamedOperands.iterator().next();
            }
            else {
                return dataFactory.getOWLObjectUnionOf(renamedOperands);
            }
        }

        @Override
        public OWLClassExpression visit(OWLObjectComplementOf ce) {
            polarity = polarity.getReversePolarity();
            OWLClassExpression renamedComplement = assignName(ce.getOperand().accept(this), polarity);
            polarity = polarity.getReversePolarity();
            return dataFactory.getOWLObjectComplementOf(renamedComplement);
        }

        @Override
        public OWLClassExpression visit(OWLObjectSomeValuesFrom ce) {
            modalDepth++;
            OWLClassExpression renamedFiller = assignName(ce.getFiller().accept(this), polarity);
            modalDepth--;
            OWLObjectPropertyExpression property = ce.getProperty();
            return dataFactory.getOWLObjectSomeValuesFrom(property, renamedFiller);
        }

        @Override
        public OWLClassExpression visit(OWLObjectAllValuesFrom ce) {
            modalDepth++;
            OWLClassExpression renamedFiller = assignName(ce.getFiller().accept(this), polarity);
            modalDepth--;
            OWLObjectPropertyExpression property = ce.getProperty();
            return dataFactory.getOWLObjectAllValuesFrom(property, renamedFiller);
        }

        @Override
        public OWLClassExpression visit(OWLObjectHasValue ce) {
            modalDepth++;
            OWLNamedIndividual renamedInd = assignName(ce.getFiller());
            modalDepth--;
            return dataFactory.getOWLObjectHasValue(ce.getProperty(), renamedInd);
        }

        @Override
        public OWLClassExpression visit(OWLObjectMinCardinality ce) {
            modalDepth++;
            OWLClassExpression renamedFiller = assignName(ce.getFiller().accept(this), polarity);
            modalDepth--;
            OWLObjectPropertyExpression prop = ce.getProperty();
            int cardi = ce.getCardinality();
            return dataFactory.getOWLObjectMinCardinality(cardi, prop, renamedFiller);
        }

        @Override
        public OWLClassExpression visit(OWLObjectExactCardinality ce) {
            return ce.asIntersectionOfMinMax().accept(this);
        }

        @Override
        public OWLClassExpression visit(OWLObjectMaxCardinality ce) {
            polarity = polarity.getReversePolarity();
            modalDepth++;
            OWLClass renamedFiller = assignName(ce.getFiller().accept(this), polarity);
            modalDepth--;
            polarity = polarity.getReversePolarity();
            OWLObjectPropertyExpression prop = ce.getProperty();
            int cardi = ce.getCardinality();
            return dataFactory.getOWLObjectMaxCardinality(cardi, prop, renamedFiller);
        }

        @Override
        public OWLClassExpression visit(OWLObjectHasSelf ce) {
            return ce;
        }

        @Override
        public OWLClassExpression visit(OWLObjectOneOf ce) {
            return dataFactory.getOWLObjectOneOf(ce.individuals().map(ind -> assignName(ind)));
        }

        @Override
        public OWLClassExpression visit(OWLDataSomeValuesFrom ce) {
            return ce;
        }

        @Override
        public OWLClassExpression visit(OWLDataAllValuesFrom ce) {
            return ce;
        }

        @Override
        public OWLClassExpression visit(OWLDataHasValue ce) {
            return ce;
        }

        @Override
        public OWLClassExpression visit(OWLDataMinCardinality ce) {
            return ce;
        }

        @Override
        public OWLClassExpression visit(OWLDataExactCardinality ce) {
            return ce;
        }

        @Override
        public OWLClassExpression visit(OWLDataMaxCardinality ce) {
            return ce;
        }
    }
}
