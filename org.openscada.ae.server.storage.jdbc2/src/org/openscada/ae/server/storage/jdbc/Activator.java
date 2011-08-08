package org.openscada.ae.server.storage.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Dictionary;
import java.util.Hashtable;

import org.openscada.ae.server.storage.Storage;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator
{
    private static BundleContext context;

    private JdbcStorage jdbcStorage;

    private Connection connection;

    private ServiceRegistration jdbcStorageHandle;

    private final int maxLength = 4000;

    static BundleContext getContext ()
    {
        return context;
    }

    /*
     * (non-Javadoc)
     * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
     */
    @Override
    public void start ( final BundleContext bundleContext ) throws Exception
    {
        Activator.context = bundleContext;
        this.connection = createConnection ();
        this.jdbcStorage = createJdbcStorage ( this.connection );
        this.jdbcStorage.start ();

        final Dictionary<String, Object> properties = new Hashtable<String, Object> ();
        properties.put ( Constants.SERVICE_DESCRIPTION, "JDBC implementation for org.openscada.ae.server.storage.Storage" );
        properties.put ( Constants.SERVICE_VENDOR, "TH4 SYSTEMS GmbH" );
        this.jdbcStorageHandle = context.registerService ( new String[] { JdbcStorage.class.getName (), Storage.class.getName () }, this.jdbcStorage, properties );
    }

    /*
     * (non-Javadoc)
     * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
     */
    @Override
    public void stop ( final BundleContext bundleContext ) throws Exception
    {
        if ( this.jdbcStorageHandle != null )
        {
            this.jdbcStorageHandle.unregister ();
            this.jdbcStorageHandle = null;
        }
        if ( this.jdbcStorage != null )
        {
            this.jdbcStorage.stop ();
            this.jdbcStorage = null;
        }
        if ( this.connection != null )
        {
            this.connection.close ();
            this.connection = null;
        }
        Activator.context = null;
    }

    private Connection createConnection () throws SQLException, ClassNotFoundException
    {
        final String driver = System.getProperty ( "org.openscada.ae.server.storage.jdbc.driver", "" );
        final String url = System.getProperty ( "org.openscada.ae.server.storage.jdbc.url", "" );
        final String user = System.getProperty ( "org.openscada.ae.server.storage.jdbc.username", "" );
        final String password = System.getProperty ( "org.openscada.ae.server.storage.jdbc.password", "" );

        Class.forName ( driver );
        final Connection connection = DriverManager.getConnection ( url, user, password );
        connection.setAutoCommit ( false );
        return connection;
    }

    private JdbcStorage createJdbcStorage ( final Connection connection )
    {
        final JdbcStorage jdbcStorage = new JdbcStorage ();
        StorageDao storageDao;
        if ( "legacy".equals ( System.getProperty ( "org.openscada.ae.server.storage.jdbc.instance", "" ) ) )
        {
            final LegacyJdbcStorageDao jdbcStorageDao = new LegacyJdbcStorageDao ();
            jdbcStorageDao.setMaxLength ( Integer.getInteger ( "org.openscada.ae.server.storage.jdbc.maxlength", this.maxLength ) );
            if ( !System.getProperty ( "org.openscada.ae.server.storage.jdbc.schema", "" ).trim ().isEmpty () )
            {
                jdbcStorageDao.setSchema ( System.getProperty ( "org.openscada.ae.server.storage.jdbc.schema" ) + "." );
            }
            jdbcStorageDao.setConnection ( connection );
            storageDao = jdbcStorageDao;
        }
        else
        {
            final JdbcStorageDao jdbcStorageDao = new JdbcStorageDao ();
            jdbcStorageDao.setInstance ( System.getProperty ( "org.openscada.ae.server.storage.jdbc.instance", "default" ) );
            jdbcStorageDao.setMaxLength ( Integer.getInteger ( "org.openscada.ae.server.storage.jdbc.maxlength", this.maxLength ) );
            if ( !System.getProperty ( "org.openscada.ae.server.storage.jdbc.schema", "" ).trim ().isEmpty () )
            {
                jdbcStorageDao.setSchema ( System.getProperty ( "org.openscada.ae.server.storage.jdbc.schema" ) + "." );
            }
            jdbcStorageDao.setConnection ( connection );
            storageDao = jdbcStorageDao;
        }
        jdbcStorage.setJdbcStorageDao ( storageDao );
        return jdbcStorage;
    }
}
