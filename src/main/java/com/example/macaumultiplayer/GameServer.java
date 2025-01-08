package com.example.macaumultiplayer;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.function.BiPredicate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

public class GameServer extends WebSocketServer {

    private static class Player {
        public ArrayList<ArrayList<String>> cards;
        public String id = "";
        WebSocket conn;
        public boolean hasChangeSuitAbillity = false;

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
    private Integer umflaturi = 0;
    private final Integer START_CARDS_AMOUNT = 5;

    private ArrayList<ArrayList<String>> SuitChanger;
    private ArrayList<Integer> dummyCards = new ArrayList<Integer>();

    private ArrayList<ArrayList<String>> CreateTempSuit() {
        var suit = new ArrayList<ArrayList<String>>();
        var hearts = new ArrayList<String>();
        hearts.add("ace");
        hearts.add("hearts");
        var clubs = new ArrayList<String>();
        clubs.add("ace");
        clubs.add("clubs");
        var spades = new ArrayList<String>();
        spades.add("ace");
        spades.add("spades");
        var diamonds = new ArrayList<String>();
        diamonds.add("ace");
        diamonds.add("diamonds");
        suit.add(hearts);
        suit.add(spades);
        suit.add(diamonds);
        suit.add(clubs);
        return suit;
    }

    public GameServer(int port) {
        super(new InetSocketAddress(port));
        this.table.add(this.deck.drawCard());

    }

    public void setOnStartCallback(Runnable callback) {
        this.onStartCallback = callback;
    }

    public ArrayList<String> getTopCard() {
        return this.table.get(this.table.size() - 1);
    }

    public void UpdateClientsState() {
        for (var player : this.players) {
            player.cards.sort(Comparator.comparing(o -> o.get(0)));
            Letter letter = new Letter();
            letter.action = "update-state";
            letter.your_id = player.id;
            letter.top_card = this.getTopCard();
            letter.cards = player.cards;
            letter.CardsDeckLeft = this.deck.getDeckSize();
            letter.player_turn = this.GetCurrPlayerId();
            letter.table = this.table;
            for (var other : this.players) {
                if (!Objects.equals(other.id, player.id)) {
                    var size = other.cards.size();
                    if (other.hasChangeSuitAbillity) size = this.SuitChanger.size();
                    letter.players.add(new Letter.PlayerInfo(other.id, size));
                }
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
        var player = new Player(UUID.randomUUID().toString(), conn, this.deck.drawCards(START_CARDS_AMOUNT));
        players.add(player);
        if (this.curr_player == -1) this.curr_player = 0;
        this.UpdateClientsState();
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("Player disconnected: " + conn.getRemoteSocketAddress());
        var i = this.getPlayerIndexByWsConn(conn);
        this.deck.RecycleCards(this.players.get(i).cards);
        this.players.remove((int)i);
        this.UpdateClientsState();

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
            var skipPlayers = are_same_rank && Objects.equals("ace", _card.get(0));
            var changeSuit = are_same_rank && Objects.equals(_card.get(0), "7");

            // he gave cards after he was umflat
            if (this.umflaturi > 0) {
                if (has_umflaturi) this.umflaturi += Integer.parseInt(_card.get(0)) * given_cards.size();
                if (has_blocker) this.umflaturi = 0;
                player.RemoveCards(given_cards);
                this.table.addAll(given_cards);
                this.SetNextPlayer();
                this.RemoveAnyDummyCard();
                return;
            }

            // no more umflaturi
            if (has_umflaturi) {
                this.umflaturi += Integer.parseInt(_card.get(0)) * given_cards.size();
                player.RemoveCards(given_cards);
                this.table.addAll(given_cards);
                this.SetNextPlayer();
                this.RemoveAnyDummyCard();
                return;
            }

            // frontend expects blockers to be allowed above any card
            if (has_blocker) {
                player.RemoveCards(given_cards);
                this.table.addAll(given_cards);
                this.SetNextPlayer();
                this.RemoveAnyDummyCard();
                return;
            }


            // all dummies are removed and it works perfectly => just be careful when reshufling
            // so that you dont reshufle before removing dummy
            // !!! potential issue, all draw instead of give until deck is empty => remove dummys,
            //  => and reindex last dummy, so even if they draw infinetely it works n-rec-times
            if (player.hasChangeSuitAbillity) {
                var newTopCard = new ArrayList<>(this.getTopCard());
                newTopCard.set(1, given_cards.get(0).get(1));
                this.table.add(newTopCard);
                this.dummyCards.add(this.table.size() - 1);
                player.hasChangeSuitAbillity = false;
                player.cards.clear();
                player.cards.addAll(this.SuitChanger);
                this.SetNextPlayer();
                return;
            }

            if (changeSuit) {
                player.RemoveCards(given_cards);
                this.SuitChanger = new ArrayList<>(player.cards);
                player.hasChangeSuitAbillity = true;
                player.cards.clear();
                player.cards.addAll(this.CreateTempSuit());
                return;
            }

            if (are_same_rank && is_compatible_hand) {
                this.RemoveAnyDummyCard();
                player.RemoveCards(given_cards);
                this.table.addAll(given_cards);
                this.SetNextPlayer();
                if (skipPlayers) {
                    this.curr_player += given_cards.size();
                    this.curr_player %= this.players.size();
                }
            }
        }
    }

    private void RemoveAnyDummyCard() {
        // when you remove the index decreasez :(((((((
        for (int i = this.dummyCards.size() - 1; i >= 0; i--)
            this.table.remove((int) (this.dummyCards.get(i)));
        this.dummyCards.clear();
    }


    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("Message from player: " + message);

        var i = this.getPlayerIndexByWsConn(conn);

        if(i<0) return;

        var player = this.players.get(i);
        var letter = this.ParseLetter(message);

        this.HandleRequests(letter, player, i);
        this.HandleWinning(player, i);
        this.HandleDefencelessUmflaturi();

        this.UpdateClientsState();
    }

    private Integer getPlayerIndexByWsConn(WebSocket conn) {
        int i = 0;
        for (; i < this.players.size(); i++) {
            var player = this.players.get(i);
            if (player.conn.equals(conn)) return i;
        }
        return -1;
    }

    private Letter ParseLetter(String message) {
        var letter = new Letter();
        try {
            letter = objectMapper.readValue(message, Letter.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return  letter;
    }

    private void HandleDefencelessUmflaturi() {
        // in case all disconnect but this one player
        if(this.curr_player >= this.players.size()) return;

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
    }

    private void HandleWinning(Player player, int i) {
        if(player == null) return;
        // player has won

        if (player.cards.isEmpty()) {
            this.curr_player = i;
            this.deck.reset();
            this.table.clear();
            this.table.add(this.deck.drawCard());
            for (var it : this.players) {
                it.cards = this.deck.drawCards(START_CARDS_AMOUNT);
            }
        }
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
