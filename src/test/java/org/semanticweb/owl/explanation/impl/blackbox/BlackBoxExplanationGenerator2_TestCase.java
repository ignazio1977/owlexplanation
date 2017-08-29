package org.semanticweb.owl.explanation.impl.blackbox;

import com.google.common.collect.Sets;

import openllet.owlapi.OpenlletReasonerFactory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.semanticweb.owl.explanation.api.Explanation;
import org.semanticweb.owl.explanation.api.NullExplanationProgressMonitor;
import org.semanticweb.owl.explanation.impl.blackbox.checker.SatisfiabilityEntailmentCheckerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import java.util.Set;
import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

/**
 * Matthew Horridge
 * Stanford Center for Biomedical Informatics Research
 * 17/11/15
 */
@RunWith(MockitoJUnitRunner.class)
public class BlackBoxExplanationGenerator2_TestCase {

    private BlackBoxExplanationGenerator2<OWLAxiom> generator;

    private OWLReasonerFactory reasonerFactory;

    private OWLDataFactory df=OWLManager.getOWLDataFactory();

    private OWLAxiom entailment;

    private OWLAxiom ASubClassOfB;

    private OWLAxiom BSubClassOfC;

    private Supplier<OWLOntologyManager> m=OWLManager::createOWLOntologyManager;

    @Before
    public void setUp() {

        OWLClass A = df.getOWLClass(IRI.create("http://example.com/A"));
        OWLClass B = df.getOWLClass(IRI.create("http://example.com/B"));
        OWLClass C = df.getOWLClass(IRI.create("http://example.com/C"));


        ASubClassOfB = df.getOWLSubClassOfAxiom(A, B);
        BSubClassOfC = df.getOWLSubClassOfAxiom(B, C);



        entailment = df.getOWLSubClassOfAxiom(A, C);

        reasonerFactory = new OpenlletReasonerFactory();

        generator = new BlackBoxExplanationGenerator2<>(
                Sets.newHashSet(ASubClassOfB, BSubClassOfC),
                new SatisfiabilityEntailmentCheckerFactory(reasonerFactory, m),
                new StructuralExpansionStrategy(m),
                new SimpleContractionStrategy(),
                new NullExplanationProgressMonitor<OWLAxiom>(),
                m
        );

    }

    @Test
    public void test() {
        Set<Explanation<OWLAxiom>> explanations = generator.getExplanations(entailment);
        assertThat(explanations.size(), is(1));
        Explanation<OWLAxiom> explanation = explanations.iterator().next();
        assertThat(explanation.getEntailment(), is(entailment));
        assertThat(explanation.getAxioms().size(), is(2));
        assertThat(explanation.getAxioms(), containsInAnyOrder(ASubClassOfB, BSubClassOfC));
    }

}
