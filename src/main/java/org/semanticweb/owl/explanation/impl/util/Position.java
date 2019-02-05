package org.semanticweb.owl.explanation.impl.util;

import java.util.List;

import com.carrotsearch.hppcrt.lists.IntArrayList;

/**
 * Author: Matthew Horridge<br>
 * The University of Manchester<br>
 * Bio-Health Informatics Group<br>
 * Date: 14/02/2011
 */
public class Position {

    private IntArrayList positionList = new IntArrayList();

    public Position(int... position) {
        positionList.add(position);
    }

    public Position(List<Integer> position, int childPosition) {
        position.forEach(positionList::add);
        positionList.add(childPosition);
    }

    public Position(IntArrayList position, int childPosition) {
        positionList.addAll(position);
        positionList.add(childPosition);
    }

    public Position(int position) {
        positionList.add(position);
    }

    public Position() {
    }

    public boolean isEmpty() {
        return positionList.isEmpty();
    }

    public Position addPosition(int position) {
        return new Position(positionList, position);
    }

    @Override
    public String toString() {
        return positionList.toString().replace(", ", ".").replace("{", "").replace("}", "");
    }

    @Override
    public int hashCode() {
        return positionList.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == this) {
            return true;
        }
        if(!(obj instanceof Position)) {
            return false;
        }
        Position otherPosition = (Position) obj;
        return positionList.equals(otherPosition.positionList);
    }
}
