package org.semanticweb.owl.explanation.impl.blackbox.checker;

import org.semanticweb.owl.explanation.api.ExplanationException;
import org.semanticweb.owl.explanation.api.ExplanationGeneratorInterruptedException;
import org.semanticweb.owl.explanation.telemetry.DefaultTelemetryInfo;
import org.semanticweb.owl.explanation.telemetry.TelemetryInfo;
import org.semanticweb.owl.explanation.telemetry.TelemetryTimer;
import org.semanticweb.owl.explanation.telemetry.TelemetryTransmitter;
import org.semanticweb.owl.explanation.impl.blackbox.EntailmentChecker;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.*;
import uk.ac.manchester.cs.owlapi.modularity.SyntacticLocalityModuleExtractor;
import uk.ac.manchester.cs.owlapi.modularity.ModuleType;

import static org.semanticweb.owlapi.util.OWLAPIStreamUtils.add;
import static org.semanticweb.owlapi.util.OWLAPIStreamUtils.asList;
import static org.semanticweb.owlapi.util.OWLAPIStreamUtils.asSet;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
public class SatisfiabilityEntailmentChecker implements EntailmentChecker<OWLAxiom> {

    protected OWLOntologyManager man;
    
    private Supplier<OWLOntologyManager> m;

    protected OWLAxiom axiom;

    private OWLClassExpression unsatDesc;

    protected Set<OWLEntity> freshEntities;

    private OWLReasonerFactory reasonerFactory;

    private Set<OWLAxiom> seedSignature;

    private boolean useModularisation;

    final private Set<OWLAxiom> lastAxioms;

    final private Set<OWLAxiom> lastEntailingAxioms;

    private int counter = 0;

    private ModuleType moduleType = ModuleType.STAR;

    private long timeOutMS = Long.MAX_VALUE;

    public SatisfiabilityEntailmentChecker(OWLReasonerFactory reasonerFactory, OWLAxiom entailment, Supplier<OWLOntologyManager> m) {
        this(reasonerFactory, entailment, m, true, Long.MAX_VALUE);
    }

    public SatisfiabilityEntailmentChecker(OWLReasonerFactory reasonerFactory, OWLAxiom entailment, Supplier<OWLOntologyManager> m, boolean useModularisation, long timeOutMS) {
        this.reasonerFactory = reasonerFactory;
        this.axiom = entailment;
        this.useModularisation = useModularisation;
        this.timeOutMS = timeOutMS;
        this.seedSignature = new HashSet<>();
        this.lastAxioms = new HashSet<>();
        this.lastEntailingAxioms = new HashSet<>();
        freshEntities = new HashSet<>();
        this.man = m.get();
        this.m = m;

        if (entailment instanceof OWLSubClassOfAxiom && ((OWLSubClassOfAxiom) entailment).getSuperClass().isOWLNothing()) {
            unsatDesc = ((OWLSubClassOfAxiom) entailment).getSubClass();
        }
        else {
            SatisfiabilityConverter con = new SatisfiabilityConverter();
            unsatDesc = entailment.accept(con);
        }
    }

//    public static int getTimeOut() {
//        return TIME_OUT;
//    }
//
//    public static void setTimeOut(int timeOut) {
//        SatisfiabilityEntailmentChecker.TIME_OUT = timeOut;
//    }

    @Override
    public String getModularisationTypeDescription() {
        return moduleType.toString();
    }

    @Override
    public boolean isUseModularisation() {
        return useModularisation;
    }

    @Override
    public OWLAxiom getEntailment() {
        return axiom;
    }


    @Override
    public Set<OWLEntity> getEntailmentSignature() {
        return asSet(axiom.signature());
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
    public Set<OWLEntity> getSeedSignature() {
        if (axiom instanceof OWLSubClassOfAxiom) {
            return asSet(((OWLSubClassOfAxiom) axiom).getSubClass().signature());
        }
        else {
            return asSet(axiom.signature());
        }
    }

    @Override
    public Set<OWLAxiom> getModule(Set<OWLAxiom> axioms) {

        if (useModularisation) {
            if (axioms.isEmpty()) {
                return Collections.emptySet();
            }

            moduleType = ModuleType.STAR;
            SyntacticLocalityModuleExtractor extractor = new SyntacticLocalityModuleExtractor(m.get(), axioms.stream(), moduleType);
            return extractor.extract(getEntailmentSignature());
        }
        else {
            return axioms;
        }
    }

    private static OWLOntology createEmptyOntology(OWLOntologyManager man) {
        try {
            return man.createOntology();
        } catch (OWLOntologyCreationException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean containsNominals(OWLAxiom axiom) {
        return axiom.nestedClassExpressions().anyMatch(ce->ce instanceof OWLObjectOneOf);
    }

    @Override
    public boolean isEntailed(Set<OWLAxiom> axioms) {

        TelemetryTimer totalTimer = new TelemetryTimer();
        TelemetryTimer moduleTimer = new TelemetryTimer();
        TelemetryTimer entailmentCheckTimer = new TelemetryTimer();
        TelemetryInfo info = new DefaultTelemetryInfo("entailmentcheck", false, totalTimer, moduleTimer, entailmentCheckTimer);
        final TelemetryTransmitter transmitter = TelemetryTransmitter.getTransmitter();

        transmitter.beginTransmission(info);
        boolean entailed = true;
        OWLOntology toSave = null;
        try {
//            transmitter.recordObject(info, "entailment", "", getEntailment());
            transmitter.recordMeasurement(info, "input size", axioms.size());
            totalTimer.start();

            lastEntailingAxioms.clear();
            lastAxioms.clear();
            lastAxioms.addAll(axioms);

            if (axioms.contains(axiom)) {
                lastEntailingAxioms.add(axiom);
                return true;
            }



            OWLOntology ont = man.createOntology(axioms);
            toSave = ont;
            // Previously, I had coded the checker so that we broke out if the
            // signature of the unsatDesc was not totally contained in set of axioms.
            // However, if a GCI was in the set of axioms, for example an object
            // property domain checker, then this could cause erronous results.  We
            // now add in the signature using declaration axioms.

            OWLDataFactory df=man.getOWLDataFactory();
            unsatDesc.signature()
                .filter(ent->!ent.isBuiltIn() && !ont.containsEntityInSignature(ent))
                .forEach(ent -> ont.add(df.getOWLDeclarationAxiom(ent)));

            String clsName = "Entailment" + System.currentTimeMillis();
            OWLClass namingCls = man.getOWLDataFactory().getOWLClass(IRI.create(clsName));
            OWLAxiom namingAxiom = man.getOWLDataFactory().getOWLSubClassOfAxiom(namingCls, unsatDesc);
            man.addAxiom(ont, namingAxiom);
            for (OWLEntity freshEntity : freshEntities) {
                man.addAxiom(ont, man.getOWLDataFactory().getOWLDeclarationAxiom(freshEntity));
            }

            // Do the actual entailment check
            counter++;
            entailmentCheckTimer.start();
            OWLReasoner reasoner = reasonerFactory.createReasoner(ont, new SimpleConfiguration(new NullReasonerProgressMonitor(), FreshEntityPolicy.ALLOW, timeOutMS, IndividualNodeSetPolicy.BY_SAME_AS));
            entailed = !reasoner.isSatisfiable(unsatDesc);
            entailmentCheckTimer.stop();


            reasoner.dispose();
            man.removeOntology(ont);
            if (entailed) {
                lastEntailingAxioms.remove(namingAxiom);
                add(lastEntailingAxioms, ont.logicalAxioms());
            }
            return entailed;
        }
        catch (OWLOntologyCreationException e) {
            throw new ExplanationException(e);
        }
        catch (TimeOutException e) {
            transmitter.recordMeasurement(info, "reasoner time out", true);
            throw e;
        }
        catch (ExplanationGeneratorInterruptedException e) {
            transmitter.recordMeasurement(info, "interrupted", true);
            throw e;
        }
        catch (RuntimeException e) {
            if (toSave != null) {
                try (FileOutputStream out = new FileOutputStream(new File("/tmp/lasterror.owl"))) {
                    toSave.saveOntology(out);
                } catch (OWLOntologyStorageException e1) {
                    e1.printStackTrace();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
            transmitter.recordException(info, e);
            throw e;
        }
        finally {
            totalTimer.stop();
            transmitter.recordTiming(info, "satisfiability check time", entailmentCheckTimer);
            transmitter.recordMeasurement(info, "entailed", entailed);
            transmitter.recordTiming(info, "time", totalTimer);
            transmitter.endTransmission(info);
        }
    }


    @Override
    public Set<OWLAxiom> getEntailingAxioms(Set<OWLAxiom> axioms) {
        if (!axioms.equals(lastAxioms)) {
            isEntailed(axioms);
        }
        return lastEntailingAxioms;
    }



    private class AxiomsSplitter implements OWLAxiomVisitor {

        private Set<OWLAxiom> aboxAxioms;

        private Set<OWLAxiom> tboxAxioms;

        private AxiomsSplitter(int size) {
            aboxAxioms = new HashSet<>(size);
            tboxAxioms = new HashSet<>(size);
        }

        public Set<OWLAxiom> getAboxAxioms() {
            return aboxAxioms;
        }

        public Set<OWLAxiom> getTboxAxioms() {
            return tboxAxioms;
        }

        @Override
        public void visit(OWLDeclarationAxiom axiom) {
        }



        @Override
        public void visit(OWLSubClassOfAxiom axiom) {
            tboxAxioms.add(axiom);

        }

        @Override
        public void visit(OWLNegativeObjectPropertyAssertionAxiom axiom) {
            tboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLAsymmetricObjectPropertyAxiom axiom) {
            tboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLReflexiveObjectPropertyAxiom axiom) {
            tboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLDisjointClassesAxiom axiom) {
            tboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLDataPropertyDomainAxiom axiom) {
            tboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLObjectPropertyDomainAxiom axiom) {
            tboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLEquivalentObjectPropertiesAxiom axiom) {
            tboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLNegativeDataPropertyAssertionAxiom axiom) {
            tboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLDifferentIndividualsAxiom axiom) {
            aboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLDisjointDataPropertiesAxiom axiom) {
            tboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLDisjointObjectPropertiesAxiom axiom) {
            tboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLObjectPropertyRangeAxiom axiom) {
            tboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLObjectPropertyAssertionAxiom axiom) {
            aboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLFunctionalObjectPropertyAxiom axiom) {
            tboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLSubObjectPropertyOfAxiom axiom) {
            tboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLDisjointUnionAxiom axiom) {
            tboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLSymmetricObjectPropertyAxiom axiom) {
            tboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLDataPropertyRangeAxiom axiom) {
            tboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLFunctionalDataPropertyAxiom axiom) {
            tboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLEquivalentDataPropertiesAxiom axiom) {
            tboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLClassAssertionAxiom axiom) {
            aboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLEquivalentClassesAxiom axiom) {
            tboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLDataPropertyAssertionAxiom axiom) {
            aboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLTransitiveObjectPropertyAxiom axiom) {
            tboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLIrreflexiveObjectPropertyAxiom axiom) {
            tboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLSubDataPropertyOfAxiom axiom) {
            tboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLInverseFunctionalObjectPropertyAxiom axiom) {
            tboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLSameIndividualAxiom axiom) {
            aboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLSubPropertyChainOfAxiom axiom) {
            tboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLInverseObjectPropertiesAxiom axiom) {
            tboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLHasKeyAxiom axiom) {
            tboxAxioms.add(axiom);
        }

        @Override
        public void visit(OWLDatatypeDefinitionAxiom axiom) {
            tboxAxioms.add(axiom);
        }

        @Override
        public void visit(SWRLRule rule) {
            tboxAxioms.add(axiom);
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



    private class SatisfiabilityConverter implements OWLAxiomVisitorEx<OWLClassExpression> {

        private OWLDataFactory df = man.getOWLDataFactory();

        @Override
        public OWLClassExpression visit(OWLAsymmetricObjectPropertyAxiom axiom) {
            throw new UnsupportedAxiomTypeException(axiom);
        }


        @Override
        public OWLClassExpression visit(OWLClassAssertionAxiom axiom) {
            OWLClassExpression nominal = df.getOWLObjectOneOf(axiom.getIndividual());
            return df.getOWLObjectIntersectionOf(nominal, df.getOWLObjectComplementOf(axiom.getClassExpression()));
        }


        @Override
        public OWLClassExpression visit(OWLDataPropertyAssertionAxiom axiom) {
            OWLClassExpression nom = df.getOWLObjectOneOf(axiom.getSubject());
            OWLClassExpression hasVal = df.getOWLDataHasValue(axiom.getProperty(), axiom.getObject());
            return df.getOWLObjectIntersectionOf(nom, df.getOWLObjectComplementOf(hasVal));
        }


        @Override
        public OWLClassExpression visit(OWLDataPropertyDomainAxiom axiom) {
            OWLClassExpression exists = df.getOWLDataSomeValuesFrom(axiom.getProperty(), df.getTopDatatype());
            return df.getOWLObjectIntersectionOf(exists, df.getOWLObjectComplementOf(axiom.getDomain()));
        }


        @Override
        public OWLClassExpression visit(OWLDataPropertyRangeAxiom axiom) {
            OWLClassExpression forall = df.getOWLDataAllValuesFrom(axiom.getProperty(), axiom.getRange());
            return df.getOWLObjectIntersectionOf(df.getOWLThing(), df.getOWLObjectComplementOf(forall));
        }


        @Override
        public OWLClassExpression visit(OWLSubDataPropertyOfAxiom axiom) {
            OWLLiteral c = df.getOWLLiteral("x");
            OWLClassExpression subHasValue = df.getOWLDataHasValue(axiom.getSubProperty(), c);
            OWLClassExpression supHasValue = df.getOWLDataHasValue(axiom.getSuperProperty(), c);
            return df.getOWLObjectIntersectionOf(subHasValue, df.getOWLObjectComplementOf(supHasValue));
        }


        @Override
        public OWLClassExpression visit(OWLDeclarationAxiom axiom) {
            throw new UnsupportedAxiomTypeException(axiom);
        }


        @Override
        public OWLClassExpression visit(OWLDifferentIndividualsAxiom axiom) {
            throw new UnsupportedAxiomTypeException(axiom);
        }


        @Override
        public OWLClassExpression visit(OWLDisjointClassesAxiom axiom) {
            return df.getOWLObjectIntersectionOf(axiom.classExpressions());
        }


        @Override
        public OWLClassExpression visit(OWLDisjointDataPropertiesAxiom axiom) {
            throw new UnsupportedAxiomTypeException(axiom);
        }


        @Override
        public OWLClassExpression visit(OWLDisjointObjectPropertiesAxiom axiom) {
            throw new UnsupportedAxiomTypeException(axiom);
        }


        @Override
        public OWLClassExpression visit(OWLDisjointUnionAxiom axiom) {
            throw new UnsupportedAxiomTypeException(axiom);
        }


        @Override
        public OWLClassExpression visit(OWLAnnotationAssertionAxiom axiom) {
            throw new UnsupportedAxiomTypeException(axiom);
        }


        @Override
        public OWLClassExpression visit(OWLEquivalentClassesAxiom axiom) {
            List<OWLClassExpression> expressions=asList(axiom.classExpressions());
            if (expressions.size() != 2) {
                throw new UnsupportedAxiomTypeException(axiom);
            }

            OWLClassExpression d1 = expressions.get(0);
            OWLClassExpression d2 = expressions.get(1);

            if (d1.isOWLNothing()) {
                return d2;
            }
            else if (d2.isOWLNothing()) {
                return d1;
            }
            else if (d1.isOWLThing()) {
                return df.getOWLObjectComplementOf(d2);
            }
            else if (d2.isOWLThing()) {
                return df.getOWLObjectComplementOf(d1);
            }
            else {
                return df.getOWLObjectUnionOf(df.getOWLObjectIntersectionOf(d1, df.getOWLObjectComplementOf(d2)), df.getOWLObjectIntersectionOf(df.getOWLObjectComplementOf(d1), d2));
            }
        }


        @Override
        public OWLClassExpression visit(OWLEquivalentDataPropertiesAxiom axiom) {
            throw new UnsupportedAxiomTypeException(axiom);
        }


        @Override
        public OWLClassExpression visit(OWLEquivalentObjectPropertiesAxiom axiom) {
            throw new UnsupportedAxiomTypeException(axiom);
        }


        @Override
        public OWLClassExpression visit(OWLFunctionalDataPropertyAxiom axiom) {
            return axiom.asOWLSubClassOfAxiom().accept(this);
        }


        @Override
        public OWLClassExpression visit(OWLFunctionalObjectPropertyAxiom axiom) {
            return axiom.asOWLSubClassOfAxiom().accept(this);
        }

        @Override
        public OWLClassExpression visit(OWLInverseFunctionalObjectPropertyAxiom axiom) {
            return axiom.asOWLSubClassOfAxiom().accept(this);
        }


        @Override
        public OWLClassExpression visit(OWLInverseObjectPropertiesAxiom axiom) {
            OWLClass clsA = df.getOWLClass(IRI.create("owlapi:explanation:clsA"));
            freshEntities.add(clsA);
            OWLClass clsB = df.getOWLClass(IRI.create("owlapi:explanation:clsB"));
            freshEntities.add(clsB);
            OWLClassExpression subHasValueA = df.getOWLObjectSomeValuesFrom(axiom.getFirstProperty(), clsA);
            OWLClassExpression supHasValueA = df.getOWLObjectSomeValuesFrom(axiom.getSecondProperty().getInverseProperty(), clsA);
            OWLClassExpression subHasValueB = df.getOWLObjectSomeValuesFrom(axiom.getSecondProperty(), clsB);
            OWLClassExpression supHasValueB = df.getOWLObjectSomeValuesFrom(axiom.getFirstProperty().getInverseProperty(), clsB);
            OWLClassExpression ceA = df.getOWLObjectIntersectionOf(subHasValueA, df.getOWLObjectComplementOf(supHasValueA));
            OWLClassExpression ceB = df.getOWLObjectIntersectionOf(subHasValueB, df.getOWLObjectComplementOf(supHasValueB));
            return df.getOWLObjectUnionOf(ceA, ceB);
        }


        @Override
        public OWLClassExpression visit(OWLIrreflexiveObjectPropertyAxiom axiom) {
            return axiom.asOWLSubClassOfAxiom().accept(this);
        }


        @Override
        public OWLClassExpression visit(OWLNegativeDataPropertyAssertionAxiom axiom) {
            return axiom.asOWLSubClassOfAxiom().accept(this);
        }


        @Override
        public OWLClassExpression visit(OWLNegativeObjectPropertyAssertionAxiom axiom) {
            return axiom.asOWLSubClassOfAxiom().accept(this);
        }


        @Override
        public OWLClassExpression visit(OWLHasKeyAxiom owlHasKeyAxiom) {
            throw new UnsupportedAxiomTypeException(axiom);
        }


        @Override
        public OWLClassExpression visit(OWLDatatypeDefinitionAxiom owlDatatypeDefinition) {
            throw new UnsupportedAxiomTypeException(axiom);
        }


        @Override
        public OWLClassExpression visit(OWLSubAnnotationPropertyOfAxiom owlSubAnnotationPropertyOfAxiom) {
            throw new UnsupportedAxiomTypeException(axiom);
        }


        @Override
        public OWLClassExpression visit(OWLAnnotationPropertyDomainAxiom owlAnnotationPropertyDomainAxiom) {
            throw new UnsupportedAxiomTypeException(axiom);
        }


        @Override
        public OWLClassExpression visit(OWLAnnotationPropertyRangeAxiom owlAnnotationPropertyRangeAxiom) {
            throw new UnsupportedAxiomTypeException(axiom);
        }


        @Override
        public OWLClassExpression visit(OWLObjectPropertyAssertionAxiom axiom) {
            OWLClassExpression nom = df.getOWLObjectOneOf(axiom.getSubject());
            OWLClassExpression hasVal = df.getOWLObjectHasValue(axiom.getProperty(), axiom.getObject());
            return df.getOWLObjectIntersectionOf(nom, df.getOWLObjectComplementOf(hasVal));
        }


        @Override
        public OWLClassExpression visit(OWLSubPropertyChainOfAxiom axiom) {
            throw new UnsupportedAxiomTypeException(axiom);
        }


        @Override
        public OWLClassExpression visit(OWLObjectPropertyDomainAxiom axiom) {
            OWLClassExpression exists = df.getOWLObjectSomeValuesFrom(axiom.getProperty(), df.getOWLThing());
            return df.getOWLObjectIntersectionOf(exists, df.getOWLObjectComplementOf(axiom.getDomain()));
        }


        @Override
        public OWLClassExpression visit(OWLObjectPropertyRangeAxiom axiom) {
            OWLClassExpression forall = df.getOWLObjectAllValuesFrom(axiom.getProperty(), axiom.getRange());
            return df.getOWLObjectIntersectionOf(df.getOWLThing(), df.getOWLObjectComplementOf(forall));
        }


        @Override
        public OWLClassExpression visit(OWLSubObjectPropertyOfAxiom axiom) {
            OWLClass clsA = df.getOWLClass(IRI.create("owlapi:explanation:clsA"));
            freshEntities.add(clsA);
            OWLClassExpression subHasValue = df.getOWLObjectSomeValuesFrom(axiom.getSubProperty(), clsA);
            OWLClassExpression supHasValue = df.getOWLObjectSomeValuesFrom(axiom.getSuperProperty(), clsA);
            return df.getOWLObjectIntersectionOf(subHasValue, df.getOWLObjectComplementOf(supHasValue));
        }


        @Override
        public OWLClassExpression visit(OWLReflexiveObjectPropertyAxiom axiom) {
            return df.getOWLObjectHasSelf(axiom.getProperty()).getObjectComplementOf();
        }


        @Override
        public OWLClassExpression visit(OWLSameIndividualAxiom axiom) {
            throw new UnsupportedAxiomTypeException(axiom);
        }


        @Override
        public OWLClassExpression visit(OWLSubClassOfAxiom axiom) {
            return man.getOWLDataFactory().getOWLObjectIntersectionOf(axiom.getSubClass(), man.getOWLDataFactory().getOWLObjectComplementOf(axiom.getSuperClass()));
        }


        @Override
        public OWLClassExpression visit(OWLSymmetricObjectPropertyAxiom axiom) {
            throw new UnsupportedAxiomTypeException(axiom);
        }


        @Override
        public OWLClassExpression visit(OWLTransitiveObjectPropertyAxiom axiom) {
            OWLClass clsA = df.getOWLClass(IRI.create("owlapi:explanation:clsA"));
            freshEntities.add(clsA);
            OWLClassExpression subHasValue = df.getOWLObjectSomeValuesFrom(axiom.getProperty(), df.getOWLObjectSomeValuesFrom(axiom.getProperty(), clsA));
            OWLClassExpression supHasValue = df.getOWLObjectSomeValuesFrom(axiom.getProperty(), clsA);
            return df.getOWLObjectIntersectionOf(subHasValue, df.getOWLObjectComplementOf(supHasValue));
        }


        @Override
        public OWLClassExpression visit(SWRLRule rule) {
            throw new UnsupportedAxiomTypeException(rule);
        }
    }


    public static class UnsupportedAxiomTypeException extends RuntimeException {

        private AxiomType type;


        public UnsupportedAxiomTypeException(OWLAxiom ax) {
            super("Unsupported type of axiom: " + ax.getAxiomType().getName());
            type = ax.getAxiomType();
        }


        public AxiomType getType() {
            return type;
        }
    }
}
