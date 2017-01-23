/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.joba.workingudpclient;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPResult;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
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
        JSAP jsap = new JSAP();
        FlaggedOption targetIp = new FlaggedOption("targetIp")
                                    .setStringParser(JSAP.INETADDRESS_PARSER)
                                    .setRequired(true)
                                    .setShortFlag('d')
                                    .setLongFlag("destination");
        FlaggedOption relayIp = new FlaggedOption("relayIp")
                                    .setStringParser(JSAP.INETADDRESS_PARSER)
                                    .setRequired(true)
                                    .setShortFlag('r')
                                    .setLongFlag("relay");
        FlaggedOption relayPort = new FlaggedOption("relayPort")
                                    .setStringParser(JSAP.INTEGER_PARSER)
                                    .setRequired(true)
                                    .setDefault("52125")
                                    .setShortFlag('p')
                                    .setLongFlag("port");
        FlaggedOption token = new FlaggedOption("token")
                                    .setRequired(true)
                                    .setShortFlag('t')
                                    .setLongFlag("token");
        FlaggedOption rules = new FlaggedOption("rules")
                                    .setRequired(false)
                                    .setLongFlag("rules");
        jsap.registerParameter(targetIp);
        jsap.registerParameter(relayIp);
        jsap.registerParameter(relayPort);
        jsap.registerParameter(token);
        jsap.registerParameter(rules);
        JSAPResult config = jsap.parse(args);   
        System.out.println(Arrays.toString(config.getStringArray("test")));
        if (!config.success()) {
            System.err.println();
            System.err.println("Usage: " + jsap.getUsage());
            System.err.println();
            System.exit(0);
        }
        InetSocketAddress natBypassServer = new InetSocketAddress(config.getInetAddress("relayIp"), config.getInt("relayPort"));
        NATHole hole = UDPHoleCreator.createHole(config.getString("token"), config.getInetAddress("targetIp"), natBypassServer);
        channelHandler = new ChannelHandler(hole.getSocket(), hole.getTarget());
        if(config.contains("rules")) {
            for (String rule : config.getString("rules").split(",")) {
                if(!rule.equals("")) {
                    parseRule(rule);
                }
            }
        }
        channelHandler.start();
        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
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