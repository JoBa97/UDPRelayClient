package me.joba.workingudpclient.tcp;

import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;
import me.joba.workingudpclient.ChannelHandler;

public class BidirectionalUDPStream extends OutputStream {

    private final byte channelId;
    private final ChannelHandler handler;
    private final Consumer<byte[]> writeTo;
    
    volatile boolean closed = false;

    private final Object swLock = new Object();
    private long swFirstFullSeq = 0, inSeq = 0;

    private final byte[][] sendWindow = new byte[1024][];
    private int swFirstFull = 0, swNextEmpty = 0;
    private int swOut = 0;
    private long lastAckRequestSent = Long.MAX_VALUE;

    public BidirectionalUDPStream(byte channelId, ChannelHandler handler, Consumer<byte[]> writeTo) {
        this.channelId = channelId;
        this.handler = handler;
        this.writeTo = writeTo;
        new Thread(new WriterThreadRunner(this)).start();
    }

    public void runWriterThread() throws Exception {
        int wait = 5000;
        while (true) {
            byte[] dgram;
            int idx = swOut - swFirstFull;
            if (idx < 0) {
                idx += sendWindow.length;
            }
            long seq = swFirstFullSeq + idx;
            synchronized (swLock) {
                if (swOut != swNextEmpty) {
                    dgram = new byte[8 + sendWindow[swOut].length];
                    System.arraycopy(sendWindow[swOut], 0, dgram, 8, sendWindow[swOut].length);
                    swOut = (swOut + 1) % sendWindow.length;
                    lastAckRequestSent = Long.MAX_VALUE;
                    wait = 1;
                }
                else if (swOut != swFirstFull) {
                    dgram = new byte[8];
                    seq |= 0x4000000000000000L;
                    if (lastAckRequestSent == Long.MAX_VALUE) {
                        lastAckRequestSent = System.currentTimeMillis();
                    }
                    else if (System.currentTimeMillis() - lastAckRequestSent > 60000) {
                        close();
                        throw new IOException("Packet was not acked");
                    }
                    if (wait < 5000) {
                        wait *= 2;
                    }
                    if (wait < 50) {
                        wait = 50;
                    }
                }
                else {
                    // queue empty
                    dgram = null;
                    wait = 5000;
                }
            }
            if (dgram != null) {
                System.out.println("Sending dram " + dgram.length);
                send(seq, dgram);
            }
            synchronized (swLock) {
                swLock.wait(wait);
            }
        }
    }

    private void send(long seq, byte[] dgram) throws IOException {
        dgram[0] = (byte) (seq >> 56);
        dgram[1] = (byte) (seq >> 48);
        dgram[2] = (byte) (seq >> 40);
        dgram[3] = (byte) (seq >> 32);
        dgram[4] = (byte) (seq >> 24);
        dgram[5] = (byte) (seq >> 16);
        dgram[6] = (byte) (seq >> 8);
        dgram[7] = (byte) (seq);
        handler.send(channelId, dgram);
    }

    public void receive(byte[] buffer) throws Exception {
        System.out.println(buffer.length);
        if (buffer[0] == 0xff && buffer.length >= 3) {
            System.out.println("ack");
            // that was a packet from the first stager,
            // just ack it
            handler.send(channelId, new byte[]{buffer[1]});
            return;
        }
        if (buffer.length < 8 || buffer.length > 508) {
            System.out.println("???");
            return;
        }
        int flags = ((buffer[0] & 0xFF) >> 6);
        if (flags == 3) {
            return;
        }
        long seq = ((buffer[0] & 0x3FL) << 56) | ((buffer[1] & 0xFFL) << 48) | ((buffer[2] & 0xFFL) << 40) | (buffer[3] & 0xFFL + 32) | ((buffer[4] & 0xFFL) << 24) | ((buffer[5] & 0xFFL) << 16) | ((buffer[6] & 0xFFL) << 8) | (buffer[7] & 0xFFL);
        System.out.println("Seq " + seq);
        System.out.println("inSeq " + inSeq);
        switch (flags) {
            case 0: // data
                if (seq == inSeq) {
                    inSeq++;
                    byte[] data = new byte[buffer.length - 8];
                    System.arraycopy(buffer, 8, data, 0, data.length);
                    System.out.println("Accepting");
                    writeTo.accept(data);
                }
                else if (seq > inSeq) {
                    // send ack so that peer resends missing
                    // data
                    System.out.println("Send a");
                    send(inSeq | 0x8000000000000000L, new byte[8]);
                }
                break;
            case 1: // no new data after, please ack
                if (seq >= inSeq) {
                    // send ack so that peer resends missing
                    // data if needed
                    System.out.println("Send b");
                    send(inSeq | 0x8000000000000000L, new byte[8]);
                }
                break;
            case 2: // ack / please resend after
                synchronized (swLock) {
                    if (seq < swFirstFullSeq) // old ack
                    {
                        return;
                    }
                    while (swFirstFullSeq < seq) {
                        if (swFirstFull == swNextEmpty) {
                            throw new IOException("Unsent data acked");
                        }
                        sendWindow[swFirstFull] = null;
                        swFirstFull = (swFirstFull + 1) % sendWindow.length;
                        swFirstFullSeq++;
                    }
                    swOut = swFirstFull;
                    swLock.notifyAll();
                }
                break;
        }
    }

    public void write(int b) throws IOException {
        write(new byte[]{(byte) b}, 0, 1);
    }

    public synchronized void write(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return;
        }
        while (len > 500) {
            write(b, off, 500);
            off += 500;
            len -= 500;
        }
        byte[] copy = new byte[len];
        System.arraycopy(b, off, copy, 0, len);
        synchronized (swLock) {
            int swNextEmptyNew = (swNextEmpty + 1) % sendWindow.length;
            try {
                while (swNextEmptyNew == swFirstFull) {
                    swLock.wait();
                }
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
            sendWindow[swNextEmpty] = copy;
            swNextEmpty = swNextEmptyNew;
            swLock.notifyAll();
        }
    }

    public synchronized void close() throws IOException {
        closed = true;
        handler.destroyChannel(channelId);
    }

    public static final class WriterThreadRunner implements Runnable {

        private BidirectionalUDPStream s;

        WriterThreadRunner(BidirectionalUDPStream s) {
            this.s = s;
        }

        public void run() {
            try {
                s.runWriterThread();
            } catch (Exception ex) {
                if (!s.closed) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }
}
