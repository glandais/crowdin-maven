package com.googlecode.crowdin.maven.tool;

import java.util.*;

public class SortedProperties extends Properties {

    private static final long serialVersionUID = 1L;

    @Override
    public synchronized Enumeration<Object> keys() {
        final Set<Object> keySet = keySet();
        final Vector<String> keys = new Vector<>(keySet.size());
        for (final Object key : keySet) {
            keys.add(key.toString());
        }
        Collections.sort(keys);
        return (Enumeration) keys.elements();
    }
}