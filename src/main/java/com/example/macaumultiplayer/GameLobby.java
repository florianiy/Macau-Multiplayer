package com.example.macaumultiplayer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.io.Console;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.*;
import java.util.function.BiPredicate;

public class GameLobby extends Application {

    private Scene Game;
    public VBox root;
    private GameClient gameClient;
    private GameServer gameServer;
    ObjectMapper objectMapper = new ObjectMapper();

    public String ToJson(Letter letter) {
        try {
            return this.objectMapper.writeValueAsString(letter);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isPortInUse(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            return false; // Port is available
        } catch (IOException e) {
            return true; // Port is already in use
        }
    }

    public ImageView getImageView(String url) {
        Image image = new Image(getClass().getResource(url).toExternalForm());
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(70);
        imageView.setPreserveRatio(true);
        imageView.setCache(true);
        imageView.setOnMouseEntered(event -> {
            if (imageView.getScaleY() == 1) {
                imageView.setScaleY(1.2);
                imageView.setScaleX(1.2);
            }

        });

        imageView.setOnMouseExited(event -> {
            if (imageView.getScaleY() > 1) imageView.setScaleY(1);
            imageView.setScaleX(1);
        });

        return imageView;
    }

    public ImageView getCardImage(ArrayList<String> card) {
        return this.getImageView("/cards/" + card.get(0) + "_of_" + card.get(1) + ".png");
    }

    Text playerText;
    HBox top_hbox;
    VBox AllCards;

    Pane deck_ui;
    Integer LastDeckAmount = 50;
    Boolean isMyTurn = false;

    Letter cardSenderLetter = new Letter();
    BiPredicate<ArrayList<String>, ArrayList<String>> are_compatible_cards = (a, b) ->
            Objects.equals(a.get(0), b.get(0)) || Objects.equals(a.get(1), b.get(1));


    public Map<Integer, Boolean> GetValidCardsIntellisense(Letter letter){
        var compatibles = new ArrayList<ArrayList<String>>();
        compatibles.add(letter.top_card);
        var tsize = letter.cards.size();
        Map<Integer, Boolean> illy_map = new HashMap<>();
        for (int i = 0; i < tsize *2 ; i++) {

            var thiscard = letter.cards.get(i %tsize);
            var topCardCompatible = this.are_compatible_cards.test(letter.top_card, thiscard);
            var umflaturi = Objects.equals(thiscard.get(0), "2") || Objects.equals(thiscard.get(0), "3");
            // blockeru se da peste orice chiar daca nu esti umflat momentan in backend
            var blocker = Objects.equals(thiscard.get(0), "4");
            var suitChanger = Objects.equals(thiscard.get(0), "7");
            var sameRank = false;

            for (var ccard : compatibles) {
                if (Objects.equals(ccard.get(0), thiscard.get(0))) {
                    sameRank = true;
                    break;
                }
            }
            if(topCardCompatible) compatibles.add(thiscard);
            if(topCardCompatible || sameRank || umflaturi || blocker || suitChanger) {
                illy_map.put(i%tsize, true);
            }
        }
    return illy_map;
    }

    Pane table_ui;
    public void UpdateGame(Letter letter) {
        if (!Objects.equals(letter.action, "update-state"))
            return;

        this.isMyTurn = Objects.equals(letter.player_turn, letter.your_id);

        var hue = new ColorAdjust();
        hue.setHue(0);
        this.root.setEffect(hue);
        if (!this.isMyTurn) {
            hue.setHue(0.1);
            this.root.setEffect(hue);
        }

        // add this player id
        playerText.setText("Let's Go <" + letter.your_id + ">");
        // topcard

//        top_hbox.getChildren().set(0, this.getCardImage(letter.top_card));

this.table_ui.getChildren().clear();
        Random random = new Random();

        int jj =0;
        for (var table_card: letter.table) {
            var hiddenCard = this.getCardImage(table_card);
            hiddenCard.setLayoutX(10+jj * 0.2 *  (random.nextBoolean() ? 1 : -1));
            hiddenCard.setRotate(random.nextInt(20) * (random.nextBoolean() ? 1 : -1));
            hiddenCard.setLayoutY(-jj * 0.2 * (random.nextBoolean() ? 1 : -1));

            table_ui.getChildren().add(hiddenCard);
            jj++;

        }

        while (this.deck_ui.getChildren().size() >= letter.CardsDeckLeft) {
            this.deck_ui.getChildren().remove(letter.CardsDeckLeft - 1);
        }

        AllCards.getChildren().clear();

        // card hbox
        Pane pane = new Pane();
        AllCards.getChildren().add(pane);
        // card intelli sense
        ColorAdjust grayscale = new ColorAdjust();
        grayscale.setSaturation(-1);
        var illy_map = this.GetValidCardsIntellisense(letter);
        System.out.println(illy_map);
        for (int i = 0; i < letter.cards.size(); i++) {

            var card = letter.cards.get(i);
            if (card == null) break;
            var imageView = this.getCardImage(card);
            imageView.setLayoutX(i * 40);
            pane.getChildren().add(imageView);

            if(!illy_map.containsKey(i)) imageView.setOpacity(.3);

            imageView.setOnMouseClicked(event -> {
                if (!this.isMyTurn) return;
                if (event.isControlDown()) {
                    cardSenderLetter.cards.add(card);
                } else {
                    // prevent duplicate send of same card
                    if (imageView.getScaleY() != 0.6) cardSenderLetter.cards.add(card);
                    this.gameClient.send(this.ToJson(cardSenderLetter));
                    cardSenderLetter.cards.clear();
                }
                imageView.setScaleY(0.6);

            });
        }




        for (var player : letter.players) {
            Pane fp = new Pane();
            for (int i = 0; i < player.cards_left; i++) {
                var hiddenCard = this.getImageView("/cards/hidden-card.png");
                hiddenCard.setLayoutX(i * 15);
                fp.getChildren().add(hiddenCard);
            }

            Text _asd = new Text("player: " + player.player_id);
            StackPane hugger = new StackPane();
            hugger.setAlignment(_asd, Pos.CENTER_LEFT);
            hugger.getChildren().addAll(fp, _asd);


            this.AllCards.getChildren().add(hugger);
        }
    }

    private void InitGame(boolean hostServer) {
        this.root = new VBox(10);
        this.root.setStyle("-fx-background-color: lightgray;");
        this.Game = new Scene(root, 500, 400, Color.LIGHTGRAY);
        this.Game.getRoot().setStyle("-fx-font-family: 'serif'");
        this.playerText = new Text("player: undefined");
        this.top_hbox = new HBox();
        this.AllCards = new VBox();
        this.root.getChildren().addAll(this.playerText, this.top_hbox, this.AllCards);


        this.cardSenderLetter.action = "give";
        // tophbox
//        var card = new ArrayList<String>();
//        card.add("ace");
//        card.add("hearts");

        table_ui = new Pane();


        top_hbox.getChildren().add(table_ui);
        // deck of cards where player can draw cards
        deck_ui = new Pane();
        Random random = new Random();

        for (int i = 0; i < this.LastDeckAmount; i++) {
            var hiddenCard = this.getImageView("/cards/hidden-card.png");
            hiddenCard.setLayoutX(100+i * 0.1 *  (random.nextBoolean() ? 1 : -1));
            hiddenCard.setRotate(random.nextInt(10) * (random.nextBoolean() ? 1 : -1));
            hiddenCard.setLayoutY(-i * 0.1 * (random.nextBoolean() ? 1 : -1));
            hiddenCard.setOnMouseClicked(event -> {
                if (!this.isMyTurn) return;
                var let = new Letter();
                let.action = "draw";
                this.gameClient.send(this.ToJson(let));
            });
            deck_ui.getChildren().add(hiddenCard);
        }

        top_hbox.getChildren().add(deck_ui);


        try {
            this.gameClient = new GameClient(12334);
            this.gameClient.setOnMessageListener((json) -> {
                System.out.println(json);
                try {
                    Letter letter = this.objectMapper.readValue(json, Letter.class);
                    Platform.runLater(() -> {
                        this.UpdateGame(letter);
                    });
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

        Thread serverThread = null;
        if (hostServer) {
            this.gameServer = new GameServer(12334);
            serverThread = new Thread(() -> {
                try {
                    this.gameServer.setOnStartCallback(() -> {
                        try {
                            this.gameClient.connect();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    this.gameServer.start();
                    System.out.println("WebSocket server started on port " + 12334);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

        } else {
            try {
                this.gameClient.connect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (hostServer) {
            serverThread.setDaemon(true); // Make it a daemon thread
            serverThread.start();
        }
    }

    @Override
    public void start(Stage primaryStage) {

        this.InitGame(!this.isPortInUse(12334));
        var params = super.getParameters().getRaw();
        int x = Integer.parseInt(params.get(0));
        int y = Integer.parseInt(params.get(1));
        primaryStage.setX(x);
        primaryStage.setY(y);
        primaryStage.setScene(Game);
        primaryStage.setTitle("Macau Multiplayer Game");
        primaryStage.show();
    }


    public static void main(String[] args) {
        launch(args);
    }
}
