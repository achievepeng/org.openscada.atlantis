package org.openscada.ae.server.http.monitor;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import org.openscada.ae.Event;
import org.openscada.ae.Event.Fields;
import org.openscada.ae.event.EventProcessor;
import org.openscada.ae.filter.EventMatcher;
import org.openscada.ae.filter.internal.EventMatcherImpl;
import org.openscada.ae.monitor.common.AbstractStateMachineMonitorService;
import org.openscada.ca.ConfigurationDataHelper;
import org.openscada.core.Variant;
import org.openscada.utils.lang.Pair;
import org.osgi.framework.BundleContext;

public class EventMonitorImpl extends AbstractStateMachineMonitorService implements EventMonitor
{
    private EventMatcher matcher = null;

    public EventMonitorImpl ( final BundleContext context, final Executor executor, final EventProcessor eventProcessor, final String id )
    {
        super ( context, executor, eventProcessor, id );
    }

    public void update ( final Map<String, String> properties )
    {
        final ConfigurationDataHelper cfg = new ConfigurationDataHelper ( properties );

        setEventInformationAttributes ( convertAttributes ( cfg ) );
        setActive ( cfg.getBoolean ( "active", true ) );
        setRequireAkn ( cfg.getBoolean ( "requireAck", true ) );
        setEventMatcher ( cfg.getString ( "filter", "" ) );
    }

    private void setEventMatcher ( final String filter )
    {
        this.matcher = new EventMatcherImpl ( filter );
    }

    private static Map<String, Variant> convertAttributes ( final ConfigurationDataHelper cfg )
    {
        final Map<String, Variant> attributes = new HashMap<String, Variant> ();

        for ( final Map.Entry<String, String> entry : cfg.getPrefixed ( "info." ).entrySet () )
        {
            attributes.put ( entry.getKey (), new Variant ( entry.getValue () ) );
        }

        return attributes;
    }

    public synchronized Pair<Boolean, Event> evaluate ( final Event event )
    {
        if ( this.matcher != null )
        {
            if ( this.matcher.matches ( event ) )
            {
                // FIXME: just for now, the real implementation should set AKN directly
                this.setFailure ( new Variant (), event.getSourceTimestamp () );
                this.setOk ( new Variant (), event.getSourceTimestamp () );
                final Event resultEvent = Event.create ().event ( event ).attribute ( Fields.MONITOR_TYPE, getId () ).build ();
                return new Pair<Boolean, Event> ( true, resultEvent );
            }
        }
        return new Pair<Boolean, Event> ( false, event );
    }

    @Override
    public void init ()
    {
        super.init ();
        setOk ( new Variant (), new Date ( System.currentTimeMillis () ) );
    }
}
