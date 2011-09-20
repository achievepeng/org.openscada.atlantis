package org.openscada.ae.server.storage.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import javax.sql.ConnectionPoolDataSource;

import org.openscada.ae.Event;
import org.openscada.core.VariantType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseStorageDao implements StorageDao
{
    private static final Logger logger = LoggerFactory.getLogger ( BaseStorageDao.class );

    private String schema = "";

    private int maxLength = 4000;

    private String instance = "default";

    private ConnectionPoolDataSource dataSource;

    public void setSchema ( final String schema )
    {
        this.schema = schema;
    }

    public String getSchema ()
    {
        return schema;
    }

    public void setMaxLength ( final int maxLength )
    {
        this.maxLength = maxLength;
    }

    public int getMaxLength ()
    {
        return maxLength;
    }

    public void setInstance ( final String instance )
    {
        this.instance = instance;
    }

    public String getInstance ()
    {
        return instance;
    }

    public void setDataSource ( ConnectionPoolDataSource dataSource )
    {
        this.dataSource = dataSource;
    }

    public ConnectionPoolDataSource getDataSource ()
    {
        return dataSource;
    }

    public Connection createConnection () throws SQLException
    {
        Connection connection = this.getDataSource ().getPooledConnection ().getConnection ();
        connection.setAutoCommit ( false );
        return connection;
    }

    public void closeStatement ( Statement statement )
    {
        try
        {
            if ( statement == null || statement.isClosed () )
            {
                return;
            }
            statement.close ();
        }
        catch ( SQLException e )
        {
            logger.debug ( "Exception on closing statement", e );
        }
    }

    public void closeConnection ( Connection connection )
    {
        try
        {
            if ( connection == null || connection.isClosed () )
            {
                return;
            }
            connection.close ();
        }
        catch ( SQLException e )
        {
            logger.debug ( "Exception on closing statement", e );
        }
    }

    @Override
    public void updateComment ( final UUID id, final String comment ) throws Exception
    {
        final Connection con = createConnection ();

        final PreparedStatement stm1 = con.prepareStatement ( String.format ( getDeleteAttributesSql (), this.getSchema () ) );
        stm1.setString ( 1, id.toString () );
        stm1.setString ( 2, Event.Fields.COMMENT.getName () );
        stm1.addBatch ();
        stm1.execute ();

        final PreparedStatement stm2 = con.prepareStatement ( String.format ( getInsertAttributesSql (), this.getSchema () ) );
        stm2.setString ( 1, id.toString () );
        stm2.setString ( 2, Event.Fields.COMMENT.getName () );
        stm2.setString ( 3, VariantType.STRING.name () );
        stm2.setString ( 4, clip ( this.getMaxLength (), comment ) );
        stm2.setLong ( 5, (Long)null );
        stm2.setDouble ( 6, (Double)null );
        stm2.addBatch ();
        stm2.execute ();

        con.commit ();
        closeStatement ( stm1 );
        closeStatement ( stm2 );
        closeConnection ( con );
    }

    protected String clip ( final int i, final String string )
    {
        if ( string == null )
        {
            return null;
        }
        if ( i < 1 || string.length () <= i )
        {
            return string;
        }
        return string.substring ( 0, i );
    }

    protected abstract String getDeleteAttributesSql ();

    protected abstract String getInsertAttributesSql ();
}
