package com.example.macaumultiplayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Deck {
    private ArrayList<ArrayList<String>> cards;

    public Deck() {
        cards = new ArrayList<>();
        String[] suits = {"♠", "♣", "♦", "💖"};
        String[] ranks = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};

        for (String suit : suits) {
            for (String rank : ranks) {
                var hateJavaSomethimes = new ArrayList<String>();
                hateJavaSomethimes.add(rank);
                hateJavaSomethimes.add(suit);
                cards.add( hateJavaSomethimes);
            }
        }
        Collections.shuffle(cards);
    }

    public ArrayList<String> drawCard() {
        if (!cards.isEmpty()) {
            return cards.remove(0);
        }
        return null; // Deck is empty
    }

    public ArrayList<ArrayList<String>> drawCards(int amount) {
        var cards = new ArrayList<ArrayList<String>>();
        for (int i = 0; i < amount; i++) cards.add(this.drawCard());
        return cards;
    }

    public void addCards(List<String> other) {

    }

}
