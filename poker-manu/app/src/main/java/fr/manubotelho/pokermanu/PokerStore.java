package fr.manubotelho.pokermanu;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

final class PokerStore extends SQLiteOpenHelper {
    static final class Session {
        long id, startedAt, endedAt;
        String type, location, game, stakes;
        double buyIn, rebuys, cashOut;
        Session(long id,long startedAt,long endedAt,String type,String location,String game,String stakes,double buyIn,double rebuys,double cashOut){
            this.id=id; this.startedAt=startedAt; this.endedAt=endedAt; this.type=type; this.location=location; this.game=game; this.stakes=stakes;
            this.buyIn=buyIn; this.rebuys=rebuys; this.cashOut=cashOut;
        }
        double result(){ return cashOut-buyIn-rebuys; }
        long durationMs(){ return Math.max(0,endedAt-startedAt); }
        double hours(){ return durationMs()/3600000.0; }
        double hourly(){ double h=hours(); return h>0?result()/h:0; }
    }

    static final class Hand {
        long id, createdAt;
        String hero, board, note, game;
        Hand(long id,long createdAt,String hero,String board,String note,String game){
            this.id=id; this.createdAt=createdAt; this.hero=hero; this.board=board; this.note=note; this.game=game;
        }
    }

    PokerStore(Context context){ super(context,"poker_manu.db",null,2); }

    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, started_at INTEGER NOT NULL, ended_at INTEGER NOT NULL, type TEXT NOT NULL DEFAULT 'Cash Game', location TEXT NOT NULL, game TEXT NOT NULL, stakes TEXT NOT NULL, buy_in REAL NOT NULL, rebuys REAL NOT NULL, cash_out REAL NOT NULL)");
        db.execSQL("CREATE INDEX sessions_date ON sessions(started_at DESC)");
        db.execSQL("CREATE TABLE hands (id INTEGER PRIMARY KEY AUTOINCREMENT, created_at INTEGER NOT NULL, hero TEXT NOT NULL, board TEXT NOT NULL, note TEXT NOT NULL, game TEXT NOT NULL)");
        db.execSQL("CREATE INDEX hands_date ON hands(created_at DESC)");
    }

    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion<2){
            try{ db.execSQL("ALTER TABLE sessions ADD COLUMN type TEXT NOT NULL DEFAULT 'Cash Game'"); }catch(Exception ignored){}
        }
    }

    long saveSession(long startedAt,long endedAt,String type,String location,String game,String stakes,double buyIn,double rebuys,double cashOut){
        ContentValues v=new ContentValues();
        v.put("started_at",startedAt); v.put("ended_at",endedAt); v.put("type",clean(type,"Cash Game")); v.put("location",clean(location,"Non renseigné"));
        v.put("game",clean(game,"Hold'em")); v.put("stakes",clean(stakes,"-") );
        v.put("buy_in",Math.max(0,buyIn)); v.put("rebuys",Math.max(0,rebuys)); v.put("cash_out",Math.max(0,cashOut));
        return getWritableDatabase().insertOrThrow("sessions",null,v);
    }

    List<Session> sessions(){
        ArrayList<Session> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("sessions",new String[]{"id","started_at","ended_at","type","location","game","stakes","buy_in","rebuys","cash_out"},null,null,null,null,"started_at DESC")){
            while(c.moveToNext()) out.add(new Session(c.getLong(0),c.getLong(1),c.getLong(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getDouble(7),c.getDouble(8),c.getDouble(9)));
        }
        return out;
    }

    Session lastSession(){
        try(Cursor c=getReadableDatabase().query("sessions",new String[]{"id","started_at","ended_at","type","location","game","stakes","buy_in","rebuys","cash_out"},null,null,null,null,"started_at DESC","1")){
            return c.moveToFirst()?new Session(c.getLong(0),c.getLong(1),c.getLong(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getDouble(7),c.getDouble(8),c.getDouble(9)):null;
        }
    }

    long saveHand(String hero,String board,String note,String game){
        ContentValues v=new ContentValues();
        v.put("created_at",System.currentTimeMillis()); v.put("hero",clean(hero,"-- --")); v.put("board",clean(board,""));
        v.put("note",clean(note,"")); v.put("game",clean(game,"Hold'em"));
        return getWritableDatabase().insertOrThrow("hands",null,v);
    }

    List<Hand> hands(){
        ArrayList<Hand> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("hands",new String[]{"id","created_at","hero","board","note","game"},null,null,null,null,"created_at DESC")){
            while(c.moveToNext()) out.add(new Hand(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5)));
        }
        return out;
    }

    void deleteSession(long id){ getWritableDatabase().delete("sessions","id=?",new String[]{Long.toString(id)}); }
    void deleteHand(long id){ getWritableDatabase().delete("hands","id=?",new String[]{Long.toString(id)}); }

    private String clean(String value,String fallback){
        if(value==null) return fallback;
        String s=value.trim(); return s.isEmpty()?fallback:s;
    }
}
