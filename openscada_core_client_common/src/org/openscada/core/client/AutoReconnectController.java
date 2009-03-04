package org.openscada.core.client;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.openscada.core.ConnectionInformation;

/**
 * A automatic reconnect controller which keeps connection in the requested state
 * <p>
 * In order to use the reconnect controller put the connection in the constructor and call {@link #connect()}.
 * 
 * <pre><code>
 * Connection connection = ...;
 * AutoReconnectController controller = new AutoReconnectController ( connection );
 * controller.connect ();
 * </code></pre> 
 * <p>
 * Note that if you do not hold an instance to the auto reconnect controller it will be garbage collected
 * and the connection state will no longer be monitored.
 * @since 0.12
 * @author Jens Reimann
 *
 */
public class AutoReconnectController implements ConnectionStateListener
{
    private final static class ThreadFactoryImplementation implements ThreadFactory
    {
        private final ConnectionInformation connectionInformation;

        private ThreadFactoryImplementation ( final ConnectionInformation connectionInformation )
        {
            this.connectionInformation = connectionInformation;
        }

        public Thread newThread ( final Runnable r )
        {
            final Thread t = new Thread ( r );
            t.setDaemon ( true );
            t.setName ( "AutoReconnect/" + this.connectionInformation );
            return t;
        }
    }

    private static Logger logger = Logger.getLogger ( AutoReconnectController.class );

    private static final long DEFAULT_RECONNECT_DELAY = 10 * 1000;

    private final Connection connection;

    private boolean connect;

    private final long reconnectDelay;

    private final ScheduledThreadPoolExecutor executor;

    private long lastTimestamp;

    private ConnectionState state;

    private boolean checkScheduled;

    public AutoReconnectController ( final Connection connection, long reconnectDelay )
    {
        this.connection = connection;
        this.reconnectDelay = reconnectDelay;

        if ( this.connection == null )
        {
            throw new NullPointerException ( "'connection' must not be null" );
        }

        if ( reconnectDelay <= 0 )
        {
            reconnectDelay = DEFAULT_RECONNECT_DELAY;
        }

        this.connection.addConnectionStateListener ( this );

        final ThreadFactory threadFactory = new ThreadFactoryImplementation ( connection.getConnectionInformation () );

        this.executor = new ScheduledThreadPoolExecutor ( 1, threadFactory );
    }

    @Override
    protected void finalize () throws Throwable
    {
        logger.debug ( "Finalized" );
        if ( this.executor != null )
        {
            this.executor.shutdownNow ();
        }
        super.finalize ();
    }

    public synchronized void connect ()
    {
        if ( this.connect == true )
        {
            return;
        }
        this.connect = true;

        // we want that now!
        this.lastTimestamp = 0;

        triggerUpdate ( this.connection.getState () );
    }

    public synchronized void disconnect ()
    {
        if ( this.connect == false )
        {
            return;
        }
        this.connect = false;

        // we want that now!
        this.lastTimestamp = 0;

        triggerUpdate ( this.connection.getState () );
    }

    public void stateChange ( final Connection connection, final ConnectionState state, final Throwable error )
    {
        logger.info ( String.format ( "State change: %s", state ), error );
        triggerUpdate ( state );
    }

    private synchronized void triggerUpdate ( final ConnectionState state )
    {
        this.state = state;

        if ( !this.checkScheduled )
        {
            this.checkScheduled = true;
            this.executor.execute ( new Runnable () {

                public void run ()
                {
                    performUpdate ( state );
                }
            } );
        }
    }

    protected synchronized void performUpdate ( final ConnectionState state )
    {
        logger.debug ( "Performing update: " + state );

        final long now = System.currentTimeMillis ();
        final long diff = now - this.lastTimestamp;

        logger.debug ( String.format ( "Last action: %s, diff: %s, delay: %s", this.lastTimestamp, diff, this.reconnectDelay ) );

        if ( diff > this.reconnectDelay )
        {
            performCheckNow ();
        }
        else
        {
            final long delay = this.reconnectDelay - diff;
            logger.info ( String.format ( "Delaying next check by %s milliseconds", delay ) );
            this.executor.schedule ( new Runnable () {

                public void run ()
                {
                    performCheckNow ();
                }
            }, delay, TimeUnit.MILLISECONDS );
        }

        this.lastTimestamp = System.currentTimeMillis ();
    }

    private synchronized void performCheckNow ()
    {
        this.checkScheduled = false;
        logger.debug ( String.format ( "Performing state check: %s (request: %s)", this.state, this.connect ) );

        switch ( this.state )
        {
        case CLOSED:
            if ( this.connect )
            {
                logger.info ( "Trigger connect" );
                this.connection.connect ();
            }
            break;
        case LOOKUP:
        case CONNECTING:
        case CONNECTED:
        case BOUND:
            if ( !this.connect )
            {
                logger.info ( "Trigger disconnect" );
                this.connection.disconnect ();
            }
            break;
        default:
            logger.info ( "Do nothing" );
            break;
        }
    }

}
