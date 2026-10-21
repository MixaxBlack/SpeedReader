package com.mixax.speedreader;
import android.graphics.Color;
public final class Themes {
    public static final String[] EN = {"Paper", "Night", "Sepia", "Ink", "Forest", "Ocean", "Plum", "Sand", "Slate", "High contrast"};
    public static final String[] RU = {"Бумага", "Ночь", "Сепия", "Чернила", "Лес", "Океан", "Слива", "Песок", "Сланец", "Контраст"};
    public static final String[] BG = {"#F5F1E7", "#151918", "#EFE0C3", "#FFFFFF", "#152D26", "#102B3D", "#302235", "#E8D9BD", "#25303C", "#000000"};
    public static final String[] FG = {"#242E28", "#E4E9DE", "#44321D", "#161616", "#DDEEDF", "#DEEAF4", "#F3E2EF", "#3B3024", "#E5EAF0", "#FFFFFF"};
    static int bg(int i) { return Color.parseColor(BG[i]); }
    static int fg(int i) { return Color.parseColor(FG[i]); }
}
