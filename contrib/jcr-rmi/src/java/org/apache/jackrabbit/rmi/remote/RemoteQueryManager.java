/*
 * Copyright 2004-2005 The Apache Software Foundation or its licensors,
 *                     as applicable.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.rmi.remote;

import java.rmi.Remote;
import java.rmi.RemoteException;

import javax.jcr.RepositoryException;
import javax.jcr.query.InvalidQueryException;
import javax.jcr.query.Query;

/**
 * Remote version of the JCR {@link javax.jcr.query.QueryManager QueryManager} interface.
 * Used by the  {@link org.apache.jackrabbit.rmi.server.ServerQueryManager ServerQueryManager}
 * and {@link org.apache.jackrabbit.rmi.client.ClientQueryManager ClientQueryManager}
 * adapter base classes to provide transparent RMI access to remote items.
 * <p>
 * RMI errors are signalled with RemoteExceptions.
 *
 * @author Philipp Koch
 * @see javax.jcr.query.QueryManager
 * @see org.apache.jackrabbit.rmi.client.ClientQueryManager
 * @see org.apache.jackrabbit.rmi.server.ServerQueryManager
 */
public interface RemoteQueryManager extends Remote {

    /**
     * @see javax.jcr.query.QueryManager#createQuery
     *
     * @throws InvalidQueryException if statement is invalid or language is unsupported.
     * @throws RepositoryException if another error occurs
     * @return A <code>Query</code> object.
     */
    public RemoteQuery createQuery(String statement, String language)
        throws InvalidQueryException, RepositoryException, RemoteException;

    /**
     * @see javax.jcr.query.QueryManager#getQuery
     *
     * @param absPath node path of a persisted query (that is, a node of type <code>nt:query</code>).
     * @throws InvalidQueryException If <code>node</code> is not a valid persisted query
     * (that is, a node of type <code>nt:query</code>).
     * @throws RepositoryException if another error occurs
     * @return a <code>Query</code> object.
     */
    public RemoteQuery getQuery(String absPath)
        throws InvalidQueryException, RepositoryException, RemoteException;

    /**
     * * @see javax.jcr.query.QueryManager#getSupportedQueryLanguages
     *
     * See {@link Query}.
     * @return An string array.
     */
    public String[] getSupportedQueryLanguages() throws RemoteException;

}
