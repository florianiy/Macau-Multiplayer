package com.example.macaumultiplayer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.ServerSocket;
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

    public void UpdateGame(Letter letter) {
        if (!Objects.equals(letter.action, "update-state"))
            return;

        this.root.getChildren().clear();

        this.root.getChildren().add(new Text("Привет мирid: " + letter.your_id));

        this.root.getChildren().add(new Button(String.join(" ", letter.top_card)));


        var drawCard = new Button("draw card");
        drawCard.setOnAction(event -> {
            var let = new Letter();
            let.action = "draw";
            this.gameClient.send(this.ToJson(let));
        });
        this.root.getChildren().add(drawCard);


        for (var card : letter.cards) {

            if (card == null) break;
            Button _card = new Button(String.join(" ", card));

            this.root.getChildren().add(_card);
            _card.setOnAction(e -> {
                System.out.println(card);
                var let = new Letter();
                let.action = "give";
                let.cards.add(card);
                this.gameClient.send(this.ToJson(let));
                this.root.getChildren().remove(_card);
            });
        }

        for (var player : letter.players) {
            Text _asd = new Text(player.cards_left + " -- player:" + player.player_id);
            this.root.getChildren().add(_asd);
        }
    }

    private void InitGame(boolean hostServer) {
        root = new VBox(10);
        this.Game = new Scene(root, 500, 400, Color.LIGHTGRAY);
        this.Game.getRoot().setStyle("-fx-font-family: 'serif'");

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
