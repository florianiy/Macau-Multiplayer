package com.example.macaumultiplayer;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;

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

        public void RemoveCards(ArrayList<ArrayList<String>> givenCards) {
            for (var card : givenCards)
                this.cards.remove(card);
        }
    }

    private List<Player> players = new ArrayList<Player>();
    private Runnable onStartCallback;

    private Deck deck = new Deck();
    private ArrayList<ArrayList<String>> table = new ArrayList<>();
    private Integer curr_player = 0; // not used yet

    private String GetCurrPlayerId() {
        return this.players.get(this.curr_player).id;
    }

    ObjectMapper objectMapper = new ObjectMapper();
    private final ArrayList<String> ace;
    //    private ArrayList<String> four;
    private Integer umflaturi = 0;

    public GameServer(int port) {
        super(new InetSocketAddress(port));
        this.table.add(this.deck.drawCard());
        this.ace = new ArrayList<String>();
        this.ace.add("ace");
        this.ace.add("clubs");
    }

    public void setOnStartCallback(Runnable callback) {
        this.onStartCallback = callback;
    }

    public ArrayList<String> getTopCard() {
        return this.table.get(this.table.size() - 1);
    }

    public void UpdateClientsState() {
        for (var player : this.players) {
            Letter letter = new Letter();
            letter.action = "update-state";
            letter.your_id = player.id;
            letter.top_card = this.getTopCard();
            letter.cards = player.cards;
            letter.CardsDeckLeft = this.deck.getDeckSize();
            letter.player_turn = this.GetCurrPlayerId();
            letter.table = this.table;
            for (var other : this.players) {
                if (!Objects.equals(other.id, player.id))
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
        if (this.curr_player == -1) this.curr_player = 0;
        this.UpdateClientsState();
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("Player disconnected: " + conn.getRemoteSocketAddress());
        // handle remove player
    }

    private void SetNextPlayer() {
        this.curr_player = (this.curr_player + 1) % this.players.size();
    }

    private BiPredicate<ArrayList<String>, ArrayList<String>> are_compatible_cards = (a, b) ->
            Objects.equals(a.get(0), b.get(0)) || Objects.equals(a.get(1), b.get(1));

    public void HandleRequests(Letter letter, Player player, Integer index) {
        if (player == null) return;

        if (!Objects.equals(this.curr_player, index)) return;

        if (Objects.equals(letter.action, "draw")) {
            if (this.umflaturi > 0) {
                player.cards.addAll(this.deck.drawCards(this.umflaturi));
                this.umflaturi = 0;
            } else {
                player.cards.add(this.deck.drawCard());
            }
            this.SetNextPlayer();
            return;
        }

        if (Objects.equals(letter.action, "give")) {
            var given_cards = letter.cards;
            var _card = given_cards.get(0);

            var are_same_rank = true;
            for (var card : given_cards) {
                if (!Objects.equals(_card.get(0), card.get(0))) {
                    are_same_rank = false;
                    break;
                }
            }
            var is_compatible_hand = this.are_compatible_cards.test(this.getTopCard(), _card);
            var has_umflaturi = are_same_rank && Objects.equals(_card.get(0), "2") || Objects.equals(_card.get(0), "3");
            var has_blocker = are_same_rank && Objects.equals(_card.get(0), "4");


            // he gave cards after he was umflat
            if (this.umflaturi > 0) {
                if (has_umflaturi) this.umflaturi += Integer.parseInt(_card.get(0)) * given_cards.size();
                if (has_blocker) this.umflaturi = 0;
                player.RemoveCards(given_cards);
                this.table.addAll(given_cards);
                this.SetNextPlayer();
                return;
            }

            // no more umflaturi
            if (has_umflaturi) {
                this.umflaturi += Integer.parseInt(_card.get(0)) * given_cards.size();
                player.RemoveCards(given_cards);
                this.table.addAll(given_cards);
                this.SetNextPlayer();
                return;
            }

            if (are_same_rank && is_compatible_hand) {
                player.RemoveCards(given_cards);
                this.table.addAll(given_cards);
                var skipPlayers = Objects.equals(this.ace.get(0), _card.get(0));
                this.SetNextPlayer();
                if (!skipPlayers) return;
                this.curr_player += given_cards.size();
                this.curr_player %= this.players.size();
            }
        }
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
        int i = 0;
        for (; i < this.players.size(); i++) {
            var player = this.players.get(i);
            if (player.conn.equals(conn)) {
                _player = player;
                break;
            }
        }



        this.HandleRequests(letter, _player, i);

        // player has won
        if (_player.cards.isEmpty()) {
            this.curr_player = i;
            this.deck.reset();
            this.table.clear();
            this.table.add(this.deck.drawCard());
            for (var it : this.players) {
                it.cards = this.deck.drawCards(5);
            }
        }

        // next player has no cards to counter umflaturi anyway
        var next_player = this.players.get(this.curr_player);
        if (this.umflaturi > 0 && next_player.cards.stream().noneMatch(card ->
                Objects.equals(card.get(0), "2")
                        || Objects.equals(card.get(0), "3")
                        || Objects.equals(card.get(0), "4"))) {
            next_player.cards.addAll(this.deck.drawCards(this.umflaturi));
            this.umflaturi = 0;
            this.SetNextPlayer();
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
