/*
 * This file is part of the OpenSCADA project
 * Copyright (C) 2006-2009 inavare GmbH (http://inavare.com)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.openscada.da.server.proxy;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.xmlbeans.XmlException;
import org.openscada.core.InvalidOperationException;
import org.openscada.core.NotConvertableException;
import org.openscada.core.NullValueException;
import org.openscada.core.Variant;
import org.openscada.da.proxy.configuration.RootDocument;
import org.openscada.da.server.browser.common.FolderCommon;
import org.openscada.da.server.common.impl.HiveCommon;
import org.w3c.dom.Node;

/**
 * @author Juergen Rose &lt;juergen.rose@inavare.net&gt;
 *
 */
public class Hive extends HiveCommon
{
    private final FolderCommon rootFolder;

    private final Map<ProxyPrefixName, ProxyConnection> connections = new HashMap<ProxyPrefixName, ProxyConnection> ();

    private boolean initialized = false;

    private String separator = ".";

    private FolderCommon connectionsFolder;

    /**
     * @throws XmlException
     * @throws IOException
     * @throws ClassNotFoundException
     * @throws NotConvertableException 
     * @throws NullValueException 
     * @throws InvalidOperationException 
     */
    public Hive () throws XmlException, IOException, ClassNotFoundException, InvalidOperationException, NullValueException, NotConvertableException
    {
        this ( new XMLConfigurator ( RootDocument.Factory.parse ( new File ( "configuration.xml" ) ) ) );
    }

    /**
     * @param configurator
     * @throws ClassNotFoundException
     * @throws NotConvertableException 
     * @throws NullValueException 
     * @throws InvalidOperationException 
     */
    public Hive ( final XMLConfigurator configurator ) throws ClassNotFoundException, InvalidOperationException, NullValueException, NotConvertableException
    {
        this.rootFolder = new FolderCommon ();
        setRootFolder ( this.rootFolder );
        initialize ( configurator );
    }

    /**
     * @param node
     * @throws XmlException
     * @throws ClassNotFoundException
     * @throws NotConvertableException 
     * @throws NullValueException 
     * @throws InvalidOperationException 
     */
    public Hive ( final Node node ) throws XmlException, ClassNotFoundException, InvalidOperationException, NullValueException, NotConvertableException
    {
        this ( new XMLConfigurator ( RootDocument.Factory.parse ( node ) ) );
    }

    /**
     * @param group
     */
    public void addGroup ( final ProxyGroup group )
    {
        if ( this.initialized )
        {
            throw new IllegalArgumentException ( "no further connections may be added when initialize() was already called!" );
        }
        if ( this.connections.keySet ().contains ( group.getPrefix () ) )
        {
            throw new IllegalArgumentException ( "prefix must not already exist!" );
        }
        final ProxyConnection connection = new ProxyConnection ( this, this.connectionsFolder, group );
        this.connections.put ( group.getPrefix (), connection );
    }

    /**
     * @param configurator 
     * @throws NotConvertableException 
     * @throws NullValueException 
     * @throws InvalidOperationException 
     * @throws ClassNotFoundException 
     * 
     */
    public void initialize ( final XMLConfigurator configurator ) throws ClassNotFoundException, InvalidOperationException, NullValueException, NotConvertableException
    {
        // create connections folder
        this.connectionsFolder = new FolderCommon ();
        this.rootFolder.add ( "connections", this.connectionsFolder, new HashMap<String, Variant> () );

        if ( configurator != null )
        {
            configurator.configure ( this );

        }

        for ( final ProxyConnection proxyConnection : this.connections.values () )
        {
            proxyConnection.init ();
        }

        addItemFactory ( new ProxyDataItemFactory ( this.connections, this.separator ) );

        this.initialized = true;
    }

    /**
     * @param separator
     */
    public void setSeparator ( final String separator )
    {
        if ( this.initialized )
        {
            throw new IllegalArgumentException ( "separator may not be changed when initialize() was already called!" );
        }
        this.separator = separator;
    }

    /**
     * @return separator which separates prefix from rest of item name
     */
    public String getSeparator ()
    {
        return this.separator;
    }
}
