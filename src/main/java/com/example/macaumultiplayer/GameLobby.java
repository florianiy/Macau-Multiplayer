package com.example.macaumultiplayer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Objects;

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
        return imageView;
    }

    public ImageView getCardImage(ArrayList<String> card) {
        return this.getImageView("/cards/" + card.get(0) + "_of_" + card.get(1) + ".png");
    }

    Text playerText;
    HBox top_hbox;
    VBox AllCards;

    Pane  deck_ui;
    Integer LastDeckAmount = 50;

    public void UpdateGame(Letter letter) {
        if (!Objects.equals(letter.action, "update-state"))
            return;

        // add this player id
        playerText.setText("Let's Go <" + letter.your_id + ">");
        // topcard
        top_hbox.getChildren().set(0, this.getCardImage(letter.top_card));


        while(this.deck_ui.getChildren().size() >= letter.CardsDeckLeft){
            this.deck_ui.getChildren().remove(letter.CardsDeckLeft -1);
        }

        AllCards.getChildren().clear();

        // card hbox
        Pane pane  = new Pane();
        AllCards.getChildren().add(pane);
        for (int i =0;i<letter.cards.size();i++ ) {
            var card = letter.cards.get(i);
            if (card == null) break;
            var imageView = this.getCardImage(card);
            imageView.setLayoutX(i* 40);
            pane.getChildren().add(imageView);

            imageView.setOnMouseClicked(e -> {
                System.out.println(card);
                var let = new Letter();
                let.action = "give";
                let.cards.add(card);
                this.gameClient.send(this.ToJson(let));
                pane.getChildren().remove(imageView);
            });
        }

        for (var player : letter.players) {
            Pane fp = new Pane();
            for(int i=0;i<player.cards_left;i++){
                var hiddenCard = this.getImageView("/cards/hidden-card.png");
                hiddenCard.setLayoutX(i*15);
                fp.getChildren().add(hiddenCard);
            }

            Text _asd = new Text("player: " + player.player_id);
            this.AllCards.getChildren().addAll(_asd, fp);
        }
    }

    private void InitGame(boolean hostServer) {
        root = new VBox(10);
        this.Game = new Scene(root, 500, 400, Color.LIGHTGRAY);
        this.Game.getRoot().setStyle("-fx-font-family: 'serif'");
        this.playerText = new Text("player: undefined");
        this.top_hbox = new HBox();
        this.AllCards = new VBox();
        this.root.getChildren().addAll(this.playerText, this.top_hbox, this.AllCards);


        // tophbox
        var card = new ArrayList<String>();
        card.add("ace"); card.add("hearts");
        top_hbox.getChildren().add(this.getCardImage(card));
        // deck of cards where player can draw cards
             deck_ui = new Pane();
            for(int i=0;i<this.LastDeckAmount;i++){
                var hiddenCard = this.getImageView("/cards/hidden-card.png");
                hiddenCard.setLayoutX(i*2.5);
                hiddenCard.setLayoutY(-i*0.5);
                hiddenCard.setOnMouseClicked(event -> {
                    var let = new Letter();
                    let.action = "draw";
                    this.gameClient.send(this.ToJson(let));
                });
                deck_ui.getChildren().add(hiddenCard);
            }
        top_hbox.getChildren().add(deck_ui);

//        var deck_of_cards = this.getImageView("/cards/deck_of_cards.png");
//        deck_of_cards.setOnMouseClicked(event -> {
//            var let = new Letter();
//            let.action = "draw";
//            this.gameClient.send(this.ToJson(let));
//        });
//        top_hbox.getChildren().add(deck_of_cards);


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
