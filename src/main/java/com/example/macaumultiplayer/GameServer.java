package com.example.macaumultiplayer;

import java.net.InetSocketAddress;
import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

public class GameServer extends WebSocketServer {

    private static class Player {
        public ArrayList<ArrayList<String>> cards;
        public String id = "";
        WebSocket conn;

        Player(String id, WebSocket conn, ArrayList<ArrayList<String>> cards) {
            this.cards = cards;
            this.id = id;
            this.conn = conn;
        }
    }

    private List<Player> players = new ArrayList<Player>();
    private Runnable onStartCallback;

    private Deck deck = new Deck();
    private ArrayList<ArrayList<String>> table = new ArrayList<>();
    private String curr_player = ""; // not used yet
    ObjectMapper objectMapper = new ObjectMapper();

    public GameServer(int port) {
        super(new InetSocketAddress(port));
        this.table.add(this.deck.drawCard());
    }

    public void setOnStartCallback(Runnable callback) {
        this.onStartCallback = callback;
    }

    public ArrayList<String> getTopCard(){
        return this.table.get(this.table.size()-1);
    }
    public void UpdateClientsState(){


        for(var player : this.players){
            Letter letter = new Letter();
            letter.action = "update-state";
            letter.your_id = player.id;
            letter.top_card = this.getTopCard();
            letter.cards = player.cards;
            letter.CardsDeckLeft = this.deck.getDeckSize();
            letter.player_turn = this.curr_player;

           for(var other: this.players){
             if(!Objects.equals(other.id, player.id))
               letter.players.add(new Letter.PlayerInfo(other.id, other.cards.size()));
           }

            String json;
            try {
                json = this.objectMapper.writeValueAsString(letter);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            player.conn.send(json);
        }
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("New player connected: " + conn.getRemoteSocketAddress());
        var player = new Player(UUID.randomUUID().toString(), conn, this.deck.drawCards(5));
        players.add(player);
        this.UpdateClientsState();
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("Player disconnected: " + conn.getRemoteSocketAddress());
//        clients.remove(conn);

        // Notify remaining clients that a player has disconnected
//        for (WebSocket client : clients) {
//            client.send("A player has left the game.");
//        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("Message from player: " + message);
        var letter = new Letter();
        try {
            letter = objectMapper.readValue(message, Letter.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }



        // get player who requested
        Player _player = null;
        for(var player: this.players){
            if(player.conn.equals(conn)) _player = player;
        }


        if(Objects.equals(letter.action, "draw")){
            _player.cards.add(this.deck.drawCard());
        }
        else if(Objects.equals(letter.action, "give")){
            var given = letter.cards;
            var top_card = this.getTopCard();
            if(given.size() == 1) {
                var card = given.get(0);
                if(Objects.equals(card.get(0), top_card.get(0)) || Objects.equals(card.get(1), top_card.get(1))) {
                    _player.cards.remove(card);
                    this.table.add(card);
                }
            }
            else if(given.size() > 1) {
                // to be done
            }
        }

        this.UpdateClientsState();
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        if (onStartCallback != null) {
            onStartCallback.run(); // Invoke the callback
        }
        System.out.println("Server started!");
    }

}
