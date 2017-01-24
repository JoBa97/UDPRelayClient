/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.joba.workingudpclient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Jonas
 */
public class WaitingTCPChannel extends Channel {
    
    private final ChannelHandler channelHandler;
    private Socket socket;
    private byte channelId;
    private final int listeningPort;
    private boolean shutdown = false;
    private ServerSocket server;
    
    public WaitingTCPChannel(byte channelId, int listeningPort, ChannelHandler channelHandler) throws IOException {
        this.channelId = channelId;
        this.channelHandler = channelHandler;
        this.listeningPort = listeningPort;
        server = new ServerSocket(listeningPort);
                    
    }
    
    @Override
    public void setChannel(byte channelId) {
        this.channelId = channelId;
    }
    
    @Override
    public void send(byte[] data) throws IOException {
        if(socket == null || socket.isClosed()) {
            openSocket();
        }
        socket.getOutputStream().write(data);
        
    }
    
    private void openSocket() throws IOException {
        if(socket != null) {
            socket.close();
        }
        socket = server.accept();  
    }
    
    @Override
    public void run() {
        try {
            byte[] buffer = new byte[60000];
            openSocket();
            System.out.println("Opened channel: " + channelId);
            while (!shutdown) {
                int size;
                while ((size = socket.getInputStream().read(buffer)) != -1) {
                    try {
                        byte[] data = new byte[size];
                        System.arraycopy(buffer, 0, data, 0, size);
                        channelHandler.send(channelId, data);
                    } catch (IOException e) {
                        if (!socket.isClosed()) {
                            e.printStackTrace();
                        }
                    }
                }
                openSocket();
                System.out.println("Opened socket");
            }
            
        } catch (IOException ex) {
            Logger.getLogger(UDPChannel.class.getName()).log(Level.SEVERE, null, ex);
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
