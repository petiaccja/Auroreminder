package petiaccja.auroreminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by kardo on 2017. 11. 08..
 */

public class ServiceStarter extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            Intent myIntent = new Intent(context, AuroraService.class);
            context.startService(myIntent);

            PendingIntent pendingIntent = PendingIntent.getService(context, 1, myIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
            am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 15*60*1000, 15*60*1000, pendingIntent);
        }
    }
}
