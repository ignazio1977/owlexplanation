package org.semanticweb.owl.explanation.impl.setree;

import java.util.*;

import gnu.trove.map.hash.TObjectIntHashMap;

/**
 * Author: Matthew Horridge<br>
 * The University of Manchester<br>
 * Bio-Health Informatics Group<br>
 * Date: 19-Jul-2010
 */
public class SETreeBuilder<O> {

    private List<O> elements = new ArrayList<>();

    private TObjectIntHashMap<O> indexMap = new TObjectIntHashMap<>(16, 0.75f, 0);

    public SETreeBuilder(List<O> axioms) {
        this.elements = axioms;
        int index = 1;
        for(O ax : axioms) {
            indexMap.put(ax, index);
            index++;
        }
    }

    public SETreeNode<O> buildTree() {
        SETreeNode<O> root = new SETreeNode<>(Collections.<O>emptyList());
        extendNode(root);
        return root;
    }

    private void extendNode(SETreeNode<O> node) {
        int maxIndex = getMaxIndex(node);
        for(int i = maxIndex; i < elements.size(); i++) {
            List<O> nodeElements = new ArrayList<>(node.getElements());
            nodeElements.add(elements.get(i));
            SETreeNode<O> child = new SETreeNode<>(nodeElements);
            node.addChild(child);
            extendNode(child);
        }
    }

    private int getMaxIndex(SETreeNode<O> node) {
        int maxIndex = 0;
        for(O element : node.getElements()) {
            int index = getIndexOf(element);
            if(index > maxIndex) {
                maxIndex = index;
            }
        }
        return maxIndex;
    }

    public int getIndexOf(O element) {
        return indexMap.get(element);
    }
}
