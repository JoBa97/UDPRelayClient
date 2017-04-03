/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.joba.workingudpclient;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Jonas
 */
public class ChannelHandler extends Thread {

    private final Map<Byte, Channel> ROUTES = new HashMap<>();
    private final DatagramSocket socket;
    private final SocketAddress target;
    public static final byte CHANNEL_KEEP_ALIVE = 0;
    public static final byte CHANNEL_KILL_SOCKET = -1;
    
    public ChannelHandler(DatagramSocket outgoing, SocketAddress target) {
        this.socket = outgoing;
        this.target = target;
    }

    public void send(byte channel, byte[] dataRaw) throws IOException {
        
        byte[] data = ByteBuffer.allocate(dataRaw.length + 1)
                .put(channel)
                .put(dataRaw, 0, dataRaw.length)
                .array();
        DatagramPacket packet = new DatagramPacket(data, data.length, target);
        socket.send(packet);
    }

    final protected static char[] hexArray = "0123456789abcdef".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    @Override
    public void run() {
        byte[] buffer = new byte[65535];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        while (true) {
            try {
                socket.receive(packet);
                byte channel = buffer[packet.getOffset()];
                byte[] data = new byte[packet.getLength() - 1];
                System.arraycopy(buffer, packet.getOffset() + 1, data, 0, data.length);
                
                if (channel > 0 && ROUTES.containsKey(channel)) {
                    ROUTES.get(channel).send(data);
                }
                else {
                    switch (channel) {
                        case CHANNEL_KILL_SOCKET: {
                            byte target = data[0];
                            ROUTES.get(target).killSocket();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public boolean hasChannel(byte id) {
        return ROUTES.containsKey(id);
    }

    public void createChannel(byte id, Channel channel) {
        ROUTES.put(id, channel);
    }

    public void destroyChannel(byte id) {
        Channel channel = ROUTES.get(id);
        ROUTES.remove(id);
        channel.close();
    }

    public void swapChannels(byte id1, byte id2) {
        Channel udpc1 = ROUTES.get(id1);
        Channel udpc2 = ROUTES.get(id2);
        if (udpc1 != null) {
            udpc1.setChannel(id2);
            ROUTES.put(id2, udpc1);
        }
        if (udpc2 != null) {
            udpc2.setChannel(id1);
            ROUTES.put(id1, udpc2);
        }
    }

    public void showChannels() {
        for (Map.Entry<Byte, Channel> entry : ROUTES.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
    }
}
