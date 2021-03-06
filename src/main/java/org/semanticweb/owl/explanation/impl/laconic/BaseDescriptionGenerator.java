package org.semanticweb.owl.explanation.impl.laconic;

import org.semanticweb.owlapi.model.*;

import java.util.*;
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
import java.util.stream.Stream;


/**
 * Author: Matthew Horridge<br> The University Of Manchester<br> Information Management Group<br> Date:
 * 15-Sep-2008<br><br>
 */
public abstract class BaseDescriptionGenerator implements OWLClassExpressionVisitorEx<Set<OWLClassExpression>> {

    private OWLDataFactory factory;

    private static TriviallyTopChecker topChecker = new TriviallyTopChecker();

    private static TriviallyBottomChecker bottomChecker = new TriviallyBottomChecker();


    public BaseDescriptionGenerator(OWLDataFactory factory) {
        this.factory = factory;
    }

    public boolean isThing(OWLClassExpression description) {
        return description.accept(topChecker) == Boolean.TRUE;
    }

    public boolean isNothing(OWLClassExpression description) {
        return description.accept(bottomChecker) == Boolean.TRUE;
    }

    public OWLDataFactory getDataFactory() {
        return factory;
    }


    public Set<OWLClassExpression> computeTau(OWLClassExpression desc) {
        TauGenerator gen = new TauGenerator(factory);
        return desc.accept(gen);
    }


    public Set<OWLClassExpression> computeBeta(OWLClassExpression desc) {
        BetaGenerator gen = new BetaGenerator(factory);
        return desc.accept(gen);
    }

    private Set<Set<OWLClassExpression>> computeReplacements(Stream<OWLClassExpression> operands) {
        Set<List<OWLClassExpression>> ps = new HashSet<>();
        ps.add(new ArrayList<OWLClassExpression>());
        operands.forEach(op -> updateUnion(ps, op));
        Set<Set<OWLClassExpression>> result = new HashSet<>();
        for(List<OWLClassExpression> desc : ps) {
            result.add(new HashSet<>(desc));
        }
        return result;
    }

    protected void updateUnion(Set<List<OWLClassExpression>> ps, OWLClassExpression op) {
        Set<List<OWLClassExpression>> pscopy = new HashSet<>(ps);

        for (OWLClassExpression opEx : op.accept(this)) {
            for (List<OWLClassExpression> pselement : pscopy) {
                ArrayList<OWLClassExpression> union = new ArrayList<>();

                union.addAll(pselement);
                union.add(opEx);
                ps.remove(pselement);
                ps.add(union);
            }
        }
    }



    @Override
    public Set<OWLClassExpression> visit(OWLObjectIntersectionOf desc) {
        Set<OWLClassExpression> descs = new HashSet<>();
        Set<Set<OWLClassExpression>> conjunctions = computeReplacements(desc.operands());
        for (Set<OWLClassExpression> conjuncts : conjunctions) {
            for(Iterator<OWLClassExpression> it = conjuncts.iterator(); it.hasNext(); ) {
                if(isThing(it.next())) {
                    it.remove();
                }
            }
            if(conjuncts.isEmpty()) {
                descs.add(factory.getOWLThing());
            }
            else if (conjuncts.size() != 1) {
                descs.add(factory.getOWLObjectIntersectionOf(conjuncts));
            }
            else {
                descs.add(conjuncts.iterator().next());
            }
        }
        descs.add(getLimit());
        return descs;
    }


    @Override
    public Set<OWLClassExpression> visit(OWLObjectUnionOf desc) {
        Set<OWLClassExpression> descs = new HashSet<>();
        Set<Set<OWLClassExpression>> disjunctions = computeReplacements(desc.operands());
        for (Set<OWLClassExpression> disjuncts : disjunctions) {
            for(Iterator<OWLClassExpression> it = disjuncts.iterator(); it.hasNext(); ) {
                if(isNothing(it.next())) {
                    it.remove();
                }
            }
            if (disjuncts.size() != 1) {
                descs.add(factory.getOWLObjectUnionOf(disjuncts));
            }
            else {
                descs.add(disjuncts.iterator().next());
            }
        }
        descs.add(getLimit());
        return descs;
    }


    @Override
    public Set<OWLClassExpression> visit(OWLObjectSomeValuesFrom desc) {
        Set<OWLClassExpression> descs = new HashSet<>();
        descs.add(desc);
        for (OWLClassExpression filler : desc.getFiller().accept(this)) {
            if (!isNothing(filler)) {
                descs.add(factory.getOWLObjectSomeValuesFrom(desc.getProperty(), filler));
            }
        }
        descs.add(getLimit());
        return descs;
    }


    @Override
    public Set<OWLClassExpression> visit(OWLObjectAllValuesFrom desc) {
        Set<OWLClassExpression> descs = new HashSet<>();
        for (OWLClassExpression filler : desc.getFiller().accept(this)) {
            if (!isThing(filler)) {
                descs.add(factory.getOWLObjectAllValuesFrom(desc.getProperty(), filler));
            }
        }
        descs.add(getLimit());
        return descs;
    }


    @Override
    public Set<OWLClassExpression> visit(OWLObjectHasValue desc) {
        Set<OWLClassExpression> descs = new HashSet<>();
        descs.add(desc);
        descs.add(factory.getOWLObjectSomeValuesFrom(desc.getProperty(), factory.getOWLThing()));
        descs.add(getLimit());
        return descs;
    }

    @Override
    public Set<OWLClassExpression> visit(OWLObjectExactCardinality desc) {
        // Syntactic for min and max
        Set<OWLClassExpression> result = new HashSet<>();
        OWLClassExpression min = getDataFactory().getOWLObjectMinCardinality(desc.getCardinality(), desc.getProperty(), desc.getFiller());
        result.addAll(min.accept(this));
        OWLClassExpression max = getDataFactory().getOWLObjectMaxCardinality(desc.getCardinality(), desc.getProperty(), desc.getFiller());
        result.addAll(max.accept(this));
        result.add(getLimit());
        return result;
    }

    @Override
    public Set<OWLClassExpression> visit(OWLObjectHasSelf desc) {
        Set<OWLClassExpression> descs = new HashSet<>();
        descs.add(desc);
        descs.add(getLimit());
        return descs;
    }


    @Override
    public Set<OWLClassExpression> visit(OWLObjectOneOf desc) {
        if(desc.individuals().count() == 1) {
            Set<OWLClassExpression> ops = new HashSet<>();
            ops.add(desc);
            ops.add(getLimit());
            return ops;
        }
        OWLClassExpression rewrite = factory.getOWLObjectUnionOf(desc.individuals().map(ind -> factory.getOWLObjectOneOf(ind)));
        return rewrite.accept(this);
    }

    protected abstract OWLClass getLimit();

    protected abstract OWLDataRange getDataLimit();

    @Override
    public Set<OWLClassExpression> visit(OWLDataSomeValuesFrom desc) {
        return Collections.<OWLClassExpression>singleton(desc);
    }


    @Override
    public Set<OWLClassExpression> visit(OWLDataAllValuesFrom desc) {
        return Collections.singleton((OWLClassExpression) desc);
    }


    @Override
    public Set<OWLClassExpression> visit(OWLDataHasValue desc) {
        return Collections.<OWLClassExpression>singleton(desc);
    }


    @Override
    public Set<OWLClassExpression> visit(OWLDataMinCardinality desc) {
        return Collections.singleton((OWLClassExpression) desc);
    }


    @Override
    public Set<OWLClassExpression> visit(OWLDataExactCardinality desc) {
        return Collections.singleton((OWLClassExpression) desc);
    }


    @Override
    public Set<OWLClassExpression> visit(OWLDataMaxCardinality desc) {
        return Collections.singleton((OWLClassExpression) desc);
    }
}
