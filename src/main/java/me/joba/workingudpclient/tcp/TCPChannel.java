/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.joba.workingudpclient.tcp;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.joba.workingudpclient.Channel;
import me.joba.workingudpclient.ChannelHandler;

/**
 *
 * @author Jonas
 */
public class TCPChannel extends Channel {

    private Socket socket;
    private final ChannelHandler handler;
    private byte channelId;
    private final int port;
    private final InetAddress targetAddress;
    private boolean shutdown = false;
    private final ServerSocket server;
    private final BidirectionalUDPStream remoteOut;
    
    public TCPChannel(byte channelId, ChannelHandler handler, InetAddress targetAddress, int targetPort) throws IOException {
        this.channelId = channelId;
        this.port = targetPort;
        this.targetAddress = targetAddress;
        this.handler = handler;
        server = null;    
        remoteOut = new BidirectionalUDPStream(channelId, handler, this::sendOut);
    }
    
    public TCPChannel(byte channelId, ChannelHandler handler, int listeningPort) throws IOException {
        this.channelId = channelId;
        this.port = listeningPort;
        this.targetAddress = null;
        this.handler = handler;    
        remoteOut = new BidirectionalUDPStream(channelId, handler, this::sendOut);
        server = new ServerSocket(listeningPort);
    }

    @Override
    public void setChannel(byte channelId) {
        this.channelId = channelId;
    }

    @Override
    public void send(byte[] data) throws Exception {
        remoteOut.receive(data);
    }
    
    private void sendOut(byte[] data) {
        if (socket.isClosed()) {
            return;
        }
        try {
            socket.getOutputStream().write(data);
            
        } catch (IOException ex) {
            try {
                closeSocket();
            } catch (IOException ex1) {
                Logger.getLogger(TCPChannel.class.getName()).log(Level.SEVERE, null, ex1);
            }
        }
    }

    private void createSocket() throws IOException{
        if(server == null) {
            socket = new Socket(targetAddress, port);
        }
        else {
            socket = server.accept();
        }
    }
    
    @Override
    public void run() {
        try {
            byte[] buffer = new byte[65536];
            while (!shutdown) {
                createSocket();
                try {
                    while (!socket.isClosed()) {
                        int read = socket.getInputStream().read(buffer);
                        if (read == -1) {
                            closeSocket();
                        } else {
                            
                            byte[] data = new byte[read];
                            System.arraycopy(buffer, 0, data, 0, read);
                            remoteOut.write(data);
                        }
                    }
                } catch (Exception e) {
                    closeSocket();
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(TCPChannel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void closeSocket() throws IOException {
        if (!socket.isClosed()) {
            socket.close();
            //channelHandler.send(ChannelHandler.CHANNEL_KILL_SOCKET, new byte[]{channelId});
        }
    }

    @Override
    public void close() {
        try {
            shutdown = true;
            if(server != null) {
                server.close();
            }
            closeSocket();
        } catch (IOException ex) {
            Logger.getLogger(TCPChannel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public String toString() {
        return socket == null ? null : socket.getRemoteSocketAddress().toString();
    }

    @Override
    public void killSocket() {
        try {
            if(socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ex) {
            Logger.getLogger(TCPChannel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public byte getChannelId() {
        return channelId;
    }
}
