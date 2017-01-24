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
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import static me.joba.workingudpclient.UDPClient.ROUTES;

/**
 *
 * @author Jonas
 */
public class ChannelHandler extends Thread {

    private final DatagramSocket outgoing;
    private final InetSocketAddress outgoingAddress;

    public ChannelHandler(DatagramSocket outgoing, InetSocketAddress outgoingAddress) {
        this.outgoing = outgoing;
        this.outgoingAddress = outgoingAddress;
    }

    public void send(byte channel, byte[] dataRaw) throws IOException {
        //System.out.println(Arrays.hashCode(dataRaw) + " " + Arrays.toString(dataRaw) + " -> " + channel);
        byte[] data = ByteBuffer.allocate(dataRaw.length + 1)
                .put(channel)
                .put(dataRaw)
                .array();
        DatagramPacket packet = new DatagramPacket(data, data.length);
        packet.setSocketAddress(outgoingAddress);
        outgoing.send(packet);
    }
    
    @Override
    public void run() {
        Thread keepAlive = new Thread() {
            @Override
            public void run() {
                DatagramPacket ping = new DatagramPacket(new byte[]{0}, 1, outgoingAddress);
                while(true) {
                    try {
                        outgoing.send(ping);
                        Thread.sleep(1000 * 10);
                    } catch (InterruptedException | IOException ex) {
                        Logger.getLogger(ChannelHandler.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        };
        keepAlive.start();
        byte[] buffer = new byte[65535];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        while (true) {
            try {
                outgoing.receive(packet);
                //ByteBuffer bbuff = ByteBuffer.wrap(packet.getData());
                
                byte[] data = new byte[packet.getLength() - 1];
                System.arraycopy(packet.getData(), packet.getOffset() + 1, data, 0, packet.getLength() - 1);
                //bbuff.get(data, packet.getOffset() + 1, packet.getLength() - 1);
                byte channel = packet.getData()[packet.getOffset()];
                //System.out.println(channel + " -> " + Arrays.hashCode(data) + " " + Arrays.toString(data));
                if(channel != 0 && ROUTES.containsKey(channel)) {
                    ROUTES.get(channel).send(data);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
