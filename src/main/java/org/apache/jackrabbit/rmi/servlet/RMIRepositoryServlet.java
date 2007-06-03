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
package org.apache.jackrabbit.rmi.servlet;

import javax.jcr.Repository;
import javax.servlet.ServletException;

import org.apache.jackrabbit.commons.servlet.AbstractRepositoryServlet;
import org.apache.jackrabbit.rmi.client.RMIRepository;

/**
 * Servlet that makes a repository from RMI available as an attribute
 * in the servlet context.
 * <p>
 * The supported initialization parameters of this servlet are:
 * <dl>
 *   <dt>javax.jcr.Repository</dt>
 *   <dd>
 *     Name of the servlet context attribute to put the repository in.
 *     The default value is "<code>javax.jcr.Repository</code>".
 *   </dd>
 *   <dt>url</dt>
 *   <dd>
 *     RMI URL of the remote repository. The default value is
 *     "<code>//localhost/javax/jcr/Repository</code>".
 *   </dd>
 * </dl>
 * <p>
 * This servlet can also be mapped to the URL space. See
 * {@link AbstractRepositoryServlet} for the details.
 *
 * @since 1.4
 */
public class RMIRepositoryServlet extends AbstractRepositoryServlet {

    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 3361176460018801671L;

    /**
     * Creates and returns an RMI repository proxy for the configured RMI URL.
     *
     * @return RMI repository proxy
     */
    protected Repository getRepository() throws ServletException {
        return new RMIRepository(
                getInitParameter("url", "//localhost/javax/jcr/Repository"));
    }

}
