/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.joba.workingudpclient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Jonas
 */
public class TCPChannel extends Channel {

    private final ChannelHandler channelHandler;
    private Socket socket;
    private byte channelId;
    private final int targetPort;
    private final InetAddress targetAddress;
    private boolean shutdown = false;
    private Semaphore socketWaitLock;

    public TCPChannel(byte channelId, InetAddress targetAddress, int targetPort, ChannelHandler channelHandler) {
        this.channelId = channelId;
        this.targetPort = targetPort;
        this.channelHandler = channelHandler;
        this.targetAddress = targetAddress;
        this.socketWaitLock = new Semaphore(1);
    }

    @Override
    public void setChannel(byte channelId) {
        this.channelId = channelId;
    }

    @Override
    public void send(byte[] data) throws IOException {
        if (socket.isClosed()) {
            openSocket();
            socket.getOutputStream().write(data);
            System.out.println("Recreated socket");
        }
        else {
            try { 
                socket.getOutputStream().write(data);
            } catch(SocketException e) {
                if(!socket.isClosed()) {
                    openSocket();
                    send(data);
                }
            }
        }
    }
    
    private void openSocket() throws IOException {
        if(socket != null) {
            socket.close();
        }
        socket = new Socket(targetAddress, targetPort);
        socketWaitLock.release();     
    }

    @Override
    public void run() {
        try {
            byte[] buffer = new byte[60000];
            openSocket();
            while (!shutdown) {
                socketWaitLock.acquireUninterruptibly();
                while (!socket.isClosed()) {
                    try {
                        int size = socket.getInputStream().read(buffer);
                        if(size == -1) {
                            socket.close();
                            break;
                        }
                        byte[] data = new byte[size];
                        System.arraycopy(buffer, 0, data, 0, size);
                        channelHandler.send(channelId, data);
                    } catch (IOException e) {
                        if (!socket.isClosed()) {
                            e.printStackTrace();
                        }
                        socket.close();
                    }
                }
                System.out.println("Closed socket");
            }
        } catch (IOException ex) {
            Logger.getLogger(TCPChannel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void close() {
        try {
            shutdown = true;
            socket.close();
        } catch (IOException ex) {
            Logger.getLogger(TCPChannel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public String toString() {
        return socket == null ? null : socket.getRemoteSocketAddress().toString();
    }
}
