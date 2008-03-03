/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.core.nodetype;

import org.apache.jackrabbit.name.QName;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.ArrayList;

/**
 * <code>EffectiveNodeTypeCache</code> implementation that uses an array of
 * node type names as key for caching the effective node types.
 */
public class EffectiveNodeTypeCacheImpl implements EffectiveNodeTypeCache {

    /**
     * ordered set of keys
     */
    private final TreeSet sortedKeys;

    /**
     * cache of pre-built aggregations of node types
     */
    private final HashMap aggregates;

    /**
     * Creates a new effective node type cache.
     */
    EffectiveNodeTypeCacheImpl() {
        sortedKeys = new TreeSet();
        aggregates = new HashMap();
    }

    /**
     * {@inheritDoc}
     */
    public Key getKey(QName[] ntNames) {
        return new WeightedKey(ntNames);
    }

    /**
     * {@inheritDoc}
     */
    public void put(EffectiveNodeType ent) {
        // we define the weight as the total number of included node types
        // (through aggregation and inheritance)
        int weight = ent.getMergedNodeTypes().length;
        // the effective node type is identified by the list of merged
        // (i.e. aggregated) node types
        WeightedKey k = new WeightedKey(ent.getMergedNodeTypes(), weight);
        put(k, ent);
    }

    /**
     * {@inheritDoc}
     */
    public void put(Key key, EffectiveNodeType ent) {
        aggregates.put(key, ent);
        sortedKeys.add(key);
    }

    /**
     * {@inheritDoc}
     */
    public boolean contains(Key key) {
        return aggregates.containsKey(key);
    }

    /**
     * {@inheritDoc}
     */
    public EffectiveNodeType get(Key key) {
        return (EffectiveNodeType) aggregates.get(key);
    }

    /**
     * Removes the effective node type for the given key from the cache.
     * @param key the key of the effective node type to remove
     * @return the removed effective node type or <code>null</code> if it was
     *         never cached.
     */
    private EffectiveNodeType remove(Key key) {
        EffectiveNodeType removed = (EffectiveNodeType) aggregates.remove(key);
        if (removed != null) {
            // remove index entry

            // FIXME: can't simply call TreeSet.remove(key) because the entry
            // in sortedKeys might have a different weight and would thus
            // not be found
            Iterator iter = sortedKeys.iterator();
            while (iter.hasNext()) {
                Key k = (Key) iter.next();
                // WeightedKey.equals(Object) ignores the weight
                if (key.equals(k)) {
                    sortedKeys.remove(k);
                    break;
                }
            }
        }
        return removed;
    }

    /**
     * {@inheritDoc}
     */
    public void invalidate(QName name) {
        // remove all affected effective node types from aggregates cache
        // (copy keys first to prevent ConcurrentModificationException)
        ArrayList keys = new ArrayList(sortedKeys);
        for (Iterator keysIter = keys.iterator(); keysIter.hasNext();) {
            Key k = (Key) keysIter.next();
            EffectiveNodeType ent = get(k);
            if (ent.includesNodeType(name)) {
                remove(k);
            }
        }
    }

    /**
     * @inheritDoc
     */
    public Key findBest(Key key) {
        // quick check for already cached key
        if (contains(key)) {
            return key;
        }
        Iterator iter = sortedKeys.iterator();
        while (iter.hasNext()) {
            Key k = (Key) iter.next();
            // check if the existing aggregate is a 'subset' of the one we're
            // looking for
            if (key.contains(k)) {
                return k;
            }
        }
        return null;
    }

    //-------------------------------------------< java.lang.Object overrides >

    /**
     * {@inheritDoc}
     */
    public Object clone() {
        EffectiveNodeTypeCacheImpl clone = new EffectiveNodeTypeCacheImpl();
        clone.sortedKeys.addAll(sortedKeys);
        clone.aggregates.putAll(aggregates);
        return clone;
    }

    //-------------------------------------------------------------< Dumpable >

    /**
     * {@inheritDoc}
     */
    public void dump(PrintStream ps) {
        ps.println("EffectiveNodeTypeCache (" + this + ")");
        ps.println();
        ps.println("EffectiveNodeTypes in cache:");
        ps.println();
        Iterator iter = sortedKeys.iterator();
        while (iter.hasNext()) {
            Key k = (Key) iter.next();
            //EffectiveNodeType ent = (EffectiveNodeType) aggregates.get(k);
            ps.println(k);
        }
    }

    //--------------------------------------------------------< inner classes >
    /**
     * A <code>WeightedKey</code> uniquely identifies
     * a combination (i.e. an aggregation) of one or more node types.
     * The weight is an indicator for the cost involved in building such an
     * aggregate (e.g. an aggregation of multiple complex node types with deep
     * inheritance trees is more costly to build/validate than an agreggation
     * of two very simple node types with just one property definition each).
     * <p/>
     * A very simple (and not very accurate) approximation of the weight would
     * be the number of explicitly aggregated node types (ignoring inheritance
     * and complexity of each involved node type). A better approximation would
     * be the number of <b>all</b>, explicitly and implicitly (note that
     * inheritance is also an aggregation) aggregated node types.
     * <p/>
     * The more accurate the weight definition, the more efficient is the
     * the building of new aggregates.
     * <p/>
     * It is important to note that the weight is not part of the key value,
     * i.e. it is not considered by the <code>hashCode()</code> and
     * <code>equals(Object)</code> methods. It does however affect the order
     * of <code>WeightedKey</code> instances. See
     * <code>{@link #compareTo(Object)}</code> for more information.
     * <p/>
     * Let's assume we have an aggregation of node types named "b", "a" and "c".
     * Its key would be "[a, b, c]" and the weight 3 (using the simple
     * approximation).
     */
    private static class WeightedKey implements Key {

        /**
         * array of node type names, sorted in ascending order
         */
        private final QName[] names;

        /**
         * the weight of this key
         */
        private final int weight;

        /**
         * @param ntNames
         */
        WeightedKey(QName[] ntNames) {
            this(ntNames, ntNames.length);
        }

        /**
         * @param ntNames
         * @param weight
         */
        WeightedKey(QName[] ntNames, int weight) {
            this.weight = weight;
            names = new QName[ntNames.length];
            System.arraycopy(ntNames, 0, names, 0, names.length);
            Arrays.sort(names);
        }

        /**
         * @param ntNames
         */
        WeightedKey(Collection ntNames) {
            this(ntNames, ntNames.size());
        }

        /**
         * @param ntNames
         * @param weight
         */
        WeightedKey(Collection ntNames, int weight) {
            this((QName[]) ntNames.toArray(new QName[ntNames.size()]), weight);
        }

        /**
         * @return the node type names of this key
         */
        public QName[] getNames() {
            return names;
        }

        /**
         * {@inheritDoc}
         */
        public boolean contains(Key otherKey) {
            WeightedKey key = (WeightedKey) otherKey;
            Set tmp = new HashSet(Arrays.asList(names));
            for (int i = 0; i < key.names.length; i++) {
                if (!tmp.contains(key.names[i])) {
                    return false;
                }
            }
            return true;
        }

        /**
         * {@inheritDoc}
         */
        public Key subtract(Key otherKey) {
            WeightedKey key = (WeightedKey) otherKey;
            Set tmp = new HashSet(Arrays.asList(names));
            tmp.removeAll(Arrays.asList(key.names));
            return new WeightedKey(tmp);

        }

        //-------------------------------------------------------< Comparable >
        /**
         * The resulting sort-order is: 1. descending weight, 2. ascending key
         * (i.e. string representation of this sorted set).
         *
         * @param o the other key to compare
         * @return the result of the comparison
         */
        public int compareTo(Object o) {
            WeightedKey other = (WeightedKey) o;

            // compare weights
            if (weight > other.weight) {
                return -1;
            } else if (weight < other.weight) {
                return 1;
            }

            // compare arrays of names
            int len1 = names.length;
            int len2 = other.names.length;
            int len = Math.min(len1, len2);

            for (int i = 0; i < len; i++) {
                QName name1 = names[i];
                QName name2 = other.names[i];
                int result = name1.compareTo(name2);
                if (result != 0) {
                    return result;
                }
            }
            return len1 - len2;
        }

        //---------------------------------------< java.lang.Object overrides >

        /**
         * {@inheritDoc}
         */
        public int hashCode() {
            int h = 17;
            // ignore weight
            for (int i = 0; i < names.length; i++) {
                h *= 37;
                h += names[i].hashCode();
            }
            return h;
        }

        /**
         * {@inheritDoc}
         */
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof WeightedKey) {
                WeightedKey other = (WeightedKey) obj;
                // ignore weight
                return Arrays.equals(names, other.names);
            }
            return false;
        }

        /**
         * {@inheritDoc}
         */
        public String toString() {
            return Arrays.asList(names).toString() + " (" + weight + ")";
        }
    }
}