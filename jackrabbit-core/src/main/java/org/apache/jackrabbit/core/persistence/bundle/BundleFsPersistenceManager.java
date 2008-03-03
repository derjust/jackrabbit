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
package org.apache.jackrabbit.core.persistence.bundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.jackrabbit.core.fs.FileSystem;
import org.apache.jackrabbit.core.fs.BasedFileSystem;
import org.apache.jackrabbit.core.fs.local.LocalFileSystem;
import org.apache.jackrabbit.core.persistence.PMContext;
import org.apache.jackrabbit.core.persistence.bundle.util.NodePropBundle;
import org.apache.jackrabbit.core.persistence.bundle.util.ErrorHandling;
import org.apache.jackrabbit.core.persistence.bundle.util.BundleBinding;
import org.apache.jackrabbit.core.persistence.bundle.util.TrackingInputStream;
import org.apache.jackrabbit.core.persistence.util.Serializer;
import org.apache.jackrabbit.core.persistence.util.BLOBStore;
import org.apache.jackrabbit.core.persistence.util.FileSystemBLOBStore;
import org.apache.jackrabbit.core.NodeId;
import org.apache.jackrabbit.core.PropertyId;
import org.apache.jackrabbit.core.state.ItemStateException;
import org.apache.jackrabbit.core.state.NodeReferencesId;
import org.apache.jackrabbit.core.state.NoSuchItemStateException;
import org.apache.jackrabbit.core.state.NodeReferences;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;

/**
 * This is a generic persistence manager that stores the {@link NodePropBundle}s
 * in a filesystem.
 * <p/>
 * Configuration:<br>
 * <ul>
 * <li>&lt;param name="{@link #setBundleCacheSize(String) bundleCacheSize}" value="8"/>
 * <li>&lt;param name="{@link #setBlobFSBlockSize(String) blobFSBlockSize}" value="0"/>
 * <li>&lt;param name="{@link #setBlobFSInitialCacheSize(String) blobFSInitialCacheSize}" value="100"/>
 * <li>&lt;param name="{@link #setBlobFSMaximumCacheSize(String) blobFSMaximumCacheSize}" value="4000"/>
 * <li>&lt;param name="{@link #setItemFSBlockSize(String) blobFSBlockSize}" value="0"/>
 * <li>&lt;param name="{@link #setItemFSInitialCacheSize(String) blobFSInitialCacheSize}" value="100"/>
 * <li>&lt;param name="{@link #setItemFSMaximumCacheSize(String) blobFSMaximumCacheSize}" value="4000"/>
 * <li>&lt;param name="{@link #setMinBlobSize(String) minBlobSize}" value="4096"/>
 * <li>&lt;param name="{@link #setErrorHandling(String) errorHandling}" value=""/>
 * </ul>
 */
public class BundleFsPersistenceManager extends AbstractBundlePersistenceManager {

    /** the cvs/svn id */
    static final String CVS_ID = "$URL$ $Rev$ $Date$";

    /** the default logger */
    private static Logger log = LoggerFactory.getLogger(BundleFsPersistenceManager.class);

    /** flag indicating if this manager was initialized */
    protected boolean initialized = false;

    /** initial size of buffer used to serialize objects */
    protected static final int INITIAL_BUFFER_SIZE = 1024;

    /** file system where BLOB data is stored */
    protected BundleFsPersistenceManager.CloseableBLOBStore blobStore;

    /**
     * Default blocksize for BLOB filesystem:
     * @see #setBlobFSBlockSize(String)
     */
    private int blobFSBlockSize = 0;

    /**
     * Default initial cache size for BLOB filesystem: 100, req. 25KB
     * @see #setBlobFSInitialCacheSize(String)
     */
    private int blobFSInitialCache = 100;

    /**
     * Default max cache size for BLOB filesystem: 4000, req. 1000KB
     * @see #setBlobFSMaximumCacheSize(String)
     */
    private int blobFSMaximumCache = 4000;

    /**
     * Default blocksize for item filesystem:
     * @see #setItemFSBlockSize(String)
     */
    private int itemFSBlockSize = 0;

    /**
     * Default initial cache size for item filesystem: 100, req. 25KB
     * @see #setItemFSInitialCacheSize(String)
     */
    private int itemFSInitialCache = 100;

    /**
     * Default max cache size for item filesystem: 4000, req. 1000KB
     * @see #setItemFSMaximumCacheSize(String)
     */
    private int itemFSMaximumCache = 4000;

    /**
     * the minimum size of a property until it gets written to the blob store
     * @see #setMinBlobSize(String)
     */
    private int minBlobSize = 0x1000;

    /**
     * the filesystem where the items are stored
     */
    private FileSystem itemFs;

    /**
     * flag for error handling
     */
    protected ErrorHandling errorHandling = new ErrorHandling();

    /**
     * the bundle binding
     */
    protected BundleBinding binding;

    /**
     * the name of this persistence manager
     */
    private String name = super.toString();


    /**
     * Returns the configured block size of the blob cqfs
     * @return the block size.
     */
    public String getBlobFSBlockSize() {
        return String.valueOf(blobFSBlockSize);
    }

    /**
     * Sets the block size of the blob fs and controlls how blobs are handled.
     * <br>
     * If the size is > 0, it must be a power of 2 and is used as blocksize
     * for the cqfs. blobs are then stored within that cqfs. A call to
     * {@link #useCqFsBlobStore()} will return <code>true</code>.
     * <br>
     * If the size is 0, the cqfs is not used at all, and the blobs are stored
     * within the workspace's physical filesystem. A call to
     * {@link #useLocalFsBlobStore()} will return <code>true</code>.
     * <br>
     * If the size is &lt; 0, the cqfs is not used at all, and the blobls are
     * stored within the item filesystem. A call to
     * {@link #useItemBlobStore()} will return <code>true</code>.
     * <br>
     * Please note that not all binary properties are considered as blobs. They
     * are only stored in the respective blob store, if their size exceeds
     * {@link #getMinBlobSize()}.
     *
     * @param size the block size
     */
    public void setBlobFSBlockSize(String size) {
        this.blobFSBlockSize = Integer.decode(size).intValue();
    }

    /**
     * Returns <code>true</code> if the blobs are stored in the DB.
     * @return <code>true</code> if the blobs are stored in the DB.
     */
    public boolean useItemBlobStore() {
        return blobFSBlockSize < 0;
    }

    /**
     * Returns <code>true</code> if the blobs are stored in the local fs.
     * @return <code>true</code> if the blobs are stored in the local fs.
     */
    public boolean useLocalFsBlobStore() {
        return blobFSBlockSize == 0;
    }

    /**
     * Returns <code>true</code> if the blobs are stored in the cqfs.
     * @return <code>true</code> if the blobs are stored in the cqfs.
     */
    public boolean useCqFsBlobStore() {
        return blobFSBlockSize > 0;
    }

    /**
     * Returns the configured inital cache size of the blobfs.
     * @return the configured inital cache size of the blobfs.
     */
    public String getBlobFSInitialCacheSize() {
        return String.valueOf(blobFSInitialCache);
    }

    /**
     * Sets the initial cache size of the blob fs. This only applies to cqfs
     * base blobstores, i.e. if {@link #useCqFsBlobStore()} returns
     * <code>true</code>.
     * @param size the initial size
     */
    public void setBlobFSInitialCacheSize(String size) {
        this.blobFSInitialCache = Integer.decode(size).intValue();
    }

    /**
     * Returns the configured maximal size of the blobfs.
     * @return the configured maximal size of the blobfs.
     */
    public String getBlobFSMaximumCacheSize() {
        return String.valueOf(blobFSMaximumCache);
    }

    /**
     * Sets the maximal cache size of the blob fs. This only applies to cqfs
     * base blobstores, i.e. if {@link #useCqFsBlobStore()} returns
     * <code>true</code>.
     * @param size the maximal size
     */
    public void setBlobFSMaximumCacheSize(String size) {
        this.blobFSMaximumCache = Integer.decode(size).intValue();
    }


    /**
     * Returns the configured block size of the item cqfs
     * @return the block size.
     */
    public String getItemFSBlockSize() {
        return String.valueOf(itemFSBlockSize);
    }

    /**
     * Sets the block size of the item fs.
     * <br>
     * If the size is > 0, it must be a power of 2 and is used as blocksize
     * for the cqfs. items are then stored within that cqfs.
     * <br>
     * If the size is 0, the cqfs is not used at all, and the items are stored
     * within the workspace's physical filesystem.
     *
     * @param size the block size
     */
    public void setItemFSBlockSize(String size) {
        this.itemFSBlockSize = Integer.decode(size).intValue();
    }

    /**
     * Returns the configured inital cache size of the itemfs.
     * @return the configured inital cache size of the itemfs.
     */
    public String getItemFSInitialCacheSize() {
        return String.valueOf(itemFSInitialCache);
    }

    /**
     * Sets the initial cache size of the item fs. This only applies to cqfs
     * based item stores.
     *
     * @param size the initial size
     */
    public void setItemFSInitialCacheSize(String size) {
        this.itemFSInitialCache = Integer.decode(size).intValue();
    }

    /**
     * Returns the configured maximal size of the itemfs.
     * @return the configured maximal size of the itemfs.
     */
    public String getItemFSMaximumCacheSize() {
        return String.valueOf(itemFSMaximumCache);
    }

    /**
     * Sets the maximal cache size of the item fs. This only applies to cqfs
     * base item storea.
     * @param size the maximal size
     */
    public void setItemFSMaximumCacheSize(String size) {
        this.itemFSMaximumCache = Integer.decode(size).intValue();
    }

    /**
     * Returns the miminum blob size.
     * @return the miminum blob size.
     */
    public int getMinBlobSize() {
        return minBlobSize;
    }

    /**
     * Sets the minumum blob size. This size defines the threshhold of which
     * size a property is included in the bundle or is stored in the blob store.
     *
     * @param minBlobSize
     */
    public void setMinBlobSize(String minBlobSize) {
        this.minBlobSize = Integer.decode(minBlobSize).intValue();
    }

    /**
     * Sets the error handling behaviour of this manager. See {@link ErrorHandling}
     * for details about the flags.
     *
     * @param errorHandling
     */
    public void setErrorHandling(String errorHandling) {
        this.errorHandling = new ErrorHandling(errorHandling);
    }

    /**
     * Returns the error handling configuration of this manager
     * @return the error handling configuration of this manager
     */
    public String getErrorHandling() {
        return errorHandling.toString();
    }

    /**
     * {@inheritDoc}
     */
    public void init(PMContext context) throws Exception {
        if (initialized) {
            throw new IllegalStateException("already initialized");
        }
        super.init(context);

        this.name = context.getHomeDir().getName();

        // create item fs
        if (itemFSBlockSize == 0) {
            itemFs = new BasedFileSystem(context.getFileSystem(), "items");
        } else {
            /*
            CQFileSystem cqfs = new CQFileSystem();
            cqfs.setPath(new File(context.getHomeDir(), "items.dat").getCanonicalPath());
            cqfs.setAutoRepair(false);
            cqfs.setAutoSync(false);
            cqfs.setBlockSize(itemFSBlockSize);
            cqfs.setCacheInitialBlocks(itemFSInitialCache);
            cqfs.setCacheMaximumBlocks(itemFSMaximumCache);
            cqfs.init();
            itemFs = cqfs;
            */
        }

        // create correct blob store
        if (useLocalFsBlobStore()) {
            LocalFileSystem blobFS = new LocalFileSystem();
            blobFS.setRoot(new File(context.getHomeDir(), "blobs"));
            blobFS.init();
            blobStore = new BundleFsPersistenceManager.FSBlobStore(blobFS);
        } else if (useItemBlobStore()) {
            blobStore = new BundleFsPersistenceManager.FSBlobStore(itemFs);
        } else /* useCqFsBlobStore() */ {
//blobStore = createCQFSBlobStore(context);
        }

        // load namespaces
        binding = new BundleBinding(errorHandling, blobStore, getNsIndex(), getNameIndex());
        binding.setMinBlobSize(minBlobSize);

        initialized = true;
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void close() throws Exception {
        if (!initialized) {
            throw new IllegalStateException("not initialized");
        }

        try {
            // close blob store
            blobStore.close();
            blobStore = null;
            itemFs.close();
            itemFs = null;
        } finally {
            initialized = false;
        }
    }

    /**
     * {@inheritDoc}
     */
    protected synchronized NodePropBundle loadBundle(NodeId id)
            throws ItemStateException {
        DataInputStream din = null;
        try {
            String path = buildNodeFilePath(null, id).toString();
            if (!itemFs.exists(path)) {
                return null;
            }
            InputStream in = itemFs.getInputStream(path);
            TrackingInputStream cin = new TrackingInputStream(in);
            din = new DataInputStream(cin);
            NodePropBundle bundle = binding.readBundle(din, id);
            bundle.setSize(cin.getPosition());
            return bundle;
        } catch (Exception e) {
            String msg = "failed to read bundle: " + id + ": " + e;
            log.error(msg);
            throw new ItemStateException(msg, e);
        } finally {
            closeStream(din);
        }
    }

    /**
     * {@inheritDoc}
     */
    protected synchronized boolean existsBundle(NodeId id) throws ItemStateException {
        try {
            StringBuffer buf = buildNodeFilePath(null, id);
            return itemFs.exists(buf.toString());
        } catch (Exception e) {
            String msg = "failed to check existence of bundle: " + id;
            BundleFsPersistenceManager.log.error(msg, e);
            throw new ItemStateException(msg, e);
        }
    }
    
    /**
     * Creates the file path for the given node id that is
     * suitable for storing node states in a filesystem.
     *
     * @param buf buffer to append to or <code>null</code>
     * @param id the id of the node
     * @return the buffer with the appended data.
     */
    protected StringBuffer buildNodeFilePath(StringBuffer buf, NodeId id) {
        if (buf == null) {
            buf = new StringBuffer();
        }
        buildNodeFolderPath(buf, id);
        buf.append('.');
        buf.append(NODEFILENAME);
        return buf;
    }    
    
    /**
     * Creates the file path for the given references id that is
     * suitable for storing reference states in a filesystem.
     *
     * @param buf buffer to append to or <code>null</code>
     * @param id the id of the node
     * @return the buffer with the appended data.
     */
    protected StringBuffer buildNodeReferencesFilePath(StringBuffer buf,
                                                       NodeReferencesId id) {
        if (buf == null) {
            buf = new StringBuffer();
        }
        buildNodeFolderPath(buf, id.getTargetId());
        buf.append('.');
        buf.append(NODEREFSFILENAME);
        return buf;
    }    

    /**
     * {@inheritDoc}
     */
    protected synchronized void storeBundle(NodePropBundle bundle) throws ItemStateException {
        try {
            StringBuffer buf = buildNodeFolderPath(null, bundle.getId());
            buf.append('.');
            buf.append(NODEFILENAME);
            String fileName = buf.toString();
            String dir = fileName.substring(0, fileName.lastIndexOf(FileSystem.SEPARATOR_CHAR));
            if (!itemFs.exists(dir)) {
                itemFs.createFolder(dir);
            }
            OutputStream out = itemFs.getOutputStream(fileName);
            DataOutputStream dout = new DataOutputStream(out);
            binding.writeBundle(dout, bundle);
            dout.close();
        } catch (Exception e) {
            String msg = "failed to write bundle: " + bundle.getId();
            BundleFsPersistenceManager.log.error(msg, e);
            throw new ItemStateException(msg, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    protected synchronized void destroyBundle(NodePropBundle bundle) throws ItemStateException {
        try {
            StringBuffer buf = buildNodeFilePath(null, bundle.getId());
            itemFs.deleteFile(buf.toString());
        } catch (Exception e) {
            if (e instanceof NoSuchItemStateException) {
                throw (NoSuchItemStateException) e;
            }
            String msg = "failed to delete bundle: " + bundle.getId();
            BundleFsPersistenceManager.log.error(msg, e);
            throw new ItemStateException(msg, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized NodeReferences load(NodeReferencesId targetId)
            throws NoSuchItemStateException, ItemStateException {
        if (!initialized) {
            throw new IllegalStateException("not initialized");
        }
        InputStream in = null;
        try {
            String path = buildNodeReferencesFilePath(null, targetId).toString();
            if (!itemFs.exists(path)) {
                // special case
                throw new NoSuchItemStateException(targetId.toString());
            }
            in = itemFs.getInputStream(path);
            NodeReferences refs = new NodeReferences(targetId);
            Serializer.deserialize(refs, in);
            return refs;
        } catch (NoSuchItemStateException e) {
            throw e;
        } catch (Exception e) {
            String msg = "failed to read references: " + targetId;
            BundleFsPersistenceManager.log.error(msg, e);
            throw new ItemStateException(msg, e);
        } finally {
            closeStream(in);
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void store(NodeReferences refs)
            throws ItemStateException {
        if (!initialized) {
            throw new IllegalStateException("not initialized");
        }
        try {
            StringBuffer buf = buildNodeFolderPath(null, refs.getTargetId());
            buf.append('.');
            buf.append(NODEREFSFILENAME);
            String fileName = buf.toString();
            String dir = fileName.substring(0, fileName.lastIndexOf(FileSystem.SEPARATOR_CHAR));
            if (!itemFs.exists(dir)) {
                itemFs.createFolder(dir);
            }
            OutputStream out = itemFs.getOutputStream(fileName);
            Serializer.serialize(refs, out);
            out.close();
        } catch (Exception e) {
            String msg = "failed to write property state: " + refs.getTargetId();
            BundleFsPersistenceManager.log.error(msg, e);
            throw new ItemStateException(msg, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void destroy(NodeReferences refs) throws ItemStateException {
        if (!initialized) {
            throw new IllegalStateException("not initialized");
        }
        try {
            StringBuffer buf = buildNodeReferencesFilePath(null, refs.getId());
            itemFs.deleteFile(buf.toString());
        } catch (Exception e) {
            if (e instanceof NoSuchItemStateException) {
                throw (NoSuchItemStateException) e;
            }
            String msg = "failed to delete references: " + refs.getTargetId();
            BundleFsPersistenceManager.log.error(msg, e);
            throw new ItemStateException(msg, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized boolean exists(NodeReferencesId targetId) throws ItemStateException {
        if (!initialized) {
            throw new IllegalStateException("not initialized");
        }
        try {
            StringBuffer buf = buildNodeReferencesFilePath(null, targetId);
            return itemFs.exists(buf.toString());
        } catch (Exception e) {
            String msg = "failed to check existence of node references: " + targetId;
            BundleFsPersistenceManager.log.error(msg, e);
            throw new ItemStateException(msg, e);
        }
    }

    /**
     * closes the input stream
     * @param ins
     */
    protected void closeStream(InputStream ins) {
        if (ins != null) {
            try {
                ins.close();
            } catch (IOException ignore) {
                // ignore
            }
        }
    }

    /**
     * logs an sql exception
     * @param message
     * @param se
     */
    protected void logException(String message, SQLException se) {
        if (message != null) {
            BundleFsPersistenceManager.log.error(message);
        }
        BundleFsPersistenceManager.log.error("       Reason: " + se.getMessage());
        BundleFsPersistenceManager.log.error("   State/Code: " + se.getSQLState() + "/" +
                se.getErrorCode());
        BundleFsPersistenceManager.log.debug("   dump:", se);
    }

    /**
     * @inheritDoc
     */
    public String toString() {
        return name;
    }

    /**
     * Helper interface for closeable stores
     */
    protected static interface CloseableBLOBStore extends BLOBStore {
        void close();
    }

    /**
     * own implementation of the filesystem blob store that uses a different
     * blob-id scheme.
     */
    private class FSBlobStore extends FileSystemBLOBStore implements BundleFsPersistenceManager.CloseableBLOBStore {

        private FileSystem fs;

        public FSBlobStore(FileSystem fs) {
            super(fs);
            this.fs = fs;
        }

        public String createId(PropertyId id, int index) {
            return buildBlobFilePath(null, id, index).toString();
        }

        public void close() {
            try {
                fs.close();
                fs = null;
            } catch (Exception e) {
                // ignore
            }
        }
    }

}