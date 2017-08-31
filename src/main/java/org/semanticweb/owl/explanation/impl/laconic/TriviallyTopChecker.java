package org.semanticweb.owl.explanation.impl.laconic;

import org.semanticweb.owlapi.model.*;
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
 * Author: Matthew Horridge<br>
 * The University Of Manchester<br>
 * Information Management Group<br>
 * Date: 15-Sep-2008<br>
 * <br>
 */
public class TriviallyTopChecker implements OWLClassExpressionVisitorEx<Boolean> {

    private TriviallyBottomChecker bottomChecker = new TriviallyBottomChecker();

    @Override
    public <T> Boolean doDefault(T object) {
        return Boolean.FALSE;
    }

    @Override
    public Boolean visit(OWLClass desc) {
        return Boolean.valueOf(desc.isOWLThing());
    }

    @Override
    public Boolean visit(OWLObjectIntersectionOf desc) {
        return Boolean.valueOf(!desc.operands().anyMatch(op -> op.accept(this) == Boolean.FALSE));
    }

    @Override
    public Boolean visit(OWLObjectUnionOf desc) {
        return Boolean.valueOf(desc.operands().anyMatch(op -> op.accept(this) == Boolean.TRUE));
    }

    @Override
    public Boolean visit(OWLObjectComplementOf desc) {
        return desc.getOperand().accept(bottomChecker);
    }

    @Override
    public Boolean visit(OWLObjectAllValuesFrom desc) {
        return desc.getFiller().accept(this);
    }

    @Override
    public Boolean visit(OWLObjectMinCardinality desc) {
        return Boolean.valueOf(desc.getCardinality() == 0);
    }
}
