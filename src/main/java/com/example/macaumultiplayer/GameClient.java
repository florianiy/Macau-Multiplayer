package com.example.macaumultiplayer;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Consumer;

public class GameClient extends WebSocketClient {
    public GameClient(int port) throws URISyntaxException{
        super(new URI("ws://localhost:"+ port));
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        System.out.println("Connected to server!");

    }
    private Consumer<String> consumer;
    public void setOnMessageListener(Consumer<String> _consumer)
    {
        this.consumer = _consumer;
    }
    @Override
    public void onMessage(String message) {
        consumer.accept(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("Disconnected from server. Reason: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }



}
