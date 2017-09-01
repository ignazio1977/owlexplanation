package org.semanticweb.owl.explanation.impl.setree;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class SETreeBuilderTest {

    @SuppressWarnings("boxing")
    @Test
    public void main() {
        List<Integer> list = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21);
        SETreeBuilder<Integer> builder = new SETreeBuilder<>(list);
        builder.buildTree();
    }
}
