package com.example.macaumultiplayer;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.util.ArrayList;
import java.util.List;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class Letter {
    public String action = "";
    public ArrayList<ArrayList<String>> cards = new ArrayList<ArrayList<String>>();
    public ArrayList<PlayerInfo> players = new ArrayList<PlayerInfo>();
    public ArrayList<String> top_card;
    public Integer CardsDeckLeft = 50;
    public String player_turn = "";
    public String your_id = "";

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class PlayerInfo{
        PlayerInfo(){}
        PlayerInfo(String id, Integer amount){
            this.player_id = id;
            this.cards_left = amount;
        }
        public String player_id = "";
        public Integer cards_left = -1;

    }
}
