package com.momen.salati;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;
import java.util.Random;

public class ZikrReceiver extends BroadcastReceiver {
    private static final String[] AAM = {
            "سبحان الله وبحمده، سبحان الله العظيم",
            "لا حول ولا قوة إلا بالله",
            "أستغفر الله العظيم وأتوب إليه",
            "اللهم صلِّ وسلم على نبينا محمد",
            "سبحان الله، والحمد لله، ولا إله إلا الله، والله أكبر",
            "لا إله إلا أنت سبحانك إني كنت من الظالمين",
            "ربنا آتنا في الدنيا حسنة وفي الآخرة حسنة وقنا عذاب النار",
            "لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير",
            "اللهم أعنّي على ذكرك وشكرك وحسن عبادتك",
            "حسبي الله لا إله إلا هو، عليه توكلت وهو رب العرش العظيم",
            "رضيت بالله ربًّا، وبالإسلام دينًا، وبمحمد ﷺ نبيًّا",
            "يا حي يا قيوم برحمتك أستغيث، أصلح لي شأني كله، ولا تكلني إلى نفسي طرفة عين",
            "اللهم إني أسألك العفو والعافية في الدنيا والآخرة",
            "أعوذ بكلمات الله التامات من شر ما خلق"
    };

    @Override
    public void onReceive(Context c, Intent intent) {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h >= 0 && h < 5) return; // quiet hours after midnight
        Notif.ensure(c);
        String text = AAM[new Random().nextInt(AAM.length)];
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(Notif.ID_ZIKR, Notif.zikr(c, text));
    }
}
