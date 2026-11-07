package xyz.pyrehaven.whostoppedthemusic.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** A draw-without-replacement deck and transport history, independent of Minecraft. */
public final class Deck {
    public record Entry(String id, int weight) {
        public Entry {
            if (weight < 1 || weight > 30) throw new IllegalArgumentException("weight must be 1–30");
        }
    }

    private final Random random;
    private List<Entry> entries = List.of();
    private boolean shuffle;
    private final List<String> cards = new ArrayList<>();
    private final List<String> history = new ArrayList<>();
    private int cursor = -1;

    public Deck(Random random) { this.random = random; }

    public void configure(List<Entry> values, boolean shuffled) {
        if (entries.equals(values) && shuffle == shuffled) return;
        entries = List.copyOf(values);
        shuffle = shuffled;
        cards.clear();
        history.clear();
        cursor = -1;
    }

    public String next() {
        if (cursor + 1 < history.size()) return history.get(++cursor);
        if (entries.isEmpty()) return null;
        if (cards.isEmpty()) refill();
        String id = cards.removeFirst();
        played(id);
        return id;
    }

    private void refill() {
        for (var entry : entries) {
            for (int i = 0; i < entry.weight(); i++) cards.add(entry.id());
        }
        if (shuffle) Collections.shuffle(cards, random);
        String last = cursor < 0 ? null : history.get(cursor);
        if (cards.getFirst().equals(last)) {
            for (int i = 1; i < cards.size(); i++) {
                if (!cards.get(i).equals(last)) { Collections.swap(cards, 0, i); break; }
            }
        }
    }

    public void played(String id) {
        history.subList(cursor + 1, history.size()).clear();
        history.add(id);
        if (history.size() > 256) history.removeFirst();
        cursor = history.size() - 1;
    }

    public boolean canPrevious() { return cursor > 0; }

    public String previous() { return canPrevious() ? history.get(--cursor) : null; }
}
