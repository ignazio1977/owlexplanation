package org.semanticweb.owl.explanation.impl.blackbox;

import java.util.Set;

import org.junit.Test;
import org.semanticweb.owl.explanation.api.ExplanationGeneratorFactory;
import org.semanticweb.owl.explanation.api.*;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.StringDocumentSource;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import openllet.owlapi.OpenlletReasonerFactory;

public class ExplainInversesTest {
    @Test
    public void shouldExplain() throws OWLOntologyCreationException {
        OWLReasonerFactory rf = new OpenlletReasonerFactory(); // Get hold of a reasoner factory
        OWLDataFactory df = OWLManager.getOWLDataFactory();
        OWLOntology ont = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(
            new StringDocumentSource("Prefix: : <http://example.org/>\n"
                + "        Ontology: <http://asdf>\n" + "        ObjectProperty: hasBigPart\n"
                + "            SubPropertyOf: hasPart, inverse (partOf)\n"
                + "        ObjectProperty: hasPart\n" + "            InverseOf:  partOf\n"
                + "        ObjectProperty: partOf\n" + "            InverseOf: hasPart\n"
                + "            Characteristics: Transitive")); // Reference to an OWLOntology

        // Create the explanation generator factory which uses reasoners provided by the specified
        // reasoner factory
        ExplanationGeneratorFactory<OWLAxiom> genFac = ExplanationManager
            .createExplanationGeneratorFactory(rf, () -> OWLManager.createOWLOntologyManager());

        // Now create the actual explanation generator for our ontology
        ExplanationGenerator<OWLAxiom> gen = genFac.createExplanationGenerator(ont);

        // Ask for explanations for some entailment
        // Get a reference to the axiom that represents the entailment that we want explanation for
        OWLAxiom entailment = df.getOWLEquivalentClassesAxiom(df.getOWLObjectAllValuesFrom(
            df.getOWLObjectProperty(IRI.create("http://example.org/hasBigPart")), df.getOWLThing()),
            df.getOWLObjectSomeValuesFrom(
                df.getOWLObjectProperty(IRI.create("http://example.org/hasBigPart")),
                df.getOWLThing()));

        // Get our explanations. Ask for a maximum of 5.
        Set<Explanation<OWLAxiom>> expl = gen.getExplanations(entailment, 5);
        for (Explanation<OWLAxiom> ax : expl) {
            System.out.println("ExplainInversesTest.should() " + ax);
        }
    }
}
