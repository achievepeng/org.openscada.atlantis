package org.openscada.net.codec;

import java.nio.ByteBuffer;

import org.openscada.net.base.data.Message;

public interface Protocol
{

    public abstract ByteBuffer code ( Message message );

    public abstract void decode ( ByteBuffer buffer );

}