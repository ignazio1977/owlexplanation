package org.semanticweb.owl.explanation.impl.blackbox;

import java.util.Collections;

import org.junit.Test;
import org.semanticweb.owl.explanation.impl.util.DeltaTransformation;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.util.DefaultPrefixManager;

import openllet.owlapi.OWLManagerGroup;

public class DeltaTransformationTestCase {
    @Test
    public void main() {
        OWLDataFactory df = OWLManager.getOWLDataFactory();
        DefaultPrefixManager pm = new DefaultPrefixManager("http://test.com#");
        OWLClass A = df.getOWLClass(":A", pm);
        OWLClass B = df.getOWLClass(":B", pm);
        OWLClass C = df.getOWLClass(":C", pm);
        OWLObjectProperty prop = df.getOWLObjectProperty(":p", pm);
        OWLIndividual i = df.getOWLNamedIndividual(":i", pm);
        OWLIndividual j = df.getOWLNamedIndividual(":j", pm);
        OWLIndividual k = df.getOWLNamedIndividual(":k", pm);
        OWLIndividual l = df.getOWLNamedIndividual(":l", pm);
        // OWLAxiom ax = SubClassOf(A, ObjectIntersectionOf(B,
        // ObjectIntersectionOf(ObjectComplementOf(B), C)));
        // OWLAxiom ax = SubClassOf(A, ObjectSomeValuesFrom(prop, OWLThing()));
        // OWLAxiom ax = SubClassOf(A, ObjectAllValuesFrom(prop, B));
        OWLAxiom ax = df.getOWLSubClassOfAxiom(A, df.getOWLObjectOneOf(i, j, k, l));
        // ToStringRenderer.getInstance().setRenderer(new DLSyntaxObjectRenderer());
        System.out.println(ax);
        System.out.println("---------------------------------------------------");
        DeltaTransformation transformation = new DeltaTransformation(df);
        for (OWLAxiom axt : transformation.transform(Collections.singleton(ax))) {
            System.out.println(axt);
        }


    }

}
