package com.limbusarchive.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Raw TCP relay: 127.0.0.1:localPort on the phone -> pcHost:pcPort.
 * The desktop app only answers requests whose Host header is 127.0.0.1:<its port>,
 * so the WebView talks to the same address on the phone and the bytes go through unchanged.
 */
class Relay {
    final int localPort;
    final String pcHost;
    final int pcPort;
    private ServerSocket server;
    private volatile boolean running;

    Relay(int localPort, String pcHost, int pcPort) {
        this.localPort = localPort;
        this.pcHost = pcHost;
        this.pcPort = pcPort;
    }

    void start() throws IOException {
        server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), localPort), 64);
        running = true;
        Thread t = new Thread(() -> {
            while (running) {
                try {
                    Socket client = server.accept();
                    new Thread(() -> handle(client), "relay-conn").start();
                } catch (IOException e) {
                    if (!running) return;
                }
            }
        }, "relay-accept");
        t.setDaemon(true);
        t.start();
    }

    void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (IOException ignored) { }
    }

    private void handle(Socket client) {
        Socket up = new Socket();
        try {
            up.connect(new InetSocketAddress(pcHost, pcPort), 5000);
            up.setTcpNoDelay(true);
            client.setTcpNoDelay(true);
            Thread a = pipe(client, up);
            Thread b = pipe(up, client);
            a.join();
            b.join();
        } catch (Exception ignored) {
        } finally {
            close(client);
            close(up);
        }
    }

    private static Thread pipe(Socket from, Socket to) {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[64 * 1024];
            try {
                InputStream in = from.getInputStream();
                OutputStream out = to.getOutputStream();
                int n;
                while ((n = in.read(buf)) >= 0) { out.write(buf, 0, n); out.flush(); }
            } catch (IOException ignored) {
            } finally {
                try { to.shutdownOutput(); } catch (IOException ignored) { }
            }
        }, "relay-pipe");
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static void close(Socket s) {
        try { s.close(); } catch (IOException ignored) { }
    }
}
