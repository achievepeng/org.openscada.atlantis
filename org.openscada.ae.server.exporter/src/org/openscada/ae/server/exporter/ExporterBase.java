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

package org.openscada.ae.server.exporter;

import org.openscada.ae.server.Service;
import org.openscada.core.ConnectionInformation;
import org.openscada.utils.lifecycle.LifecycleAware;

public abstract class ExporterBase implements LifecycleAware
{
    protected Service service = null;

    protected ConnectionInformation connectionInformation;

    public ExporterBase ( final Service service, final ConnectionInformation connectionInformation ) throws Exception
    {
        this.service = service;
        this.connectionInformation = connectionInformation;
    }

    public Class<?> getServiceClass ()
    {
        return this.service.getClass ();
    }

    public ConnectionInformation getConnectionInformation ()
    {
        return this.connectionInformation;
    }
}
