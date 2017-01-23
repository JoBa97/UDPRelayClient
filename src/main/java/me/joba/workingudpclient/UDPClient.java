/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.joba.workingudpclient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import me.joba.workingudpclient.UDPHoleCreator.NATHole;

/**
 *
 * @author Jonas
 */
public class UDPClient {

    //<Channel, Port>
    //Channel -1 = next Connection
    //Port -1 = next Channel
    public static final Map<Byte, UDPChannel> ROUTES = new HashMap<>();
    private static final int DEFAULT_PORT = 52125;
    private static ChannelHandler channelHandler;
    
    public static void main(String[] args) throws Exception {
        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
        InetAddress target;
        InetSocketAddress natBypassServer;
        String token;
        String[] rules;
        if (args.length >= 4) {
            target = InetAddress.getByName(args[0]);
            String[] serverData = args[1].split(":");
            natBypassServer = new InetSocketAddress(InetAddress.getByName(serverData[0]), serverData.length == 1 ? DEFAULT_PORT : Integer.parseInt(serverData[1]));
            token = args[2];
            rules = args[3].split(";");
        } else {
            System.out.print("Target: ");
            target = InetAddress.getByName(inFromUser.readLine());
            System.out.print("Bypass: ");
            String[] serverData = inFromUser.readLine().split(":");
            natBypassServer = new InetSocketAddress(InetAddress.getByName(serverData[0]), serverData.length == 1 ? DEFAULT_PORT : Integer.parseInt(serverData[1]));
            System.out.print("Token: ");
            token = inFromUser.readLine();
            System.out.print("Rules: ");
            rules = inFromUser.readLine().split(";");
        }
        NATHole hole = UDPHoleCreator.createHole(token, target, natBypassServer);
        channelHandler = new ChannelHandler(hole.getSocket(), hole.getTarget());
        for (String rule : rules) {
            if(!rule.equals("")) {
                parseRule(rule);
            }
        }
        channelHandler.start();
        while(true) {
            try {
                String line = inFromUser.readLine();
                if(line.equalsIgnoreCase("exit")) {
                    System.exit(0);
                }
                else if(line.equalsIgnoreCase("show")) {
                    for(Entry<Byte, UDPChannel> entry : ROUTES.entrySet()) {
                        System.out.println(entry.getKey() + ": " + entry.getValue());
                    }
                }
                else {
                    parseRule(line);
                }
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void parseRule(String rule) throws SocketException, UnknownHostException {
        switch (rule.charAt(0)) {
            case '+': {
                rule = rule.substring(1);
                String[] data = rule.split("-");
                byte channel = Byte.parseByte(data[0]);
                if (ROUTES.containsKey(channel)) {
                    System.out.println("Channel " + channel + " already exists.");
                    return;
                }
                rule = data[1];
                int port;
                InetAddress address;
                if (rule.contains(":")) {
                    data = rule.split(":");
                    rule = data[1];
                    address = InetAddress.getByName(data[0]);
                } else {
                    address = InetAddress.getLoopbackAddress();
                }
                port = Integer.parseInt(rule);
                UDPChannel udpSocket = new UDPChannel(channel, new DatagramSocket(), channelHandler, new InetSocketAddress(address, port));
                udpSocket.start();
                ROUTES.put(channel, udpSocket);
                break;
            }
            case '-': {
                byte channel = Byte.parseByte(rule.substring(1));
                ROUTES.get(channel).close();
                ROUTES.remove(channel);
                break;
            }
            case '|': {
                String[] channels = rule.substring(1).split(",");
                byte channel1 = Byte.parseByte(channels[0]);
                byte channel2 = Byte.parseByte(channels[1]);
                UDPChannel udpc1 = ROUTES.get(channel1);
                UDPChannel udpc2 = ROUTES.get(channel2);
                if (udpc1 != null) {
                    udpc1.setChannel(channel2);
                    ROUTES.put(channel2, udpc1);
                }
                if (udpc2 != null) {
                    udpc2.setChannel(channel1);
                    ROUTES.put(channel1, udpc2);
                }
                break;
            }
            case '?': {
                String[] data = rule.substring(1).split("-");
                byte channel = Byte.parseByte(data[0]);
                int port = Integer.parseInt(data[1]);
                UDPChannel udpSocket = new UDPChannel(channel, new DatagramSocket(port), channelHandler, null);
                udpSocket.start();
                break;
            }
            default: {
                System.out.println("Unknown command: " + rule);
            }
        }
    }
}