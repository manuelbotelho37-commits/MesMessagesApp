package fr.manubotelho.mestaches;
import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class TaskStoreTest {
    private Context context;
    private TaskStore store;
    @Before public void before() {
        context=ApplicationProvider.getApplicationContext();
        context.deleteDatabase(TaskStore.NAME); store=new TaskStore(context);
    }
    @After public void after() { store.close(); context.deleteDatabase(TaskStore.NAME); }
    @Test public void tasksSurviveClosingAndReopeningAndCanBeEdited() {
        long due=LocalDateTime.of(2028,2,29,14,35).atZone(ZoneId.of("Europe/Paris")).toInstant().toEpochMilli();
        long id=store.save(0,"  Rendez-vous client  ",due,true);
        store.close(); store=new TaskStore(context);
        TaskStore.Task saved=store.get(id);
        assertEquals("Rendez-vous client",saved.title); assertEquals(due,saved.dueAt);
        assertTrue(saved.appointment); assertFalse(saved.done);
        store.save(id,"Rendez-vous déplacé",due+3600000,false);
        assertEquals(1,store.list(false).size()); assertEquals(due+3600000,store.get(id).dueAt);
        assertEquals("Rendez-vous déplacé",store.get(id).title);
    }
    @Test public void tasksAreChronologicalIncludingDifferentYearsAndCompletionPersists() {
        long later=store.save(0,"Plus tard",1893456000000L,false);
        long earlier=store.save(0,"Avant",1798761600000L,false);
        assertEquals(earlier,store.list(false).get(0).id);
        assertEquals(later,store.list(false).get(1).id);
        store.setDone(earlier,true);
        store.close(); store=new TaskStore(context);
        assertEquals(1,store.list(false).size()); assertTrue(store.get(earlier).done);
        assertEquals(earlier,store.list(true).get(0).id);
        store.setDone(earlier,false);
        assertEquals(2,store.list(false).size()); assertTrue(store.list(true).isEmpty());
        store.delete(earlier); assertNull(store.get(earlier)); assertEquals(later,store.list(false).get(0).id);
    }
    @Test public void blankTitleCannotBeStored() {
        assertThrows(IllegalArgumentException.class,()->store.save(0," \n ",1,false));
        assertTrue(store.list(false).isEmpty());
    }
}
