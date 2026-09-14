package fr.manubotelho.mestaches;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class TaskStoreAttachmentTest {
    private Context context;
    private TaskStore store;

    @Before public void before() {
        context=ApplicationProvider.getApplicationContext();
        context.deleteDatabase(TaskStore.NAME);
        store=new TaskStore(context);
    }

    @After public void after() {
        if (store!=null) store.close();
        context.deleteDatabase(TaskStore.NAME);
    }

    @Test public void taskKeepsLinkedItemsAndChecklistState() {
        long taskId=store.save(0,"Préparer rendez-vous",System.currentTimeMillis()-1000,false);
        long note=store.addAttachment(taskId,"note","Note","Ne pas oublier le plan",null);
        long check=store.addAttachment(taskId,"check","Sous-tâche","0|Appeler le client",null);
        assertEquals(2,store.listAttachments(taskId).size());
        store.updateAttachment(check,"check","Sous-tâche","1|Appeler le client",null);
        assertTrue(store.listAttachments(taskId).get(1).value.startsWith("1|"));
        store.deleteAttachment(note);
        assertEquals(1,store.listAttachments(taskId).size());
        store.delete(taskId);
        assertTrue(store.listAttachments(taskId).isEmpty());
    }
}
