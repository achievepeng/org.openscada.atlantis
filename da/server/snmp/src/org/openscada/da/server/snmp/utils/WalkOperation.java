package org.openscada.da.server.snmp.utils;

import java.io.IOException;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.openscada.da.server.snmp.SNMPNode;
import org.snmp4j.PDU;
import org.snmp4j.Target;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.VariableBinding;

public abstract class WalkOperation
{
    private static Logger _log = Logger.getLogger ( WalkOperation.class );
    
    private SNMPNode _node = null;
    private OID _oid = null;
    private boolean _useBulkGet = false;
    
    public WalkOperation ( SNMPNode node, OID oid, boolean useBulkGet )
    {
        _node = node;
        _oid = oid;
        _useBulkGet = useBulkGet;
    }
    
    protected abstract void handleOID ( OID oid );
    
    @SuppressWarnings("unchecked")
    private OID processResponse ( ResponseEvent responseEvent )
    {
        if ( responseEvent == null )
            return null;
        
        if ( responseEvent.getError () != null )
        {
            _log.warn ( "Listing failed", responseEvent.getError () );
            return null;
        }
        
        if ( responseEvent.getResponse () == null )
        {
            return null;
        }
        
        PDU response = responseEvent.getResponse ();
        if ( response.getErrorStatus () != 0 )
        {
            _log.warn ( String.format ( "Error in reply: %1$d", response.getErrorStatus () ) );
            return null;
        }
        
        if ( response.getType () == PDU.REPORT )
        {
            return null;
        }
        
        Vector<VariableBinding> vbs = response.getVariableBindings ();
        
        OID nextOID = null;
        for ( VariableBinding vb : vbs )
        {
            if ( vb.isException () )
            {
                _log.info ( "Binding Exception: " + vb.toString () );
                return null;
            }
            if ( vb.getVariable ().isException () )
            {
                _log.info ( "Variable Exception: " + vb.getVariable ().toString () );
                return null;
            }
            
            OID oid = vb.getOid ();
            if ( !oid.startsWith ( _oid ) )
            {
                _log.info ( "OID has not same root" );
                return null;
            }
            
            handleOID ( oid );
            
            nextOID = oid;
        }
        
        return nextOID;
    }
    
    public void run ()
    {
        _log.debug ( "Starting walk operation for subtree: " + _oid );
        
        Target target = _node.getConnection ().createTarget ();
        PDU request = _node.getConnection ().createPDU ( target, _useBulkGet ? PDU.GETBULK : PDU.GETNEXT );
        request.setNonRepeaters ( 0 );
        request.setMaxRepetitions ( 10 );
        
        OID currentOID = _oid;
        boolean endOfList = false;
        
        // add dummy entry .. will be set later
        request.add ( new VariableBinding ( new OID ( _oid ) ) );
        
        while ( !endOfList )
        {
            request.set ( 0, new VariableBinding ( currentOID ) );
            request.setRequestID ( new Integer32 ( 0 ) );
            ResponseEvent responseEvent;
            try
            {
                //_log.info ( "Requesting: " + request.get ( 0 ).getOid () );
                responseEvent = _node.getConnection ().send ( target, request );
                currentOID = processResponse ( responseEvent );
                endOfList = ( currentOID == null );
            }
            catch ( IOException e )
            {
                endOfList = true;
            }
            
        }
    }
}
