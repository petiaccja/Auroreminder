package petiaccja.auroreminder;

import android.os.Bundle;
import android.view.Window;
import androidx.fragment.app.FragmentActivity;

public class SettingsActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_settings);
        getSupportFragmentManager().beginTransaction().add(R.id.settings_top_layout, new SettingsFragment()).commit();

    }
}
