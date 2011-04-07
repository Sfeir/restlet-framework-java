/**
 * Copyright 2005-2011 Noelios Technologies.
 * 
 * The contents of this file are subject to the terms of one of the following
 * open source licenses: LGPL 3.0 or LGPL 2.1 or CDDL 1.0 or EPL 1.0 (the
 * "Licenses"). You can select the license that you prefer but you may not use
 * this file except in compliance with one of these Licenses.
 * 
 * You can obtain a copy of the LGPL 3.0 license at
 * http://www.opensource.org/licenses/lgpl-3.0.html
 * 
 * You can obtain a copy of the LGPL 2.1 license at
 * http://www.opensource.org/licenses/lgpl-2.1.php
 * 
 * You can obtain a copy of the CDDL 1.0 license at
 * http://www.opensource.org/licenses/cddl1.php
 * 
 * You can obtain a copy of the EPL 1.0 license at
 * http://www.opensource.org/licenses/eclipse-1.0.php
 * 
 * See the Licenses for the specific language governing permissions and
 * limitations under the Licenses.
 * 
 * Alternatively, you can obtain a royalty free commercial license with less
 * limitations, transferable or non-transferable, directly at
 * http://www.noelios.com/products/restlet-engine
 * 
 * Restlet is a registered trademark of Noelios Technologies.
 */

package org.restlet.engine.connector;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.restlet.Context;
import org.restlet.Message;
import org.restlet.Response;
import org.restlet.data.Parameter;
import org.restlet.data.Status;
import org.restlet.engine.io.BufferState;
import org.restlet.engine.io.CompletionListener;
import org.restlet.engine.io.Buffer;
import org.restlet.engine.io.BufferProcessor;
import org.restlet.engine.io.IoState;
import org.restlet.util.SelectionListener;
import org.restlet.util.SelectionRegistration;
import org.restlet.util.Series;

/**
 * A network connection way though which messages are exchanged. Messages can be
 * either sent or received, requests or responses.
 * 
 * @author Jerome Louvel
 */
public abstract class Way implements SelectionListener, CompletionListener,
        BufferProcessor {

    /** The IO buffer. */
    private final Buffer buffer;

    /** The parent connection. */
    private final Connection<?> connection;

    /** The message headers. */
    private volatile Series<Parameter> headers;

    /** The IO state. */
    private volatile IoState ioState;

    /** The line builder. */
    private final StringBuilder lineBuilder;

    /** The line builder state. */
    private volatile BufferState lineBuilderState;

    /** The current message exchanged. */
    private volatile Response message;

    /** The message state. */
    private volatile MessageState messageState;

    /** The NIO selection registration. */
    private volatile SelectionRegistration registration;

    /**
     * Constructor.
     * 
     * @param connection
     *            The parent connection.
     * @param bufferSize
     *            The byte buffer size.
     */
    public Way(Connection<?> connection, int bufferSize) {
        this.connection = connection;
        this.buffer = new Buffer(bufferSize, getHelper().isDirectBuffers());
        this.headers = null;
        this.ioState = IoState.IDLE;
        this.lineBuilder = new StringBuilder();
        this.lineBuilderState = BufferState.IDLE;
        this.message = null;
        this.messageState = MessageState.IDLE;
        this.registration = new SelectionRegistration(0, this);
    }

    /**
     * Indicates if the processing loop can continue.
     * 
     * @param buffer
     *            The IO buffer to drain.
     * @param args
     *            The optional arguments to pass back to the callbacks.
     * @return True if the processing loop can continue.
     */
    public boolean canLoop(Buffer buffer, Object... args) {
        return (getConnection().getState() != ConnectionState.CLOSED)
                && ((getIoState() == IoState.PROCESSING) || (getIoState() == IoState.READY));
    }

    /**
     * Recycles the way so it can be reused. Typically invoked by a connection
     * pool.
     */
    public void clear() {
        this.buffer.clear();
        this.headers = null;
        this.ioState = IoState.IDLE;
        clearLineBuilder();
        this.message = null;
        this.messageState = MessageState.IDLE;
        this.registration.clear();
    }

    /**
     * Clears the line builder and adjust its state.
     */
    protected void clearLineBuilder() {
        getLineBuilder().delete(0, getLineBuilder().length());
        setLineBuilderState(BufferState.IDLE);
    }

    /**
     * Indicates if the buffer could be drained again.
     * 
     * @param buffer
     *            The IO buffer to drain.
     * @param args
     *            The optional arguments to pass back to the callbacks.
     * @return True if the buffer could be drained again.
     */
    public boolean couldDrain(Buffer buffer, Object... args) {
        return false;
    }

    /**
     * Indicates if the buffer could be filled again.
     * 
     * @param buffer
     *            The IO buffer to fill.
     * @param args
     *            The optional arguments to pass back to the callbacks.
     * @return True if the buffer could be filled again.
     */
    public boolean couldFill(Buffer buffer, Object... args) {
        return getConnection().getState() != ConnectionState.CLOSED;
    }

    /**
     * Returns the actual message, request or response.
     * 
     * @return The actual message, request or response.
     */
    protected abstract Message getActualMessage();

    /**
     * Returns the IO buffer.
     * 
     * @return The IO buffer.
     */
    public Buffer getBuffer() {
        return buffer;
    }

    /**
     * Returns the parent connection.
     * 
     * @return The parent connection.
     */
    protected Connection<?> getConnection() {
        return connection;
    }

    /**
     * Returns the response headers.
     * 
     * @return The response headers to be written.
     */
    protected Series<Parameter> getHeaders() {
        return headers;
    }

    /**
     * Returns the parent connector helper.
     * 
     * @return The parent connector helper.
     */
    protected ConnectionHelper<?> getHelper() {
        return getConnection().getHelper();
    }

    /**
     * Returns the operations of interest.
     * 
     * @return The operations of interest.
     */
    protected abstract int getInterestOperations();

    /**
     * Returns the IO state.
     * 
     * @return The IO state.
     */
    public IoState getIoState() {
        return ioState;
    }

    /**
     * Returns the line builder.
     * 
     * @return The line builder.
     */
    protected StringBuilder getLineBuilder() {
        return lineBuilder;
    }

    /**
     * Returns the line builder state.
     * 
     * @return The line builder state.
     */
    protected BufferState getLineBuilderState() {
        return lineBuilderState;
    }

    /**
     * Returns a score representing the way load and that could be compared with
     * other ways of the same parent connection.
     * 
     * @return A score representing the way load.
     */
    public int getLoadScore() {
        return (getMessage() == null) ? 0 : 1;
    }

    /**
     * Returns the logger.
     * 
     * @return The logger.
     */
    protected Logger getLogger() {
        return getConnection().getLogger();
    }

    /**
     * Returns the current message processed.
     * 
     * @return The current message processed.
     */
    public Response getMessage() {
        return message;
    }

    /**
     * Returns the message state.
     * 
     * @return The message state.
     */
    protected MessageState getMessageState() {
        return messageState;
    }

    /**
     * Returns the socket's NIO registration holding the link between the
     * channel and the connection.
     * 
     * @return The socket's NIO registration holding the link between the
     *         channel and the connection.
     */
    protected SelectionRegistration getRegistration() {
        return registration;
    }

    /**
     * Indicates if the way is available to handle new messages.
     * 
     * @return True if the way is available to handle new messages.
     */
    public boolean isAvailable() {
        return getMessageState().equals(MessageState.IDLE)
                && getIoState().equals(IoState.IDLE);
    }

    /**
     * Indicates if the way is empty.
     * 
     * @return True if the way is empty.
     */
    public boolean isEmpty() {
        return getBuffer().isEmpty();
    }

    /**
     * Callback method invoked when the parent connection is ready to be closed.
     */
    public void onClosed() {
        setIoState(IoState.IDLE);
        setMessageState(MessageState.IDLE);
        setMessage(null);
        setHeaders(null);
        getBuffer().clear();
    }

    /**
     * Callback method invoked when the current message has been completely
     * received or sent.
     * 
     * @param endDetected
     *            Indicates if the end of the socket channel was detected.
     * @param bufferState
     *            The new buffer state.
     */
    public void onCompleted(boolean endDetected) {
        setIoState(IoState.IDLE);
        setMessageState(MessageState.IDLE);
        setMessage(null);
        setHeaders(null);
    }

    /**
     * Drains the byte buffer by writing available bytes to the socket channel.
     * 
     * @param buffer
     *            The IO buffer to drain.
     * @param maxDrained
     *            The maximum number of bytes drained by this call.
     * @param args
     *            The optional arguments to pass back to the callbacks.
     * @return The number of bytes drained.
     * @throws IOException
     */
    public abstract int onDrain(Buffer buffer, int maxDrained, Object... args)
            throws IOException;

    /**
     * Called on error.
     * 
     * @param status
     *            The error status.
     */
    public abstract void onError(Status status);

    /**
     * Fills the byte buffer by writing the current message.
     * 
     * @param buffer
     *            The IO buffer to drain.
     * @param args
     *            The optional arguments to pass back to the callbacks.
     * @return The number of bytes filled.
     * @throws IOException
     */
    public abstract int onFill(Buffer buffer, Object... args)
            throws IOException;

    /**
     * Callback method invoked when the way has been selected for IO operations
     * it registered interest in.
     */
    public void onSelected() {
        try {
            // Adjust states
            if (getIoState() == IoState.INTEREST) {
                setIoState(IoState.PROCESSING);
            }

            if (getLogger().isLoggable(Level.FINER)) {
                if (this instanceof InboundWay) {
                    getLogger()
                            .log(Level.FINER,
                                    "Inbound way selected. Processing IO for : "
                                            + this);
                } else {
                    getLogger().log(
                            Level.FINER,
                            "Outbound way selected. Processing IO for : "
                                    + this);
                }
            }

            // IO processing
            int drained = processIoBuffer();

            if ((drained == -1)
                    && (getConnection().getState() == ConnectionState.CLOSING)) {
                // No hope to drain more bytes, complete the closing
                getBuffer().clear();
            } else if (getIoState() == IoState.PROCESSING) {
                // Socket channel exhausted
                setIoState(IoState.INTEREST);
            }
        } catch (Exception e) {
            getConnection().onError("Error while processing a connection", e,
                    Status.CONNECTOR_ERROR_COMMUNICATION);
        }
    }

    /**
     * Processes the IO buffer by filling and draining it.
     * 
     * @throws IOException
     */
    protected int processIoBuffer() throws IOException {
        return getBuffer().process(this);
    }

    /**
     * Sets the response headers to be written.
     * 
     * @param headers
     *            The response headers.
     */
    protected void setHeaders(Series<Parameter> headers) {
        this.headers = headers;
    }

    /**
     * Sets the IO state.
     * 
     * @param ioState
     *            The IO state.
     */
    public void setIoState(IoState ioState) {
        if (ioState != this.ioState) {
            if (Context.getCurrentLogger().isLoggable(Level.FINER)) {
                if (this instanceof OutboundWay) {
                    Context.getCurrentLogger().log(Level.FINER,
                            "OutboundWay#setIoState: " + ioState);
                } else {
                    Context.getCurrentLogger().log(Level.FINER,
                            "InboundWay#setIoState: " + ioState);
                }
            }

            this.ioState = ioState;
        }
    }

    /**
     * Sets the line builder state.
     * 
     * @param lineBuilderState
     *            The line builder state.
     */
    protected void setLineBuilderState(BufferState lineBuilderState) {
        this.lineBuilderState = lineBuilderState;
    }

    /**
     * Sets the current message processed.
     * 
     * @param message
     *            The current message processed.
     */
    protected void setMessage(Response message) {
        this.message = message;
    }

    /**
     * Sets the message state.
     * 
     * @param messageState
     *            The message state.
     */
    protected void setMessageState(MessageState messageState) {
        if (this.messageState != messageState) {
            this.messageState = messageState;

            if (getLogger().isLoggable(Level.FINEST)) {
                getLogger().finest("New message state: " + messageState);
            }
        }
    }

    /**
     * Sets the NIO selection registration holding the link between the
     * connection and the way.
     * 
     * @param registration
     *            The NIO selection registration holding the link between the
     *            connection and the way.
     */
    protected void setRegistration(SelectionRegistration registration) {
        this.registration = registration;
    }

    @Override
    public String toString() {
        return getIoState() + " | " + getMessageState() + " | " + getBuffer();
    }

    /**
     * Updates the way IO and message states.
     */
    public void updateState() {
        getRegistration().setInterestOperations(getInterestOperations());
    }

}
