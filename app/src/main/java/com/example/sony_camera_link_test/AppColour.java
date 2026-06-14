package com.example.sony_camera_link_test;

import android.content.Context;
import androidx.core.content.ContextCompat;

public enum AppColour {
    BLACK(R.color.black),
    WHITE(R.color.white),

    MAIN_BACKGROUND(R.color.main_background),
    CARD_BACKGROUND(R.color.card_background),

    //SIDE_MENU_TRANSPARENT(R.color.side_menu_transparent),

    TEXT_DARK_GREY(R.color.text_dark_grey),
    TEXT_LIGHT_GREY(R.color.text_light_grey),
    TEXT_BLUE(R.color.text_blue),

    DARK_PURPLE(R.color.dark_purple),
    MEDIUM_PURPLE(R.color.medium_purple),
    LIGHT_PURPLE(R.color.light_purple),
    LIGHT_PINK(R.color.light_pink),
    DARK_PINK(R.color.dark_pink),
    LIGHT_RED(R.color.light_red),
    DARK_RED(R.color.dark_red);

    private final int colorRes;

    AppColour(int colorRes) {
        this.colorRes = colorRes;
    }

    public int getColorRes() {
        return colorRes;
    }

    public int getColor(Context context) {
        return ContextCompat.getColor(context, colorRes);
    }
}
