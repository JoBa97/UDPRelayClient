/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.joba.workingudpclient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import static me.joba.workingudpclient.UDPClient.ROUTES;

/**
 *
 * @author Jonas
 */
public class ChannelHandler extends Thread {

    private final Socket outgoing;
    public static final byte CHANNEL_KEEP_ALIVE = 0;
    public static final byte CHANNEL_KILL_SOCKET = -1;
    private int sent, received;

    public ChannelHandler(Socket outgoing) {
        this.outgoing = outgoing;
    }

    public void send(byte channel, byte[] dataRaw) throws IOException {
        System.out.println("Out: " + bytesToHex(dataRaw));
        byte[] data = ByteBuffer.allocate(dataRaw.length + 5)
                .put(channel)
                .putInt(dataRaw.length)
                .put(dataRaw, 0, dataRaw.length)
                .array();
        outgoing.getOutputStream().write(data);
        outgoing.getOutputStream().flush();
    }
    
    final protected static char[] hexArray = "0123456789abcdef".toCharArray();
public static String bytesToHex(byte[] bytes) {
    char[] hexChars = new char[bytes.length * 2];
    for ( int j = 0; j < bytes.length; j++ ) {
        int v = bytes[j] & 0xFF;
        hexChars[j * 2] = hexArray[v >>> 4];
        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
    }
    return new String(hexChars);
}
    
    @Override
    public void run() {
        byte[] buffer = new byte[2096928];
        while (true) {
            try {
                int size = outgoing.getInputStream().read(buffer);
                if(size == -1) {
                    System.out.println("UDP Stream closed");
                    return;
                }
                ByteBuffer bbuff = ByteBuffer.wrap(buffer, 0, size);
                while(bbuff.hasRemaining()) {
                    byte channel = bbuff.get();
                    int length = bbuff.getInt();
                    byte[] data = new byte[length];
                    bbuff.get(data);
                    System.out.println(channel + "In: " + bytesToHex(data));
                    if(channel > 0 && ROUTES.containsKey(channel)) {
                        ROUTES.get(channel).send(data);
                    }
                    else {
                        switch(channel) {
                            case CHANNEL_KILL_SOCKET: {
                                byte target = data[0];
                                ROUTES.get(target).killSocket();
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
