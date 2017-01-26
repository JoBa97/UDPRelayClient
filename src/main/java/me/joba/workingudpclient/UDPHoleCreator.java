/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.joba.workingudpclient;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import net.rudp.ReliableServerSocket;
import net.rudp.ReliableSocket;
import net.rudp.ReliableSocketProfile;

/**
 *
 * @author balsfull
 */
public class UDPHoleCreator {

    public static NATHole createHole(String tokenString, InetAddress target, InetSocketAddress server) throws IOException, InterruptedException {
        System.out.println("Punching hole...");
        System.out.println("Connecting to remote server: " + server);
        int port;
        DatagramSocket socket = new DatagramSocket();
        socket.setSendBufferSize(60000);
        socket.setReceiveBufferSize(60000);
        byte[] token = tokenString.getBytes();
        DatagramPacket initPacket = new DatagramPacket(token, token.length, server);
        System.out.println("Sending token...");
        socket.send(initPacket);
        byte[] buffer = new byte[64];
        final DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
        System.out.println("Receiving port...");
        socket.receive(receivePacket);
        ByteBuffer buff = ByteBuffer.wrap(receivePacket.getData(), receivePacket.getOffset(), receivePacket.getLength());
        byte[] portArray = new byte[2];
        buff.get(portArray);
        boolean isServer = buff.get() != 0;
        port = Short.toUnsignedInt(ByteBuffer.wrap(portArray).getShort());
        System.out.println("Remote port: " + port);
        InetSocketAddress targetHole = new InetSocketAddress(target, port);
        boolean receivedZero = false;
        DatagramPacket confirm = new DatagramPacket(new byte[]{0}, 1, targetHole);
        socket.send(confirm);
        System.out.println("Sending: 0");
        for(int i = 0; i < 2; i++) {
            socket.receive(receivePacket);
            byte first = receivePacket.getData()[receivePacket.getOffset()];
            System.out.println("Received: " + first);
            switch (first) {
                case 0:
                    receivedZero = true;
                    confirm.setData(new byte[]{1});
                    socket.send(confirm);
                    System.out.println("Sending: 1");
                    break;
                case 1:
                    if (!receivedZero) {
                        confirm.setData(new byte[]{1});
                        confirm.setSocketAddress(receivePacket.getSocketAddress());
                        socket.send(confirm);
                        System.out.println("Sending: 1");
                        targetHole = (InetSocketAddress)confirm.getSocketAddress();
                    }
                    Socket reliableSocket;
                    System.out.println("Migrating to reliable connection...");
                    ReliableSocketProfile profile = new ReliableSocketProfile(255, 255, 65535, 255, 255, 255, 255, 255, 65535, 65535, 65535);
                    if(isServer) {
                        ReliableServerSocket rss = new ReliableServerSocket(socket, 0, profile);
                        reliableSocket = rss.accept();
                    }
                    else {
                        reliableSocket = new ReliableSocket(socket, profile);
                        System.out.println("Set size");
                        reliableSocket.connect(targetHole);
                    }
                    System.out.println(reliableSocket.getReceiveBufferSize());
                    System.out.println(reliableSocket.getSendBufferSize());
                    System.out.println("Sending: 2");
                    reliableSocket.getOutputStream().write(2);
                    reliableSocket.getOutputStream().flush();
                    if(reliableSocket.getInputStream().read() == 2) {
                        System.out.println("Received: 2");
                        System.out.println("Connection established.");
                        return new NATHole(reliableSocket, targetHole);
                    }
                default:
                    throw new IOException("Invalid data");
            }
        }
        throw new IOException("No connection");
    }

    public static class NATHole {

        private final Socket socket;
        private final InetSocketAddress target;

        public NATHole(Socket socket, InetSocketAddress target) {
            this.socket = socket;
            this.target = target;
        }

        public InetSocketAddress getTarget() {
            return target;
        }

        public Socket getSocket() {
            return socket;
        }
    }
}
