module Macau.Multiplayer {
    requires org.java_websocket;
    requires javafx.base;
    requires javafx.fxml;
    requires javafx.controls;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.annotation;
    exports com.example.macaumultiplayer;
    opens com.example.macaumultiplayer to javafx.fxml, com.fasterxml.jackson.databind;
}