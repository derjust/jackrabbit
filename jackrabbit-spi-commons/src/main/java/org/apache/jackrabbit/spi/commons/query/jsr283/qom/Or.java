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
package org.apache.jackrabbit.spi.commons.query.jsr283.qom;

/**
 * Performs a logical disjunction of two other constraints.
 * <p/>
 * To satisfy the <code>Or</code> constraint, the node-tuple must either:
 * <ul>
 * <li>satisfy {@link #getConstraint1 constraint1} but not
 * {@link #getConstraint2 constraint2}, or</li>
 * <li>satisfy {@link #getConstraint2 constraint2} but not
 * {@link #getConstraint1 constraint1}, or</li>
 * <li>satisfy both {@link #getConstraint1 constraint1} and
 * {@link #getConstraint2 constraint2}.</li>
 * </ul>
 *
 * @since JCR 2.0
 */
public interface Or extends Constraint {

    /**
     * Gets the first constraint.
     *
     * @return the constraint; non-null
     */
    Constraint getConstraint1();

    /**
     * Gets the second constraint.
     *
     * @return the constraint; non-null
     */
    Constraint getConstraint2();

}
