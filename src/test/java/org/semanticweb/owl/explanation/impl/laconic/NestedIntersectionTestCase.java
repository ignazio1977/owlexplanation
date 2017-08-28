package org.semanticweb.owl.explanation.impl.laconic;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.util.DefaultPrefixManager;

import openllet.owlapi.OWLManagerGroup;

import java.util.Set;

/*
 * Copyright (C) 2010, University of Manchester
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
 * Author: Matthew Horridge<br>
 * The University of Manchester<br>
 * Information Management Group<br>
 * Date: 28-Jan-2010
 */
public class NestedIntersectionTestCase {

    public static void main(String[] args) {
        DefaultPrefixManager pm = new DefaultPrefixManager("http://test.com#");
        OWLDataFactory df=OWLManager.getOWLDataFactory();
        OWLClass clsA = df.getOWLClass("A", pm);
        OWLClass clsB = df.getOWLClass("B", pm);
        OWLClassExpression ce = df.getOWLObjectIntersectionOf(clsA, df.getOWLObjectIntersectionOf(clsB, clsA));
        TauGenerator tauGenerator = new TauGenerator(df);
        Set<OWLClassExpression> classExpressions = ce.accept(tauGenerator);
        for(OWLClassExpression classExpression : classExpressions) {
            System.out.println(classExpression);
        }
    }

}
