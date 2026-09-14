package fr.manubotelho.mestaches;
import static org.junit.Assert.*;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import androidx.test.core.app.ApplicationProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class MainActivityTest {
    private Context context;
    private ActivityController<MainActivity> controller;
    @Before public void before() {
        context=ApplicationProvider.getApplicationContext(); context.deleteDatabase(TaskStore.NAME);
        controller=Robolectric.buildActivity(MainActivity.class).setup().visible();
    }
    @After public void after() {
        if (controller!=null) controller.pause().stop().destroy();
        context.deleteDatabase(TaskStore.NAME);
    }
    @Test public void addCompleteReopenAndUncheckThroughTheScreen() {
        MainActivity activity=controller.get();
        activity.findViewById(MainActivity.ADD).performClick();
        AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue(dialog.isShowing());
        ((EditText)dialog.findViewById(MainActivity.TITLE)).setText("Téléphoner au client");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertFalse(dialog.isShowing());
        TaskStore store=new TaskStore(context);
        assertEquals(1,store.list(false).size());
        long id=store.list(false).get(0).id;
        CheckBox check=activity.getWindow().getDecorView().findViewWithTag("check:"+id);
        assertNotNull(check); check.performClick(); assertTrue(store.get(id).done);
        controller.pause().stop().destroy();
        controller=Robolectric.buildActivity(MainActivity.class).setup().visible();
        activity=controller.get(); activity.findViewById(MainActivity.DONE).performClick();
        CheckBox finished=activity.getWindow().getDecorView().findViewWithTag("check:"+id);
        assertNotNull(finished); assertTrue(finished.isChecked());
        finished.performClick(); assertFalse(store.get(id).done);
        store.close();
    }
    @Test public void datePickerKeepsTheRequestedLeapDayAndDraftSurvivesRecreation() {
        MainActivity activity=controller.get();
        activity.findViewById(MainActivity.ADD).performClick();
        AlertDialog editor=ShadowAlertDialog.getLatestAlertDialog();
        ((EditText)editor.findViewById(MainActivity.TITLE)).setText("Préparer le dossier");
        editor.findViewById(MainActivity.DATE).performClick();
        DatePickerDialog picker=(DatePickerDialog)ShadowAlertDialog.getLatestAlertDialog();
        picker.getDatePicker().updateDate(2028,1,29);
        picker.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue(((android.widget.Button)editor.findViewById(MainActivity.DATE)).getText().toString().contains("2028"));
        Bundle state=new Bundle();
        controller.saveInstanceState(state).pause().stop().destroy();
        controller=Robolectric.buildActivity(MainActivity.class).create(state).start().resume().visible();
        AlertDialog restored=ShadowAlertDialog.getLatestAlertDialog();
        assertEquals("Préparer le dossier",((EditText)restored.findViewById(MainActivity.TITLE)).getText().toString());
        restored.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        TaskStore store=new TaskStore(context);
        java.time.LocalDate day=java.time.Instant.ofEpochMilli(store.list(false).get(0).dueAt)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        assertEquals(java.time.LocalDate.of(2028,2,29),day);
        store.close();
    }
}
