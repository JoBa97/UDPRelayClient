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
import me.joba.workingudpclient.tcp.TCPChannel;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import me.joba.workingudpclient.UDPHoleCreator.NATHole;

/**
 *
 * @author Jonas
 */
public class UDPClient {
    
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
                .setRequired(false)
                .setDefault(String.valueOf(DEFAULT_PORT))
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
        if (!config.success()) {
            System.err.println();
            System.err.println("Usage: " + jsap.getUsage());
            System.err.println();
            System.exit(0);
        }
        InetSocketAddress natBypassServer = new InetSocketAddress(config.getInetAddress("relayIp"), config.getInt("relayPort"));
        NATHole hole = UDPHoleCreator.createHole(config.getString("token"), config.getInetAddress("targetIp"), natBypassServer);
        channelHandler = new ChannelHandler(hole.getSocket(), hole.getTarget());
        if (config.contains("rules")) {
            for (String rule : config.getString("rules").split(",")) {
                if (!rule.equals("")) {
                    parseRule(rule);
                }
            }
        }
        channelHandler.start();
        BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            try {
                String line = userInput.readLine();
                if (line.equalsIgnoreCase("exit")) {
                    System.exit(0);
                } else if (line.equalsIgnoreCase("show")) {
                    channelHandler.showChannels();
                } else {
                    parseRule(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    public static void parseRule(String rule) throws SocketException, UnknownHostException, IOException {
        switch (rule.charAt(0)) {
            case '+': {
                char type = rule.charAt(1);
                rule = rule.substring(2);
                String[] data = rule.split("-");
                byte channel = Byte.parseByte(data[0]);
                if (channelHandler.hasChannel(channel)) {
                    
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
                Channel socket;
                switch (type) {
                    case 't':
                        socket = new TCPChannel(channel, channelHandler, address, port);
                        break;
                    case 'u':
                        socket = new UDPChannel(channel, new DatagramSocket(), channelHandler, new InetSocketAddress(address, port));
                        break;
                    default:
                        return;
                }
                socket.start();
                channelHandler.createChannel(channel, socket);
                break;
            }
            case '-': {
                byte channel = Byte.parseByte(rule.substring(1));
                channelHandler.destroyChannel(channel);
                break;
            }
            case '|': {
                String[] channels = rule.substring(1).split(",");
                byte channel1 = Byte.parseByte(channels[0]);
                byte channel2 = Byte.parseByte(channels[1]);
                channelHandler.swapChannels(channel1, channel2);
                break;
            }
            case '?': {
                char type = rule.charAt(1);
                String[] data = rule.substring(2).split("-");
                byte channel = Byte.parseByte(data[0]);
                int port = Integer.parseInt(data[1]);
                Channel socket;
                switch (type) {
                    case 't':
                        socket = new TCPChannel(channel, channelHandler, port);
                        break;
                    case 'u':
                        socket = new UDPChannel(channel, new DatagramSocket(port), channelHandler, null);
                        break;
                    default:
                        return;
                }
                socket.start();
                channelHandler.createChannel(channel, socket);
                break;
            }
            default: {
                
            }
        }
    }
}
