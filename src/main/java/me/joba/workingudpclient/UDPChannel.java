/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.joba.workingudpclient;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;
import static me.joba.workingudpclient.UDPClient.ROUTES;

/**
 *
 * @author Jonas
 */
public class UDPChannel extends Thread {

    private final DatagramSocket socket;
    private final ChannelHandler channelHandler;
    private InetSocketAddress localDestination;
    private byte channelId;

    public UDPChannel(byte channelId, DatagramSocket socket, ChannelHandler channelHandler, InetSocketAddress localDestination) {
        this.channelId = channelId;
        this.socket = socket;
        this.channelHandler = channelHandler;
        this.localDestination = localDestination;
    }

    public void setChannel(byte channelId) {
        this.channelId = channelId;
    }
    
    public void send(byte[] data) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, localDestination);
        socket.send(packet);
    }
    
    @Override
    public void run() {
        try {
            byte[] buffer = new byte[65535];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            if (localDestination == null) {
                socket.receive(packet);
                this.localDestination = new InetSocketAddress(packet.getAddress(), packet.getPort());
                handlePacket(packet);
                ROUTES.put(channelId, this);
                System.out.println("Opened channel: " + channelId);
            }
            while (!socket.isClosed()) {
                try {
                    socket.receive(packet);
                    handlePacket(packet);
                } catch (Exception e) {
                    if(!socket.isClosed()) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(UDPChannel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void handlePacket(DatagramPacket packet) throws IOException {
        byte[] data = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());
        channelHandler.send(channelId, data);
    }

    public void close() {
        socket.close();
    }

    public DatagramSocket getSocket() {
        return socket;
    }

    @Override
    public String toString() {
        return localDestination.toString();
    }
}
