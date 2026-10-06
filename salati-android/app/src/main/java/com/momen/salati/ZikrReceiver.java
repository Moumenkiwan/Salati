package com.momen.salati;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;
import java.util.Random;

public class ZikrReceiver extends BroadcastReceiver {
    /** Each dhikr in Arabic, with its English meaning. */
    private static final String[][] AZKAR = {
            {"سبحان الله وبحمده، سبحان الله العظيم", "Glory be to Allah and praise be to Him. Glory be to Allah, the Most Great."},
            {"لا حول ولا قوة إلا بالله", "There is no power and no strength except with Allah."},
            {"أستغفر الله العظيم وأتوب إليه", "I seek the forgiveness of Allah, the Most Great, and I turn to Him in repentance."},
            {"اللهم صلِّ وسلم على نبينا محمد", "O Allah, send blessings and peace upon our Prophet Muhammad."},
            {"سبحان الله، والحمد لله، ولا إله إلا الله، والله أكبر", "Glory be to Allah, praise be to Allah, there is no god but Allah, and Allah is the Greatest."},
            {"لا إله إلا أنت سبحانك إني كنت من الظالمين", "There is no god but You. Glory be to You. Indeed, I have been among the wrongdoers."},
            {"ربنا آتنا في الدنيا حسنة وفي الآخرة حسنة وقنا عذاب النار", "Our Lord, give us good in this world and good in the Hereafter, and protect us from the punishment of the Fire."},
            {"لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير", "There is no god but Allah alone, without partner. His is the dominion and His is the praise, and He is able to do all things."},
            {"اللهم أعنّي على ذكرك وشكرك وحسن عبادتك", "O Allah, help me to remember You, to thank You, and to worship You well."},
            {"حسبي الله لا إله إلا هو، عليه توكلت وهو رب العرش العظيم", "Allah is sufficient for me. There is no god but Him. In Him I put my trust, and He is the Lord of the Mighty Throne."},
            {"رضيت بالله ربًّا، وبالإسلام دينًا، وبمحمد ﷺ نبيًّا", "I am pleased with Allah as my Lord, Islam as my religion, and Muhammad ﷺ as my Prophet."},
            {"يا حي يا قيوم برحمتك أستغيث، أصلح لي شأني كله، ولا تكلني إلى نفسي طرفة عين", "O Ever-Living, O Sustainer, in Your mercy I seek help. Set right all my affairs and do not leave me to myself even for the blink of an eye."},
            {"اللهم إني أسألك العفو والعافية في الدنيا والآخرة", "O Allah, I ask You for pardon and well-being in this world and the Hereafter."},
            {"أعوذ بكلمات الله التامات من شر ما خلق", "I seek refuge in the perfect words of Allah from the evil of what He has created."}
    };

    @Override
    public void onReceive(Context c, Intent intent) {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h >= 0 && h < 5) return; // quiet between midnight and 5 am
        Notif.ensure(c);
        String[] z = AZKAR[new Random().nextInt(AZKAR.length)];
        String text = Alarms.en(c) ? z[0] + "\n" + z[1] : z[0];
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(Notif.ID_ZIKR, Notif.zikr(c, text));
    }
}
