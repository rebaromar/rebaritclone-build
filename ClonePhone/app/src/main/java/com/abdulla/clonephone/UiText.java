package com.abdulla.clonephone;
import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import android.app.Activity;
import android.view.ContextThemeWrapper;
import java.util.Locale;

final class UiText {
    private static volatile String language="ckb";
    static void init(Context context){language=valid(context.getSharedPreferences("language",0).getString("selected","ckb"));}
    private static String valid(String value){return "en".equals(value)||"ar".equals(value)?value:"ckb";}
    static String language(){return language;}
    static void select(Context context,String value){language=valid(value);context.getSharedPreferences("language",0).edit().putString("selected",language).apply();}
    static String t(int id){return Translations.get(language,id);}
    static int direction(){return Translations.rtl(language)?1:0;}
    static Context context(Activity activity){
        // Keep the Activity as the base: its WindowManager carries the window token.
        // A standalone createConfigurationContext loses that association and can
        // throw BadTokenException when AlertDialog.show() adds its window.
        Configuration config=new Configuration();
        Locale locale=Locale.forLanguageTag(language);
        config.setLocales(new LocaleList(locale));
        config.setLayoutDirection(locale);
        ContextThemeWrapper localized=new ContextThemeWrapper(activity,activity.getTheme());
        localized.applyOverrideConfiguration(config);
        return localized;
    }
}
