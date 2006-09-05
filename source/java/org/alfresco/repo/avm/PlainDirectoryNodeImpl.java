/*
 * Copyright (C) 2006 Alfresco, Inc.
 *
 * Licensed under the Mozilla Public License version 1.1 
 * with a permitted attribution clause. You may obtain a
 * copy of the License at
 *
 *   http://www.alfresco.org/legal/license.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */

package org.alfresco.repo.avm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.alfresco.service.cmr.avm.AVMBadArgumentException;
import org.alfresco.service.cmr.avm.AVMNodeDescriptor;

/**
 * A plain directory.  No monkey tricks except for possiblyCopy.
 * @author britt
 */
class PlainDirectoryNodeImpl extends DirectoryNodeImpl implements PlainDirectoryNode
{
    static final long serialVersionUID = 9423813734583003L;

    /**
     * Make up a new directory with nothing in it.
     * @param store
     */
    public PlainDirectoryNodeImpl(AVMStore store)
    {
        super(store.getAVMRepository().issueID(), store);
        AVMContext.fgInstance.fAVMNodeDAO.save(this);
        AVMContext.fgInstance.fAVMNodeDAO.flush();
    }
    
    /**
     * Anonymous constructor.
     */
    protected PlainDirectoryNodeImpl()
    {
    }
    
    /**
     * Copy like constructor.
     * @param other The other directory.
     * @param repos The AVMStore Object that will own us.
     */
    @SuppressWarnings("unchecked")
    public PlainDirectoryNodeImpl(PlainDirectoryNode other,
                                  AVMStore store)
    {
        super(store.getAVMRepository().issueID(), store);
        AVMContext.fgInstance.fAVMNodeDAO.save(this);
        for (ChildEntry child : AVMContext.fgInstance.fChildEntryDAO.getByParent(other))
        {
            ChildEntry newChild = new ChildEntryImpl(child.getName(),
                                                     this,
                                                     child.getChild());
            AVMContext.fgInstance.fChildEntryDAO.save(newChild);
        }
        AVMContext.fgInstance.fAVMNodeDAO.flush();
        copyProperties(other);
        copyAspects(other);
    }

    /**
     * Does this directory directly contain the given node. 
     * @param node The node to check.
     * @return Whether it was found.
     */
    public boolean directlyContains(AVMNode node)
    {
        return AVMContext.fgInstance.fChildEntryDAO.getByParentChild(this, node) != null;
    }

    /**
     * Get a directory listing.
     * @param lPath The lookup path.
     * @return The listing.
     */
    @SuppressWarnings("unchecked")
    public Map<String, AVMNode> getListing(Lookup lPath)
    {
        Map<String, AVMNode> result = new HashMap<String, AVMNode>();
        List<ChildEntry> children = AVMContext.fgInstance.fChildEntryDAO.getByParent(this);
        for (ChildEntry child : children)
        {
            result.put(child.getName(), child.getChild());
        }
        return result;
    }

    /**
     * Get a listing of the nodes directly contained by a directory.
     * @param lPath The Lookup to this directory.
     * @return A Map of names to nodes.
     */
    public Map<String, AVMNode> getListingDirect(Lookup lPath)
    {
        return getListing(lPath);
    }

    /**
     * Get a listing of from a directory node descriptor.
     * @param dir The directory node descriptor.
     * @return A Map of names to node descriptors.
     */
    public SortedMap<String, AVMNodeDescriptor> getListing(AVMNodeDescriptor dir)
    {
        if (dir.getPath() == null)
        {
            throw new AVMBadArgumentException("Path is null.");
        }
        SortedMap<String, AVMNodeDescriptor> result = new TreeMap<String, AVMNodeDescriptor>();
        List<ChildEntry> children = AVMContext.fgInstance.fChildEntryDAO.getByParent(this);
        for (ChildEntry child : children)
        {
            result.put(child.getName(), 
                       child.getChild().getDescriptor(dir.getPath(), child.getName(), dir.getIndirection()));
        }
        return result;
    }

    /**
     * Get the names of nodes deleted in this directory.
     * @return A List of names.
     */
    public List<String> getDeletedNames()
    {
        return new ArrayList<String>();
    }

    /**
     * Lookup a child by name.
     * @param lPath The lookup path so far.
     * @param name The name to lookup.
     * @param version The version to look under.
     * @param write Whether this is in a write context.
     * @return The child or null.
     */
    @SuppressWarnings("unchecked")
    public AVMNode lookupChild(Lookup lPath, String name, int version, boolean write)
    {
        // We're doing the hand unrolling of the proxy because
        // Hibernate/CGLIB proxies are broken.
        ChildEntry entry = AVMContext.fgInstance.fChildEntryDAO.getByNameParent(name, this);
        if (entry == null)
        {
            return null;
        }
        return AVMNodeUnwrapper.Unwrap(entry.getChild());
    }

    /**
     * Lookup a child using a node descriptor as context.
     * @param mine The node descriptor for this.
     * @param name The name of the child to lookup.
     * @return A node descriptor for the child.
     */
    public AVMNodeDescriptor lookupChild(AVMNodeDescriptor mine, String name)
    {
        if (mine.getPath() == null)
        {
            throw new AVMBadArgumentException("Path is null.");
        }
        ChildEntry entry = AVMContext.fgInstance.fChildEntryDAO.getByNameParent(name, this);
        if (entry == null)
        {
            return null;
        }
        return entry.getChild().getDescriptor(mine.getPath(), name, (String)null);
    }

    /**
     * Remove a child, no copying.
     * @param name The name of the child to remove.
     */
    @SuppressWarnings("unchecked")
    public void removeChild(String name)
    {
        ChildEntry entry = AVMContext.fgInstance.fChildEntryDAO.getByNameParent(name, this);
        if (entry != null)
        {
            AVMContext.fgInstance.fChildEntryDAO.delete(entry);
        }
    }

    /**
     * Put a new child node into this directory.  No copy.
     * @param name The name of the child.
     * @param node The node to add.
     */
    public void putChild(String name, AVMNode node)
    {
        ChildEntry existing = AVMContext.fgInstance.fChildEntryDAO.getByNameParent(name, this);
        if (existing != null)
        {
            existing.setChild(node);
            AVMContext.fgInstance.fChildEntryDAO.update(existing);
        }
        else
        {
            ChildEntry entry = new ChildEntryImpl(name, this, node);
            AVMContext.fgInstance.fChildEntryDAO.save(entry);
        }
    }

    /**
     * Copy on write logic.
     * @param lPath The lookup path.
     * @return A brand new copied version.
     */
    public AVMNode copy(Lookup lPath)
    {
        DirectoryNode newMe = null;
        // In a layered context a copy on write creates a new 
        // layered directory.
        if (lPath.isLayered())
        {
            // Subtlety warning: This distinguishes the case of a 
            // Directory that was branched into the layer and one
            // that is indirectly seen in this layer.
            newMe = new LayeredDirectoryNodeImpl(this, lPath.getAVMStore(), lPath,
                                                 lPath.isInThisLayer());
            ((LayeredDirectoryNodeImpl)newMe).setLayerID(lPath.getTopLayer().getLayerID());
        }
        else
        {
            newMe = new PlainDirectoryNodeImpl(this, lPath.getAVMStore());
        }
        newMe.setAncestor(this);
        return newMe;
    }

    /**
     * Get the type of this node. 
     * @return The type of this node.
     */
    public int getType()
    {
        return AVMNodeType.PLAIN_DIRECTORY;
    }

    /**
     * Get a diagnostic String representation.
     * @param lPath The Lookup.
     * @return A diagnostic String representation.
     */
    public String toString(Lookup lPath)
    {
        return "[PD:" + getId() + "]";
    }    
 
    /**
     * Turn this into a primary indirection. This must be in a 
     * layered context.
     * @param lPath The Lookup.
     */
    public void turnPrimary(Lookup lPath)
    {
        assert false : "Should never happen.";
    }

    /**
     * Retarget this directory.  lPath must be in a layered context.
     * @param lPath The Lookup.
     * @param target The target path.
     */
    public void retarget(Lookup lPath, String target)
    {
        assert false : "Should never happen.";
    }

    /**
     * Get the descriptor for this node.
     * @param lPath The Lookup.
     * @param name The name of this node in this context.
     * @return A descriptor.
     */
    public AVMNodeDescriptor getDescriptor(Lookup lPath, String name)
    {
        BasicAttributes attrs = getBasicAttributes();
        String path = lPath.getRepresentedPath();
        if (path.endsWith("/"))
        {
            path = path + name;
        }
        else
        {
            path = path + "/" + name;
        }
        return new AVMNodeDescriptor(path,
                                     name,
                                     AVMNodeType.PLAIN_DIRECTORY,
                                     attrs.getCreator(),
                                     attrs.getOwner(),
                                     attrs.getLastModifier(),
                                     attrs.getCreateDate(),
                                     attrs.getModDate(),
                                     attrs.getAccessDate(),
                                     getId(),
                                     getVersionID(),
                                     null,
                                     false,
                                     -1,
                                     false,
                                     -1);
    }

    /**
     * Get the descriptor for this node.
     * @param lPath The Lookup.
     * @return A descriptor.
     */
    public AVMNodeDescriptor getDescriptor(Lookup lPath)
    {
        BasicAttributes attrs = getBasicAttributes();
        String path = lPath.getRepresentedPath();
        return new AVMNodeDescriptor(path,
                                     path.substring(path.lastIndexOf("/") + 1),
                                     AVMNodeType.PLAIN_DIRECTORY,
                                     attrs.getCreator(),
                                     attrs.getOwner(),
                                     attrs.getLastModifier(),
                                     attrs.getCreateDate(),
                                     attrs.getModDate(),
                                     attrs.getAccessDate(),
                                     getId(),
                                     getVersionID(),
                                     null,
                                     false,
                                     -1,
                                     false,
                                     -1);
    }

    /**
     * Get this node's descriptor.
     * @param parentPath The parent path.
     * @param name The name that we were looked up under.
     * @param parentIndirection The parent indirection.
     * @return This node's node descriptor
     */
    public AVMNodeDescriptor getDescriptor(String parentPath, String name, String parentIndirection)
    {
        BasicAttributes attrs = getBasicAttributes();
        String path = parentPath.endsWith("/") ? parentPath + name : parentPath + "/" + name;
        return new AVMNodeDescriptor(path,
                                     name,
                                     AVMNodeType.PLAIN_DIRECTORY,
                                     attrs.getCreator(),
                                     attrs.getOwner(),
                                     attrs.getLastModifier(),
                                     attrs.getCreateDate(),
                                     attrs.getModDate(),
                                     attrs.getAccessDate(),
                                     getId(),
                                     getVersionID(),
                                     null,
                                     false,
                                     -1,
                                     false,
                                     -1);
    }
}

