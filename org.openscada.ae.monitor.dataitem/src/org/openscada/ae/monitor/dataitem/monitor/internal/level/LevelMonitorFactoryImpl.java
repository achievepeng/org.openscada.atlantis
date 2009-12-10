package org.openscada.ae.monitor.dataitem.monitor.internal.level;

import org.openscada.ae.event.EventProcessor;
import org.openscada.ae.monitor.dataitem.AbstractMonitorFactory;
import org.openscada.ae.monitor.dataitem.DataItemMonitor;
import org.osgi.framework.BundleContext;

public class LevelMonitorFactoryImpl extends AbstractMonitorFactory
{

    public static final String FACTORY_PREFIX = "org.openscada.da.level";

    private final String type;

    private final boolean lowerOk;

    private final int priority;

    private final boolean cap;

    private final String defaultMonitorType;

    public LevelMonitorFactoryImpl ( final BundleContext context, final EventProcessor eventProcessor, final String type, final String defaultMonitorType, final boolean lowerOk, final int priority, final boolean cap )
    {
        super ( context, eventProcessor );
        this.type = type;
        this.lowerOk = lowerOk;
        this.priority = priority;
        this.cap = cap;
        this.defaultMonitorType = defaultMonitorType;
    }

    @Override
    protected DataItemMonitor createInstance ( final String configurationId, final EventProcessor eventProcessor )
    {
        return new LevelAlarmMonitor ( this.context, eventProcessor, configurationId, FACTORY_PREFIX + "." + this.type, this.defaultMonitorType, this.lowerOk, this.priority, this.cap );
    }
}
