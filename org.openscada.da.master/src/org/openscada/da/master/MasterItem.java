/*
 * This file is part of the OpenSCADA project
 * Copyright (C) 2006-2010 inavare GmbH (http://inavare.com)
 *
 * OpenSCADA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 3
 * only, as published by the Free Software Foundation.
 *
 * OpenSCADA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License version 3 for more details
 * (a copy is included in the LICENSE file that accompanied this code).
 *
 * You should have received a copy of the GNU Lesser General Public License
 * version 3 along with OpenSCADA. If not, see
 * <http://opensource.org/licenses/lgpl-3.0.html> for a copy of the LGPLv3 License.
 */

package org.openscada.da.master;

import org.openscada.da.datasource.DataSource;

public interface MasterItem extends DataSource
{

    public static final String MASTER_ID = "master.id";

    /**
     * remove sub condition
     * @param type the type of the handler that should be removed
     * @return 
     */
    public abstract void removeHandler ( final MasterItemHandler handler );

    /**
     * Add a new sub condition
     * @param handler new condition to add
     * @param the priority this entry has in the master item
     */
    public abstract void addHandler ( final MasterItemHandler handler, int priority );

    public abstract void reprocess ();
}