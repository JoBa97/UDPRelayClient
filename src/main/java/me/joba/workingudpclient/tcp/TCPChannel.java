/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.joba.workingudpclient.tcp;

import java.io.IOException;
import java.net.InetAddress;
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

    private final ChannelHandler channelHandler;
    private Socket socket;
    private byte channelId;
    private final int port;
    private final InetAddress targetAddress;
    private boolean shutdown = false;
    private final ServerSocket server;
    
    public TCPChannel(byte channelId, ChannelHandler channelHandler, InetAddress targetAddress, int targetPort) {
        this.channelId = channelId;
        this.port = targetPort;
        this.channelHandler = channelHandler;
        this.targetAddress = targetAddress;
        server = null;
    }
    
    public TCPChannel(byte channelId, ChannelHandler channelHandler, int listeningPort) throws IOException {
        this.channelId = channelId;
        this.channelHandler = channelHandler;
        this.port = listeningPort;
        this.targetAddress = null;
        server = new ServerSocket(listeningPort);
    }

    @Override
    public void setChannel(byte channelId) {
        this.channelId = channelId;
    }

    @Override
    public void send(byte[] data) throws IOException {
        if (socket.isClosed()) {
            return;
        }
        try {
            socket.getOutputStream().write(data);
                            System.out.println("Sent to MC");
        } catch (IOException ex) {
            closeSocket();
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
            byte[] buffer = new byte[60000];
            while (!shutdown) {
                createSocket();
                try {
                    while (!socket.isClosed()) {
                        int read = socket.getInputStream().read(buffer);
                        if (read == -1) {
                            closeSocket();
                        } else {
                            System.out.println("Rec from MC");
                            byte[] data = new byte[read];
                            System.arraycopy(buffer, 0, data, 0, read);
                            channelHandler.send(channelId, data);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
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
}
