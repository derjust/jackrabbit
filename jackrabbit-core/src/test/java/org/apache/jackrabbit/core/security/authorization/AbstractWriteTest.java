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
package org.apache.jackrabbit.core.security.authorization;

import org.apache.jackrabbit.api.jsr283.security.AccessControlManager;
import org.apache.jackrabbit.api.jsr283.security.AccessControlPolicy;
import org.apache.jackrabbit.api.jsr283.security.Privilege;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.core.SessionImpl;
import org.apache.jackrabbit.core.security.TestPrincipal;
import org.apache.jackrabbit.test.JUnitTest;
import org.apache.jackrabbit.test.NotExecutableException;
import org.apache.jackrabbit.test.api.observation.EventResult;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.uuid.UUID;

import javax.jcr.AccessDeniedException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.observation.Event;
import javax.jcr.observation.ObservationManager;
import java.security.Principal;

/**
 * <code>AbstractEvaluationTest</code>...
 */
public abstract class AbstractWriteTest extends AbstractEvaluationTest {

    protected static final long DEFAULT_WAIT_TIMEOUT = 5000;

    private Group testGroup;

    protected String path;
    protected String childNPath;
    protected String childNPath2;
    protected String childPPath;
    protected String childchildPPath;
    protected String siblingPath;

    // TODO: test AC for moved node
    // TODO: test AC for moved AC-controlled node
    // TODO: test if combination of group and user permissions are properly evaluated

    protected void setUp() throws Exception {
        super.setUp();

        // create some nodes below the test root in order to apply ac-stuff
        Node node = testRootNode.addNode(nodeName1, testNodeType);
        Node cn1 = node.addNode(nodeName2, testNodeType);
        Property cp1 = node.setProperty(propertyName1, "anyValue");
        Node cn2 = node.addNode(nodeName3, testNodeType);

        Property ccp1 = cn1.setProperty(propertyName1, "childNodeProperty");

        Node n2 = testRootNode.addNode(nodeName2, testNodeType);
        superuser.save();

        path = node.getPath();
        childNPath = cn1.getPath();
        childNPath2 = cn2.getPath();
        childPPath = cp1.getPath();
        childchildPPath = ccp1.getPath();
        siblingPath = n2.getPath();
    }

    protected void tearDown() throws Exception {
        // make sure all ac info is removed
        if (testGroup != null && testUser != null) {
            testGroup.removeMember(testUser);
            testGroup.remove();
        }
        super.tearDown();
    }

    protected User getTestUser() {
        return testUser;
    }

    protected Group getTestGroup() throws RepositoryException, NotExecutableException {
        if (testGroup == null) {
            // create the testGroup
            Principal principal = new TestPrincipal("testGroup" + UUID.randomUUID());
            testGroup = getUserManager(superuser).createGroup(principal);
            testGroup.addMember(testUser);
        }
        return testGroup;
    }

    public void testGrantedPermissions() throws RepositoryException, AccessDeniedException, NotExecutableException {
        /* precondition:
           testuser must have READ-only permission on test-node and below
         */
        checkReadOnly(path);

        // give 'testUser' ADD_CHILD_NODES|MODIFY_PROPERTIES privileges at 'path'
        Privilege[] privileges = privilegesFromNames(new String[] {
                Privilege.JCR_ADD_CHILD_NODES,
                Privilege.JCR_MODIFY_PROPERTIES
        });
        givePrivileges(path, privileges, getRestrictions(superuser, path));
        /*
         testuser must now have
         - ADD_NODE permission for child node
         - SET_PROPERTY permission for child props
         - REMOVE permission for child-props
         - READ-only permission for the node at 'path'

         testuser must not have
         - REMOVE permission for child node
        */
        SessionImpl testSession = getTestSession();
        String nonExChildPath = path + "/anyItem";
        assertTrue(testSession.hasPermission(nonExChildPath, "read,add_node,set_property"));
        assertFalse(testSession.hasPermission(nonExChildPath, "remove"));

        Node testN = testSession.getNode(path);

        // must be allowed to add child node
        testN.addNode(nodeName3);
        testSession.save();

        // must be allowed to remove child-property
        testSession.getProperty(childPPath).remove();
        testSession.save();

        // must be allowed to set child property again
        testN.setProperty(Text.getName(childPPath), "othervalue");
        testSession.save();

        // must not be allowed to remove child nodes
        try {
            testSession.getNode(childNPath).remove();
            testSession.save();
            fail("test-user is not allowed to remove a node below " + path);
        } catch (AccessDeniedException e) {
            // success
        }

        // must have read-only access on 'testN' and it's sibling
        assertTrue(testSession.hasPermission(path, "read"));
        assertFalse(testSession.hasPermission(path, "add_node,set_property,remove"));
        checkReadOnly(siblingPath);
    }

    public void testDeniedPermission() throws RepositoryException, NotExecutableException, InterruptedException {
        /* precondition:
           testuser must have READ-only permission on test-node and below
         */
        checkReadOnly(path);

        // withdraw READ privilege to 'testUser' at 'path'
        Privilege[] privileges = privilegesFromName(Privilege.JCR_READ);
        withdrawPrivileges(childNPath, privileges, getRestrictions(superuser, childNPath));
        /*
         testuser must now have
         - READ-only permission at path
         - READ-only permission for the child-props of path

         testuser must not have
         - any permission on child-node and all its subtree
        */

        // must still have read-access to path, ...
        SessionImpl testSession = getTestSession();
        assertTrue(testSession.hasPermission(path, "read"));
        Node n = testSession.getNode(path);
        // ... siblings of childN
        testSession.getNode(childNPath2);
        // ... and props of path
        assertTrue(n.getProperties().hasNext());

        //testSession must not have access to 'childNPath'
        assertFalse(testSession.itemExists(childNPath));
        try {
            testSession.getNode(childNPath);
            fail("Read access has been denied -> cannot retrieve child node.");
        } catch (PathNotFoundException e) {
            // ok.
        }
        /*
        -> must not have access to subtree below 'childNPath'
        */
        assertFalse(testSession.itemExists(childchildPPath));
        try {
            testSession.getItem(childchildPPath);
            fail("Read access has been denied -> cannot retrieve prop below child node.");
        } catch (PathNotFoundException e) {
            // ok.
        }
    }

    public void testAccessControlRead() throws NotExecutableException, RepositoryException {
        AccessControlManager testAcMgr = getTestACManager();
        checkReadOnly(path);

        // re-grant READ in order to have an ACL-node
        Privilege[] privileges = privilegesFromName(Privilege.JCR_READ);
        JackrabbitAccessControlList tmpl = givePrivileges(path, privileges, getRestrictions(superuser, path));
        // make sure the 'rep:policy' node has been created.
        assertTrue(superuser.itemExists(tmpl.getPath() + "/rep:policy"));

        SessionImpl testSession = getTestSession();
        /*
         Testuser must still have READ-only access only and must not be
         allowed to view the acl-node that has been created.
        */
        assertFalse(testAcMgr.hasPrivileges(path, privilegesFromName(Privilege.JCR_READ_ACCESS_CONTROL)));
        assertFalse(testSession.itemExists(path + "/rep:policy"));

        Node n = testSession.getNode(tmpl.getPath());
        assertFalse(n.hasNode("rep:policy"));
        try {
            n.getNode("rep:policy");
            fail("Accessing the rep:policy node must throw PathNotFoundException.");
        } catch (PathNotFoundException e) {
            // ok.
        }

        /* Finally the test user must not be allowed to remove the policy. */
        try {
            testAcMgr.removePolicy(path, new AccessControlPolicy() {});
            fail("Test user must not be allowed to remove the access control policy.");
        } catch (AccessDeniedException e) {
            // success
        }
    }

    public void testAccessControlModification() throws RepositoryException, NotExecutableException {
        AccessControlManager testAcMgr = getTestACManager();
        /* precondition:
          testuser must have READ-only permission on test-node and below
        */
        checkReadOnly(path);
        SessionImpl testSession = getTestSession();

        // give 'testUser' ADD_CHILD_NODES|MODIFY_PROPERTIES| REMOVE_CHILD_NODES privileges at 'path'
        Privilege[] privileges = privilegesFromNames(new String[] {
                Privilege.JCR_ADD_CHILD_NODES,
                Privilege.JCR_REMOVE_CHILD_NODES,
                Privilege.JCR_MODIFY_PROPERTIES
        });
        JackrabbitAccessControlList tmpl = givePrivileges(path, privileges, getRestrictions(superuser, path));
        /*
         testuser must not have
         - permission to view AC items
         - permission to modify AC items
        */

        // make sure the 'rep:policy' node has been created.
        assertTrue(superuser.itemExists(tmpl.getPath() + "/rep:policy"));
        // the policy node however must not be visible to the test-user
        assertFalse(testSession.itemExists(tmpl.getPath() + "/rep:policy"));
        try {
            testAcMgr.getPolicies(tmpl.getPath());
            fail("test user must not have READ_AC privilege.");
        } catch (AccessDeniedException e) {
            // success
        }
        try {
            testAcMgr.getEffectivePolicies(tmpl.getPath());
            fail("test user must not have READ_AC privilege.");
        } catch (AccessDeniedException e) {
            // success
        }
        try {
            testAcMgr.getEffectivePolicies(path);
            fail("test user must not have READ_AC privilege.");
        } catch (AccessDeniedException e) {
            // success
        }
        try {
            testAcMgr.removePolicy(tmpl.getPath(), new AccessControlPolicy() {});
            fail("test user must not have MODIFY_AC privilege.");
        } catch (AccessDeniedException e) {
            // success
        }
    }

    public void testWithDrawRead() throws RepositoryException, NotExecutableException {
        /*
         precondition:
         testuser must have READ-only permission on test-node and below
        */
        checkReadOnly(path);

        // give 'testUser' READ_AC|MODIFY_AC privileges at 'path'
        Privilege[] grPrivs = privilegesFromName(PrivilegeRegistry.REP_WRITE);
        givePrivileges(path, grPrivs, getRestrictions(superuser, path));
        // withdraw the READ privilege
        Privilege[] dnPrivs = privilegesFromName(Privilege.JCR_READ);
        withdrawPrivileges(path, dnPrivs, getRestrictions(superuser, path));

        // test if login as testuser -> item at path must not exist.
        Session s = null;
        try {
            s = helper.getRepository().login(creds);
            assertFalse(s.itemExists(path));
        } finally {
            if (s != null) {
                s.logout();
            }
        }
    }

    public void testEventGeneration() throws RepositoryException, NotExecutableException {
        /*
         precondition:
         testuser must have READ-only permission on test-node and below
        */
        checkReadOnly(path);
        SessionImpl testSession = getTestSession();

        // withdraw the READ privilege
        Privilege[] dnPrivs = privilegesFromName(Privilege.JCR_READ);
        withdrawPrivileges(path, dnPrivs, getRestrictions(superuser, path));

        // testUser registers a eventlistener for 'path
        ObservationManager obsMgr = testSession.getWorkspace().getObservationManager();
        EventResult listener = new EventResult(((JUnitTest) this).log);
        try {
            obsMgr.addEventListener(listener, Event.NODE_REMOVED, path, true, new String[0], new String[0], true);

            // superuser removes the node with childNPath in order to provoke
            // events being generated
            superuser.getItem(childNPath).remove();
            superuser.save();

            obsMgr.removeEventListener(listener);
            // since the testUser does not have read-permission on the removed
            // node, no corresponding event must be generated.
            Event[] evts = listener.getEvents(DEFAULT_WAIT_TIMEOUT);
            for (int i = 0; i < evts.length; i++) {
                if (evts[i].getType() == Event.NODE_REMOVED &&
                        evts[i].getPath().equals(childNPath)) {
                    fail("TestUser does not have READ permission below " + path + " -> events below must not show up.");
                }
            }
        } finally {
            obsMgr.removeEventListener(listener);
        }
    }

    public void testInheritance() throws RepositoryException, NotExecutableException {
        SessionImpl testSession = getTestSession();
        AccessControlManager testAcMgr = getTestACManager();
        /* precondition:
          testuser must have READ-only permission on test-node and below
        */
        checkReadOnly(path);
        checkReadOnly(childNPath);

        // give 'modify_properties' and 'remove_node' privilege on 'path'
        Privilege[] privileges = privilegesFromNames(new String[] {
                Privilege.JCR_REMOVE_NODE, Privilege.JCR_MODIFY_PROPERTIES});
        givePrivileges(path, privileges, getRestrictions(superuser, path));
        // give 'add-child-nodes', remove_child_nodes' on 'childNPath'
        privileges = privilegesFromNames(new String[] {
                Privilege.JCR_ADD_CHILD_NODES, Privilege.JCR_REMOVE_CHILD_NODES});
        givePrivileges(childNPath, privileges, getRestrictions(superuser, childNPath));

        /*
        since evaluation respects inheritance through the node
        hierarchy, the following privileges must now be given at 'childNPath':
        - jcr:read
        - jcr:modifyProperties
        - jcr:addChildNodes
        - jcr:removeChildNodes
        - jcr:removeNode
        */
        Privilege[] expectedPrivileges =  privilegesFromNames(new String[] {
                Privilege.JCR_READ,
                Privilege.JCR_ADD_CHILD_NODES,
                Privilege.JCR_REMOVE_CHILD_NODES,
                Privilege.JCR_REMOVE_NODE,
                Privilege.JCR_MODIFY_PROPERTIES
        });
        assertTrue(testAcMgr.hasPrivileges(childNPath, expectedPrivileges));

        /*
         ... permissions granted at childNPath:
         - read
         - set-property

         BUT NOT:
         - add-node
         - remove.
         */
        String aActions = org.apache.jackrabbit.api.jsr283.Session.ACTION_SET_PROPERTY + "," + org.apache.jackrabbit.api.jsr283.Session.ACTION_READ;
        assertTrue(testSession.hasPermission(childNPath, aActions));
        String dActions = org.apache.jackrabbit.api.jsr283.Session.ACTION_REMOVE + "," + org.apache.jackrabbit.api.jsr283.Session.ACTION_ADD_NODE;
        assertFalse(testSession.hasPermission(childNPath, dActions));

        /*
        ... permissions granted at any child item of child-path:
        - read
        - set-property
        - add-node
        - remove
        */
        String nonExistingItemPath = childNPath + "/anyItem";
        assertTrue(testSession.hasPermission(nonExistingItemPath, aActions + "," + dActions));

        /* try adding a new child node -> must succeed. */
        Node childN = testSession.getNode(childNPath);
        String testPath = childN.addNode(nodeName2).getPath();

        /* test privileges on the 'new' child node */
        assertTrue(testAcMgr.hasPrivileges(testPath, expectedPrivileges));

        /* repeat test after save. */
        testSession.save();
        assertTrue(testAcMgr.hasPrivileges(testPath, expectedPrivileges));
    }

    public void testRemovePermission() throws NotExecutableException, RepositoryException {
        /*
          precondition:
          testuser must have READ-only permission on test-node and below
        */
        checkReadOnly(path);
        checkReadOnly(childNPath);
        SessionImpl testSession = getTestSession();

        Privilege[] rmChildNodes = privilegesFromName(Privilege.JCR_REMOVE_CHILD_NODES);

        // add 'remove_child_nodes' privilge at 'path'
        givePrivileges(path, rmChildNodes, getRestrictions(superuser, path));
        /*
         expected result:
         - neither node at path nor at childNPath can be removed since
           REMOVE_NODE privilege is missing.
         */
        assertFalse(testSession.hasPermission(path, org.apache.jackrabbit.api.jsr283.Session.ACTION_REMOVE));
        assertFalse(testSession.hasPermission(childNPath, org.apache.jackrabbit.api.jsr283.Session.ACTION_REMOVE));
    }

    public void testRemovePermission2() throws NotExecutableException, RepositoryException {
        /*
          precondition:
          testuser must have READ-only permission on test-node and below
        */
        checkReadOnly(path);
        checkReadOnly(childNPath);
        SessionImpl testSession = getTestSession();

        Privilege[] rmChildNodes = privilegesFromName(Privilege.JCR_REMOVE_NODE);

        // add 'remove_node' privilege at 'path'
        givePrivileges(path, rmChildNodes, getRestrictions(superuser, path));
        /*
         expected result:
         - neither node at path nor at childNPath can be removed permission
           due to missing remove_child_nodes privilege.
         */
        assertFalse(testSession.hasPermission(path, org.apache.jackrabbit.api.jsr283.Session.ACTION_REMOVE));
        assertFalse(testSession.hasPermission(childNPath, org.apache.jackrabbit.api.jsr283.Session.ACTION_REMOVE));
    }

    public void testRemovePermission3() throws NotExecutableException, RepositoryException {
        AccessControlManager testAcMgr = getTestACManager();
        /*
          precondition:
          testuser must have READ-only permission on test-node and below
        */
        checkReadOnly(path);
        checkReadOnly(childNPath);
        SessionImpl testSession = getTestSession();

        Privilege[] privs = privilegesFromNames(new String[] {
                Privilege.JCR_REMOVE_CHILD_NODES, Privilege.JCR_REMOVE_NODE
        });
        // add 'remove_node' and 'remove_child_nodes' privilge at 'path'
        givePrivileges(path, privs, getRestrictions(superuser, path));
        /*
         expected result:
         - missing remove permission at path since REMOVE_CHILD_NODES present
           at path only applies for nodes below. REMOVE_CHILD_NODES must
           be present at the parent instead (which isn't)
         - remove permission is however granted at childNPath.
         - privileges: both at path and at childNPath 'remove_node' and
           'remove_child_nodes' are present.
        */
        assertFalse(testSession.hasPermission(path, org.apache.jackrabbit.api.jsr283.Session.ACTION_REMOVE));
        assertTrue(testSession.hasPermission(childNPath, org.apache.jackrabbit.api.jsr283.Session.ACTION_REMOVE));

        assertTrue(testAcMgr.hasPrivileges(path, privs));
        assertTrue(testAcMgr.hasPrivileges(childNPath, privs));
    }

    public void testRemovePermission4() throws NotExecutableException, RepositoryException {
        SessionImpl testSession = getTestSession();
        AccessControlManager testAcMgr = getTestACManager();
        /*
          precondition:
          testuser must have READ-only permission on test-node and below
        */
        checkReadOnly(path);
        checkReadOnly(childNPath);

        Privilege[] rmChildNodes = privilegesFromName(Privilege.JCR_REMOVE_CHILD_NODES);
        Privilege[] rmNode = privilegesFromName(Privilege.JCR_REMOVE_NODE);

        // add 'remove_child_nodes' privilge at 'path'...
        givePrivileges(path, rmChildNodes, getRestrictions(superuser, path));
        // ... and add 'remove_node' privilge at 'childNPath'
        givePrivileges(childNPath, rmNode, getRestrictions(superuser, childNPath));
        /*
         expected result:
         - remove not allowed for node at path
         - remove-permission present for node at childNPath
         - both remove_node and remove_childNodes privilege present at childNPath
         */
        assertFalse(testSession.hasPermission(path, org.apache.jackrabbit.api.jsr283.Session.ACTION_REMOVE));
        assertTrue(testSession.hasPermission(childNPath, org.apache.jackrabbit.api.jsr283.Session.ACTION_REMOVE));
        assertTrue(testAcMgr.hasPrivileges(childNPath, new Privilege[] {rmChildNodes[0], rmNode[0]}));
    }

    public void testRemovePermission5() throws NotExecutableException, RepositoryException {
        /*
          precondition:
          testuser must have READ-only permission on test-node and below
        */
        checkReadOnly(path);
        checkReadOnly(childNPath);

        Privilege[] rmNode = privilegesFromName(Privilege.JCR_REMOVE_NODE);

        // add 'remove_node' privilege at 'childNPath'
        givePrivileges(childNPath, rmNode, getRestrictions(superuser, childNPath));
        /*
         expected result:
         - node at childNPath can't be removed since REMOVE_CHILD_NODES is missing.
         */
        assertFalse(getTestSession().hasPermission(childNPath, org.apache.jackrabbit.api.jsr283.Session.ACTION_REMOVE));
    }

    public void testRemovePermission6() throws NotExecutableException, RepositoryException {
        SessionImpl testSession = getTestSession();
        AccessControlManager testAcMgr = getTestACManager();
        /*
          precondition:
          testuser must have READ-only permission on test-node and below
        */
        checkReadOnly(path);
        checkReadOnly(childNPath);

        Privilege[] privs = privilegesFromNames(new String[] {
                Privilege.JCR_REMOVE_CHILD_NODES, Privilege.JCR_REMOVE_NODE
        });
        Privilege[] rmNode = privilegesFromName(Privilege.JCR_REMOVE_NODE);

        // add 'remove_child_nodes' and 'remove_node' privilge at 'path'
        givePrivileges(path, privs, getRestrictions(superuser, path));
        // ... but deny 'remove_node' at childNPath
        withdrawPrivileges(childNPath, rmNode, getRestrictions(superuser, childNPath));
        /*
         expected result:
         - neither node at path nor at childNPath could be removed.
         - no remove_node privilege at childNPath
         - read, remove_child_nodes privilege at childNPath
         */
        assertFalse(testSession.hasPermission(path, org.apache.jackrabbit.api.jsr283.Session.ACTION_REMOVE));
        assertFalse(testSession.hasPermission(childNPath, org.apache.jackrabbit.api.jsr283.Session.ACTION_REMOVE));
        assertTrue(testAcMgr.hasPrivileges(childNPath, privilegesFromNames(new String[] {Privilege.JCR_READ, Privilege.JCR_REMOVE_CHILD_NODES})));
        assertFalse(testAcMgr.hasPrivileges(childNPath, privilegesFromName(Privilege.JCR_REMOVE_NODE)));
    }

    public void testRemovePermission7() throws NotExecutableException, RepositoryException {
        SessionImpl testSession = getTestSession();
        AccessControlManager testAcMgr = getTestACManager();
        /*
          precondition:
          testuser must have READ-only permission on test-node and below
        */
        checkReadOnly(path);
        checkReadOnly(childNPath);

        Privilege[] rmChildNodes = privilegesFromName(Privilege.JCR_REMOVE_CHILD_NODES);
        Privilege[] rmNode = privilegesFromName(Privilege.JCR_REMOVE_NODE);

        // deny 'remove_child_nodes' at 'path'
        withdrawPrivileges(path, rmChildNodes, getRestrictions(superuser, path));
        // ... but allow 'remove_node' at childNPath
        givePrivileges(childNPath, rmNode, getRestrictions(superuser, childNPath));
        /*
         expected result:
         - node at childNPath can't be removed.
         */
        assertFalse(testSession.hasPermission(childNPath, org.apache.jackrabbit.api.jsr283.Session.ACTION_REMOVE));

        // additionally add remove_child_nodes priv at 'childNPath'
        givePrivileges(childNPath, rmChildNodes, getRestrictions(superuser, childNPath));
        /*
         expected result:
         - node at childNPath still can't be removed.
         - but both privileges (remove_node, remove_child_nodes) are present.
         */
        assertFalse(testSession.hasPermission(childNPath, org.apache.jackrabbit.api.jsr283.Session.ACTION_REMOVE));
        assertTrue(testAcMgr.hasPrivileges(childNPath, new Privilege[] {rmChildNodes[0], rmNode[0]}));
    }

    public void testRemovePermission8() throws NotExecutableException, RepositoryException {
        AccessControlManager testAcMgr = getTestACManager();
        /*
          precondition:
          testuser must have READ-only permission on test-node and below
        */
        checkReadOnly(path);
        checkReadOnly(childNPath);

        Privilege[] rmChildNodes = privilegesFromName(Privilege.JCR_REMOVE_CHILD_NODES);
        Privilege[] rmNode = privilegesFromName(Privilege.JCR_REMOVE_NODE);

        // add 'remove_child_nodes' at 'path
        givePrivileges(path, rmChildNodes, getRestrictions(superuser, path));
        // deny 'remove_node' at 'path'
        withdrawPrivileges(path, rmNode, getRestrictions(superuser, path));
        // and allow 'remove_node' at childNPath
        givePrivileges(childNPath, rmNode, getRestrictions(superuser, childNPath));
        /*
         expected result:
         - remove permission must be granted at childNPath
         */
        assertTrue(getTestSession().hasPermission(childNPath, org.apache.jackrabbit.api.jsr283.Session.ACTION_REMOVE));
        assertTrue(testAcMgr.hasPrivileges(childNPath, new Privilege[] {rmChildNodes[0], rmNode[0]}));
    }

    public void testSessionMove() throws RepositoryException, NotExecutableException {
        /*
          precondition:
          testuser must have READ-only permission on test-node and below
        */
        checkReadOnly(path);
        checkReadOnly(childNPath);
        SessionImpl testSession = getTestSession();

        String destPath = path + "/" + nodeName1;

        // give 'add_child_nodes' and 'nt-management' privilege
        // -> not sufficient privileges for a move
        givePrivileges(path, privilegesFromNames(new String[] {Privilege.JCR_ADD_CHILD_NODES, Privilege.JCR_NODE_TYPE_MANAGEMENT}), getRestrictions(superuser, path));
        try {
            testSession.move(childNPath, destPath);
            testSession.save();
            fail("Move requires add and remove permission.");
        } catch (AccessDeniedException e) {
            // success.
        }

        // add 'remove_child_nodes' at 'path
        // -> not sufficient for a move since 'remove_node' privilege is missing
        //    on the move-target
        givePrivileges(path, privilegesFromName(Privilege.JCR_REMOVE_CHILD_NODES), getRestrictions(superuser, path));
        try {
            testSession.move(childNPath, destPath);
            testSession.save();
            fail("Move requires add and remove permission.");
        } catch (AccessDeniedException e) {
            // success.
        }

        // allow 'remove_node' at childNPath
        // -> now move must succeed
        givePrivileges(childNPath, privilegesFromName(Privilege.JCR_REMOVE_NODE), getRestrictions(superuser, childNPath));
        testSession.move(childNPath, destPath);
        testSession.save();

        // withdraw  'add_child_nodes' privilege on former src-parent
        // -> moving child-node back must fail
        withdrawPrivileges(path, privilegesFromName(Privilege.JCR_ADD_CHILD_NODES), getRestrictions(superuser, path));
        try {
            testSession.move(destPath, childNPath);
            testSession.save();
            fail("Move requires add and remove permission.");
        } catch (AccessDeniedException e) {
            // success.
        }
    }

    public void testWorkspaceMove() throws RepositoryException, NotExecutableException {
        /*
          precondition:
          testuser must have READ-only permission on test-node and below
        */
        checkReadOnly(path);
        checkReadOnly(childNPath);
        SessionImpl testSession = getTestSession();

        String destPath = path + "/" + nodeName1;

        // give 'add_child_nodes', 'nt-mgmt' privilege
        // -> not sufficient privileges for a move.
        givePrivileges(path, privilegesFromNames(new String[] {Privilege.JCR_ADD_CHILD_NODES,
                Privilege.JCR_NODE_TYPE_MANAGEMENT}), getRestrictions(superuser, path));
        try {
            testSession.getWorkspace().move(childNPath, destPath);
            fail("Move requires add and remove permission.");
        } catch (AccessDeniedException e) {
            // success.
        }

        // add 'remove_child_nodes' at 'path
        // -> no sufficient for a move since 'remove_node' privilege is missing
        //    on the move-target
        givePrivileges(path, privilegesFromName(Privilege.JCR_REMOVE_CHILD_NODES), getRestrictions(superuser, path));
        try {
            testSession.getWorkspace().move(childNPath, destPath);
            fail("Move requires add and remove permission.");
        } catch (AccessDeniedException e) {
            // success.
        }

        // allow 'remove_node' at childNPath
        // -> now move must succeed
        givePrivileges(childNPath, privilegesFromName(Privilege.JCR_REMOVE_NODE),
                getRestrictions(superuser, childNPath));
        testSession.getWorkspace().move(childNPath, destPath);

        // withdraw  'add_child_nodes' privilege on former src-parent
        // -> moving child-node back must fail
        withdrawPrivileges(path, privilegesFromName(Privilege.JCR_ADD_CHILD_NODES), getRestrictions(superuser, path));
        try {
            testSession.getWorkspace().move(destPath, childNPath);
            fail("Move requires add and remove permission.");
        } catch (AccessDeniedException e) {
            // success.
        }
    }

    public void testGroupPermissions() throws NotExecutableException, RepositoryException {
        Group testGroup = getTestGroup();
        AccessControlManager testAcMgr = getTestACManager();
        /*
         precondition:
         testuser must have READ-only permission on test-node and below
        */
        checkReadOnly(path);

        /* add privileges for the Group the test-user is member of */
        Privilege[] privileges = privilegesFromName(Privilege.JCR_MODIFY_PROPERTIES);
        givePrivileges(path, testGroup.getPrincipal(), privileges, getRestrictions(superuser, path));

        /* testuser must get the permissions/privileges inherited from
           the group it is member of.
         */
        String actions = org.apache.jackrabbit.api.jsr283.Session.ACTION_SET_PROPERTY + "," + org.apache.jackrabbit.api.jsr283.Session.ACTION_READ;

        assertTrue(getTestSession().hasPermission(path, actions));
        Privilege[] privs = privilegesFromName(Privilege.JCR_MODIFY_PROPERTIES);
        assertTrue(testAcMgr.hasPrivileges(path, privs));
    }

    public void testMixedUserGroupPermissions() throws NotExecutableException, RepositoryException {
        Group testGroup = getTestGroup();
        AccessControlManager testAcMgr = getTestACManager();
        /*
         precondition:
         testuser must have READ-only permission on test-node and below
        */
        checkReadOnly(path);

        /* explicitely withdraw MODIFY_PROPERTIES for the user */
        Privilege[] privileges = privilegesFromName(Privilege.JCR_MODIFY_PROPERTIES);
        withdrawPrivileges(path, testUser.getPrincipal(), privileges, getRestrictions(superuser, path));
        /* give MODIFY_PROPERTIES privilege for a Group the test-user is member of */
        givePrivileges(path, testGroup.getPrincipal(), privileges, getRestrictions(superuser, path));
        /*
         since user-permissions overrule the group permissions, testuser must
         not have set_property action / modify_properties privilege.
         */
        String actions = org.apache.jackrabbit.api.jsr283.Session.ACTION_SET_PROPERTY;
        assertFalse(getTestSession().hasPermission(path, actions));
        assertFalse(testAcMgr.hasPrivileges(path, privilegesFromName(Privilege.JCR_MODIFY_PROPERTIES)));
    }

    public void testNewNodes() throws RepositoryException, NotExecutableException {
        AccessControlManager testAcMgr = getTestACManager();
        /*
         precondition:
         testuser must have READ-only permission on test-node and below
        */
        checkReadOnly(path);

        /* create some new nodes below 'path' */
        Node n = ((SessionImpl) superuser).getNode(path);
        for (int i = 0; i < 5; i++) {
            n = n.addNode(nodeName2, testNodeType);
        }
        superuser.save();

        /* make sure the same privileges/permissions are granted as at path. */
        String childPath = n.getPath();
        Privilege[] privs = testAcMgr.getPrivileges(childPath);
        assertEquals(PrivilegeRegistry.getBits(privilegesFromName(Privilege.JCR_READ)),
                PrivilegeRegistry.getBits(privs));
        getTestSession().checkPermission(childPath, org.apache.jackrabbit.api.jsr283.Session.ACTION_READ);
    }

    public void testNonExistingItem() throws RepositoryException, NotExecutableException {
        /*
          precondition:
          testuser must have READ-only permission on the root node and below
        */
        Session testSession = getTestSession();
        String rootPath = testSession.getRootNode().getPath();
        checkReadOnly(rootPath);
        testSession.checkPermission(rootPath + "nonExistingItem", org.apache.jackrabbit.api.jsr283.Session.ACTION_READ);
    }

    public void testACItemsAreProtected() throws NotExecutableException, RepositoryException {
        // search for a rep:policy node
        Node policyNode = findPolicyNode(superuser.getRootNode());
        if (policyNode == null) {
            throw new NotExecutableException("no policy node found.");
        }

        assertTrue("The rep:Policy node must be protected", policyNode.getDefinition().isProtected());
        try {
            policyNode.remove();
            fail("rep:Policy node must be protected.");
        } catch (ConstraintViolationException e) {
            // success
        }

        for (NodeIterator it = policyNode.getNodes(); it.hasNext();) {
            Node n = it.nextNode();
            if (n.isNodeType("rep:ACE")) {
                try {
                    n.remove();
                    fail("ACE node must be protected.");
                } catch (ConstraintViolationException e) {
                    // success
                }
                break;
            }
        }

        try {
            policyNode.setProperty("test", "anyvalue");
            fail("rep:policy node must be protected.");
        } catch (ConstraintViolationException e) {
            // success
        }
        try {
            policyNode.addNode("test", "rep:ACE");
            fail("rep:policy node must be protected.");
        } catch (ConstraintViolationException e) {
            // success
        }
    }

    /**
     * the ADD_CHILD_NODES privileges assigned on a node to a specific principal
     * grants the corresponding user the permission to add nodes below the
     * target node but not 'at' the target node.
     *
     * @throws RepositoryException If an error occurs.
     * @throws NotExecutableException If the test cannot be executed.
     */
    public void testAddChildNodePrivilege() throws RepositoryException, NotExecutableException {
        /*
         precondition:
         testuser must have READ-only permission on test-node and below
        */
        checkReadOnly(path);

        /* create a child node below node at 'path' */
        Node n = ((SessionImpl) superuser).getNode(path);
        n = n.addNode(nodeName2, testNodeType);
        superuser.save();

        /* add 'add_child_nodes' privilege for testSession at path. */
        Privilege[] privileges = privilegesFromName(Privilege.JCR_ADD_CHILD_NODES);
        givePrivileges(path, privileges, getRestrictions(superuser, path));

        /* test permissions. expected result:
           - testSession cannot add child-nodes at 'path'
           - testSession can add child-nodes below path
         */
        SessionImpl testSession = getTestSession();
        assertFalse(testSession.hasPermission(path, org.apache.jackrabbit.api.jsr283.Session.ACTION_ADD_NODE));
        assertTrue(testSession.hasPermission(path+"/anychild", org.apache.jackrabbit.api.jsr283.Session.ACTION_ADD_NODE));
        String childPath = n.getPath();
        assertTrue(testSession.hasPermission(childPath, org.apache.jackrabbit.api.jsr283.Session.ACTION_ADD_NODE));
    }

    public void testAclReferingToRemovedPrincipal() throws
            NotExecutableException, RepositoryException {

        JackrabbitAccessControlList acl = givePrivileges(path, privilegesFromName(PrivilegeRegistry.REP_WRITE), getRestrictions(superuser, path));
        String acPath = acl.getPath();

        // remove the test user
        testUser.remove();
        testUser = null;

        // try to retrieve the acl again
        Session s = helper.getSuperuserSession();
        try {
            AccessControlManager acMgr = getAccessControlManager(s);
            acMgr.getPolicies(acPath);
        } finally {
            s.logout();
        }
    }

    private static Node findPolicyNode(Node start) throws RepositoryException {
        Node policyNode = null;
        if (start.isNodeType("rep:Policy")) {
            policyNode = start;
        }
        for (NodeIterator it = start.getNodes(); it.hasNext() && policyNode == null;) {
            Node n = it.nextNode();
            if (!"jcr:system".equals(n.getName())) {
                policyNode = findPolicyNode(n);
            }
        }
        return policyNode;
    }
}