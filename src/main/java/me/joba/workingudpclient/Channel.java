/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.joba.workingudpclient;

import java.io.IOException;

/**
 *
 * @author Jonas
 */
public abstract class Channel extends Thread {
    public abstract void setChannel(byte channelId);
    public abstract void send(byte[] data) throws IOException;
    public abstract void close();
}
