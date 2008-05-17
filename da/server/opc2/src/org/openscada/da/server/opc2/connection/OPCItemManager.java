/*
 * This file is part of the OpenSCADA project
 * Copyright (C) 2006-2008 inavare GmbH (http://inavare.com)
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.

 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.openscada.da.server.opc2.connection;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

import org.apache.log4j.Logger;
import org.jinterop.dcom.common.JIException;
import org.jinterop.dcom.core.JIVariant;
import org.openscada.core.Variant;
import org.openscada.da.core.server.DataItemInformation;
import org.openscada.da.server.browser.common.FolderCommon;
import org.openscada.da.server.common.DataItemInformationBase;
import org.openscada.da.server.common.configuration.ConfigurationError;
import org.openscada.da.server.common.factory.FactoryHelper;
import org.openscada.da.server.common.factory.FactoryTemplate;
import org.openscada.da.server.common.item.factory.FolderItemFactory;
import org.openscada.da.server.opc2.Helper;
import org.openscada.da.server.opc2.Hive;
import org.openscada.da.server.opc2.configuration.ItemDescription;
import org.openscada.da.server.opc2.configuration.ItemSourceListener;
import org.openscada.da.server.opc2.job.Worker;
import org.openscada.da.server.opc2.job.impl.ErrorMessageJob;
import org.openscada.da.server.opc2.job.impl.ItemActivationJob;
import org.openscada.da.server.opc2.job.impl.RealizeItemsJob;
import org.openscada.da.server.opc2.job.impl.SyncReadJob;
import org.openscada.da.server.opc2.job.impl.SyncWriteJob;
import org.openscada.opc.dcom.common.KeyedResult;
import org.openscada.opc.dcom.common.KeyedResultSet;
import org.openscada.opc.dcom.common.Result;
import org.openscada.opc.dcom.common.ResultSet;
import org.openscada.opc.dcom.da.OPCDATASOURCE;
import org.openscada.opc.dcom.da.OPCITEMDEF;
import org.openscada.opc.dcom.da.OPCITEMRESULT;
import org.openscada.opc.dcom.da.OPCITEMSTATE;
import org.openscada.opc.dcom.da.WriteRequest;

public class OPCItemManager implements ItemSourceListener
{
    private static Logger logger = Logger.getLogger ( OPCItemManager.class );

    private Map<String, ItemRequest> requestMap = new HashMap<String, ItemRequest> ();
    private Map<String, ItemRequest> requestedMap = new HashMap<String, ItemRequest> ();
    private Map<String, OPCItem> itemMap = new HashMap<String, OPCItem> ();

    private Map<String, Integer> clientHandleMap = new HashMap<String, Integer> ();
    private Map<Integer, String> clientHandleMapRev = new HashMap<Integer, String> ();
    private Map<String, Integer> serverHandleMap = new HashMap<String, Integer> ();
    private Map<Integer, String> serverHandleMapRev = new HashMap<Integer, String> ();

    private Map<String, Boolean> activationRequestMap = new HashMap<String, Boolean> ();
    private Set<String> activeSet = new HashSet<String> ();

    private Queue<WriteRequest> writeRequests = new LinkedList<WriteRequest> ();

    private String itemIdPrefix;

    private Worker worker;
    private Hive hive;
    private FolderCommon flatItemFolder;
    private OPCModel model;
    private ConnectionSetup configuration;

    private FolderItemFactory parentItemFactory;

    public OPCItemManager ( Worker worker, ConnectionSetup configuration, OPCModel model, Hive hive, FolderItemFactory parentItemFactory )
    {
        this.worker = worker;
        this.model = model;
        this.hive = hive;
        this.configuration = configuration;
        this.parentItemFactory = parentItemFactory;

        this.itemIdPrefix = this.configuration.getItemIdPrefix ();

        this.flatItemFolder = new FolderCommon ();
        this.parentItemFactory.getFolder ().add ( "allItems", flatItemFolder, new HashMap<String, Variant> () );
    }

    public void shutdown ()
    {
        handleDisconnected ();

        FolderCommon folder = this.parentItemFactory.getFolder ();
        if ( folder != null )
        {
            folder.remove ( this.flatItemFolder );
        }
    }

    /**
     * Unregister everything from the hive
     */
    protected void unregisterAllItems ()
    {
        for ( Map.Entry<String, OPCItem> entry : this.itemMap.entrySet () )
        {
            this.hive.unregisterItem ( entry.getValue () );
            this.flatItemFolder.remove ( entry.getKey () );
        }

        this.itemMap.clear ();
        this.clientHandleMap.clear ();
        this.clientHandleMapRev.clear ();
        this.serverHandleMap.clear ();
        this.serverHandleMapRev.clear ();
    }

    public void requestItemsById ( Collection<String> initialItems )
    {
        List<ItemRequest> reqs = new ArrayList<ItemRequest> ( initialItems.size () );
        for ( String itemId : initialItems )
        {
            ItemRequest req = new ItemRequest ();
            OPCITEMDEF def = new OPCITEMDEF ();
            def.setItemID ( itemId );
            def.setActive ( false );
            req.setItemDefinition ( def );

            reqs.add ( req );
        }
        requestItems ( reqs );
    }

    public void requestItems ( Collection<ItemRequest> items )
    {
        synchronized ( requestMap )
        {
            for ( ItemRequest itemDef : items )
            {
                String itemId = itemDef.getItemDefinition ().getItemID ();
                if ( itemMap.containsKey ( itemId ) || requestMap.containsKey ( itemId )
                        || requestedMap.containsKey ( itemId ) )
                {
                    continue;
                }
                requestMap.put ( itemDef.getItemDefinition ().getItemID (), itemDef );
            }
        }
    }

    public void requestItem ( ItemRequest itemDef )
    {
        requestItems ( Arrays.asList ( itemDef ) );
    }

    public void requestItemById ( String itemId )
    {
        requestItemsById ( Arrays.asList ( itemId ) );
    }

    /**
     * May only be called by the controller
     */
    public void handleConnected () throws InvocationTargetException
    {
        registerAllItems ();
    }

    /**
     * May only be called by the controller
     */
    public void handleDisconnected ()
    {
        unregisterAllItems ();
    }

    /**
     * Process pending requests
     * <p>
     * May only be called by the controller
     * @throws InvocationTargetException
     */
    public void processRequests () throws InvocationTargetException
    {
        if ( requestMap.isEmpty () )
        {
            return;
        }

        List<ItemRequest> newItems = null;

        synchronized ( requestMap )
        {
            newItems = new ArrayList<ItemRequest> ( requestMap.size () );
            for ( Map.Entry<String, ItemRequest> def : requestMap.entrySet () )
            {
                newItems.add ( def.getValue () );
                requestedMap.put ( def.getKey (), def.getValue () );
            }
            requestMap.clear ();
        }

        realizeItems ( newItems );

    }

    /**
     * register all requested items with the OPC server
     * @throws InvocationTargetException
     */
    private void registerAllItems () throws InvocationTargetException
    {
        realizeItems ( this.requestedMap.values () );
        setActive ( true, activeSet );
    }

    private void realizeItems ( Collection<ItemRequest> newItems ) throws InvocationTargetException
    {
        if ( newItems.isEmpty () )
        {
            return;
        }

        Random r = new Random ();
        synchronized ( this.clientHandleMap )
        {
            for ( ItemRequest def : newItems )
            {
                Integer i = r.nextInt ();
                while ( this.clientHandleMapRev.containsKey ( i ) )
                {
                    i = r.nextInt ();
                }
                this.clientHandleMap.put ( def.getItemDefinition ().getItemID (), i );
                this.clientHandleMapRev.put ( i, def.getItemDefinition ().getItemID () );
                def.getItemDefinition ().setClientHandle ( i );
            }
        }

        // for now do it one by one .. since packages that get too big cause an error
        for ( ItemRequest def : newItems )
        {
            //RealizeItemsJob job = new RealizeItemsJob ( this.model.getItemMgt (), newItems.toArray ( new OPCITEMDEF[0] ) );
            RealizeItemsJob job = new RealizeItemsJob ( this.model.getItemMgt (),
                    new OPCITEMDEF[] { def.getItemDefinition () } );
            KeyedResultSet<OPCITEMDEF, OPCITEMRESULT> result = worker.execute ( job, job );

            for ( KeyedResult<OPCITEMDEF, OPCITEMRESULT> entry : result )
            {
                String itemId = entry.getKey ().getItemID ();

                if ( entry.isFailed () )
                {
                    logger.debug ( String.format ( "Revoking client handle %d for item %s",
                            entry.getKey ().getClientHandle (), itemId ) );
                    clientHandleMap.remove ( itemId );
                    clientHandleMapRev.remove ( entry.getKey ().getClientHandle () );
                }
                else
                {
                    int serverHandle = entry.getValue ().getServerHandle ();
                    serverHandleMap.put ( itemId, serverHandle );
                    serverHandleMapRev.put ( serverHandle, itemId );
                }
                createItem ( def, entry );
            }
        }
    }

    /**
     * Create a new item based on the result information from the group add call
     * @param entry the result from the group add operation
     * @return the new item
     */
    private OPCItem createItem ( ItemRequest req, KeyedResult<OPCITEMDEF, OPCITEMRESULT> entry )
    {
        OPCITEMDEF def = entry.getKey ();
        OPCITEMRESULT result = entry.getValue ();

        DataItemInformation di = new DataItemInformationBase ( createItemId ( def ),
                Helper.convertToAccessSet ( result.getAccessRights () ) );
        OPCItem item = new OPCItem ( this.hive, this, di, entry );

        applyTemplate ( item );

        this.itemMap.put ( def.getItemID (), item );
        this.hive.registerItem ( item );

        Map<String, Variant> browserMap = Helper.convertToAttributes ( entry );
        if ( req.getAttributes () != null  )
        {
            browserMap.putAll ( req.getAttributes () );
        }

        this.flatItemFolder.add ( def.getItemID (), item, browserMap );

        return item;
    }

    /**
     * Apply the item template as configured in the hive
     * @param item the item to which a template should by applied
     */
    private void applyTemplate ( OPCItem item )
    {
        String itemId = item.getInformation ().getName ();
        FactoryTemplate ft = this.hive.findFactoryTemplate ( itemId );
        logger.debug ( String.format ( "Find template for item '%s' : %s", itemId, ft ) );
        if ( ft != null )
        {
            try
            {
                item.setChain ( FactoryHelper.instantiateChainList ( this.hive, ft.getChainEntries () ) );
            }
            catch ( ConfigurationError e )
            {
                logger.warn ( "Failed to apply item template", e );
            }
            item.setAttributes ( ft.getItemAttributes () );
        }
    }

    /**
     * create an item id from the opc item definition for this item manager
     * @param itemDef the opc item definition
     * @return the item id
     */
    private String createItemId ( OPCITEMDEF itemDef )
    {
        return getItemPrefix () + "." + itemDef.getItemID ();
    }

    private String getItemPrefix ()
    {
        if ( this.itemIdPrefix == null || this.itemIdPrefix.length () == 0 )
        {
            return this.configuration.getDeviceTag ();
        }
        else
        {
            return this.configuration.getDeviceTag () + "." + this.itemIdPrefix;
        }
    }

    public void wakeupItem ( String item )
    {
        synchronized ( activationRequestMap )
        {
            activationRequestMap.put ( item, Boolean.TRUE );
            activeSet.add ( item );
        }
    }

    public void suspendItem ( String item )
    {
        synchronized ( activationRequestMap )
        {
            activationRequestMap.put ( item, Boolean.FALSE );
            activeSet.remove ( item );
        }
    }

    /**
     * May only be called by the controller
     * @throws InvocationTargetException 
     * @throws JIException 
     */
    public void processActivations () throws InvocationTargetException
    {
        if ( activationRequestMap.isEmpty () )
        {
            return;
        }

        Map<String, Boolean> processMap;

        synchronized ( activationRequestMap )
        {
            processMap = new HashMap<String, Boolean> ( activationRequestMap );
            activationRequestMap.clear ();
        }

        performActivations ( processMap );
    }

    /**
     * Handle the pending activations
     * @param processMap the activations to process
     * @throws InvocationTargetException
     */
    private void performActivations ( Map<String, Boolean> processMap ) throws InvocationTargetException
    {
        Set<String> setActive = new HashSet<String> ();
        Set<String> setInactive = new HashSet<String> ();

        for ( Map.Entry<String, Boolean> entry : processMap.entrySet () )
        {
            if ( entry.getValue () )
            {
                setActive.add ( entry.getKey () );
            }
            else
            {
                setInactive.add ( entry.getKey () );
            }
        }

        setActive ( true, setActive );
        setActive ( false, setInactive );
    }

    /**
     * execute setting the active state.
     * <p>
     * This method might block until either the timeout occurrs or the
     * operation is completed
     * @param state the state to set
     * @param list the list to set
     * @throws InvocationTargetException
     */
    private void setActive ( boolean state, Collection<String> list ) throws InvocationTargetException
    {
        if ( list.isEmpty () )
        {
            return;
        }

        // now look up client IDs
        List<Integer> handles = new ArrayList<Integer> ( list.size () );
        for ( String itemId : list )
        {
            Integer handle = this.serverHandleMap.get ( itemId );
            if ( handle != null )
            {
                handles.add ( handle );
            }
        }

        if ( handles.isEmpty () )
        {
            return;
        }

        ItemActivationJob job = new ItemActivationJob ( this.model, state, handles.toArray ( new Integer[0] ) );
        this.worker.execute ( job, job );
    }

    /**
     * Perform the read operation on the already registered items
     * @param dataSource the datasource to read from (cache or device)
     * @throws InvocationTargetException
     */
    public void read ( OPCDATASOURCE dataSource ) throws InvocationTargetException
    {
        Set<String> readSet;
        synchronized ( activationRequestMap )
        {
            readSet = new HashSet<String> ( this.activeSet );
        }

        if ( readSet.isEmpty () )
        {
            // we are lucky
            return;
        }

        Set<Integer> handles = new HashSet<Integer> ();
        for ( String itemId : readSet )
        {
            Integer handle = serverHandleMap.get ( itemId );
            if ( handle != null )
            {
                handles.add ( handle );
            }
        }

        if ( handles.isEmpty () )
        {
            // we are lucky ... better late than never
            return;
        }

        SyncReadJob job = new SyncReadJob ( this.model, dataSource, handles.toArray ( new Integer[0] ) );
        KeyedResultSet<Integer, OPCITEMSTATE> result = this.worker.execute ( job, job );

        // we have the read result, so now we distribute the result to the items
        handleReadResult ( result );
    }

    /**
     * Provide the registers items with the read result
     * @param result the read result
     * @throws InvocationTargetException
     */
    private void handleReadResult ( KeyedResultSet<Integer, OPCITEMSTATE> result ) throws InvocationTargetException
    {
        for ( KeyedResult<Integer, OPCITEMSTATE> entry : result )
        {
            String itemId = this.serverHandleMapRev.get ( entry.getKey () );
            if ( itemId == null )
            {
                logger.info ( String.format ( "Got read reply for invalid item - client handle: '%s'", entry.getKey () ) );
                continue;
            }

            OPCItem item = this.itemMap.get ( itemId );
            if ( item == null )
            {
                logger.info ( String.format ( "Get read reply for invalid item id: %s", itemId ) );
                continue;
            }

            String errorMessage = null;
            if ( entry.isFailed () )
            {
                errorMessage = getErrorMessage ( entry.getErrorCode () );
            }

            item.updateStatus ( entry, errorMessage );
        }
    }

    private String getErrorMessage ( int errorCode ) throws InvocationTargetException
    {
        ErrorMessageJob job = new ErrorMessageJob ( this.model, errorCode );
        return worker.execute ( job, job );
    }

    public void addWriteRequest ( String itemId, Variant value )
    {
        if ( !this.model.isConnected () )
        {
            // discard write request
            logger.warn ( String.format ( "OPC is not connected", value ) );
            return;
        }

        JIVariant variant = Helper.ours2theirs ( value );
        if ( variant == null )
        {
            logger.warn ( String.format ( "Failed to convert %s to variant", value ) );
            return;
        }

        Integer serverHandle = this.serverHandleMap.get ( itemId );
        if ( serverHandle == null )
        {
            logger.warn ( String.format ( "Server handle not found for item %s", itemId ) );
            return;
        }

        addWriteRequest ( new WriteRequest ( serverHandle, variant ) );
    }

    protected void addWriteRequest ( WriteRequest request )
    {
        synchronized ( writeRequests )
        {
            writeRequests.add ( request );
        }
    }

    /**
     * Perform all queued write requests
     * <p>
     * May only be called by the controller
     * @throws InvocationTargetException
     */
    public void processWriteRequests () throws InvocationTargetException
    {
        if ( writeRequests.isEmpty () )
        {
            return;
        }

        List<WriteRequest> requests = new ArrayList<WriteRequest> ();

        synchronized ( writeRequests )
        {
            requests.addAll ( writeRequests );
            writeRequests.clear ();
        }

        // if one write call fails here all other won't be written
        // this is ok, since all others will fail as well
        // specific item write errors are handled specifically using the result value
        for ( WriteRequest request : requests )
        {
            SyncWriteJob job = new SyncWriteJob ( this.model, new WriteRequest[] { request } );

            try
            {
                ResultSet<WriteRequest> result = worker.execute ( job, job );
                handleWriteResult ( result.get ( 0 ) );
            }
            catch ( InvocationTargetException e )
            {
                handleWriteException ( e, request );
                throw e;
            }
        }
    }

    /**
     * handle the case a critical write error occurred
     * @param e the exception
     * @param request the request that caused the error
     */
    private void handleWriteException ( InvocationTargetException e, WriteRequest request )
    {
        String itemId = this.serverHandleMapRev.get ( request.getServerHandle () );
        logger.warn ( String.format ( "Failed to perform write request for item %s (%s) => %s", itemId,
                request.getServerHandle (), request.getValue () ), e );

        if ( itemId == null )
        {
            return;
        }

        OPCItem item = this.itemMap.get ( itemId );
        if ( item == null )
        {
            return;
        }

        item.setLastWriteError ( null );
    }

    private void handleWriteResult ( Result<WriteRequest> result )
    {
        String itemId = this.serverHandleMapRev.get ( result.getValue ().getServerHandle () );
        if ( itemId == null )
        {
            return;
        }

        OPCItem item = this.itemMap.get ( itemId );
        if ( item == null )
        {
            return;
        }

        item.setLastWriteError ( result );
    }

    public void availableItemsChanged ( Set<ItemDescription> availableItems )
    {
        List<ItemRequest> requests = new ArrayList<ItemRequest> ( availableItems.size () );

        for ( ItemDescription item : availableItems )
        {
            ItemRequest req = new ItemRequest ();

            OPCITEMDEF def = new OPCITEMDEF ();
            def.setItemID ( item.getId () );
            req.setItemDefinition ( def );

            Map<String, Variant> attributes = new HashMap<String, Variant> ();
            attributes.put ( "description", new Variant ( item.getDescription () ) );
            req.setAttributes ( attributes );

            requests.add ( req );
        }

        requestItems ( requests );
    }
}
