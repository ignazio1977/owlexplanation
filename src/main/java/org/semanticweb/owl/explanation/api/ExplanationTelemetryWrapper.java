package org.semanticweb.owl.explanation.api;

import org.semanticweb.owl.explanation.telemetry.TelemetryObject;
import org.semanticweb.owlapi.model.*;

import java.io.*;
import java.util.function.Supplier;

/**
 * Author: Matthew Horridge<br>
 * The University of Manchester<br>
 * Bio-Health Informatics Group<br>
 * Date: 26/01/2011
 */
public class ExplanationTelemetryWrapper implements TelemetryObject {

    private Explanation<OWLAxiom> explanation;
    private Supplier<OWLOntologyManager> m;

    public ExplanationTelemetryWrapper(Explanation<OWLAxiom> explanation, Supplier<OWLOntologyManager> m) {
        this.explanation = explanation;
        this.m = m;
    }

    @Override
    public String getPreferredSerialisedName() {
        return "justification.owl.xml";
    }

    @Override
    public boolean isSerialisedAsXML() {
        return true;
    }

    @Override
    public void serialise(OutputStream outputStream) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Explanation.store(explanation, bos, m);
        String rendering = new String(bos.toByteArray());
        rendering = rendering.replace("<?xml version=\"1.0\"?>\n", "");
        PrintWriter pw = new PrintWriter(outputStream);
        pw.println(rendering);
        pw.flush();
    }


}
