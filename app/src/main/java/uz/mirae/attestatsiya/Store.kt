package uz.mirae.attestatsiya

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.time.LocalDate

val codes = listOf("huquq", "kasbiy ta'lim", "pedagogika")
val labels = listOf("Huquq", "Kasbiy ta’lim", "Pedagogika")
data class Item(val id:Int,val group:String,val topic:String,val stem:String,val options:List<String>,val key:String,val pick:String)
data class Attempt(val id:Int,val size:Int,val done:Boolean,val day:String)
class Store(context:Context) {
    private val bank:SQLiteDatabase
    private val db = context.openOrCreateDatabase("progress.db",Context.MODE_PRIVATE,null)
    init {
        val file = File(context.filesDir,"bank400.db")
        if (!file.exists()) {
            val temp = File(context.filesDir,"bank.tmp")
            context.assets.open("bank.db").use { input -> temp.outputStream().use { input.copyTo(it) } }
            check(temp.renameTo(file))
        }
        bank = SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY)
        db.execSQL("CREATE TABLE IF NOT EXISTS runs(id INTEGER PRIMARY KEY,size INTEGER,done INTEGER DEFAULT 0,day TEXT,cursor INTEGER DEFAULT 0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS items(run INTEGER,pos INTEGER,q INTEGER,pick TEXT DEFAULT '',PRIMARY KEY(run,pos),UNIQUE(run,q))")
        val columns = db.rawQuery("PRAGMA table_info(items)", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(1)) }
        }
        if ("draft" !in columns) db.execSQL("ALTER TABLE items ADD COLUMN draft TEXT NOT NULL DEFAULT ''")
    }
    fun runs():List<Attempt> = db.rawQuery("SELECT * FROM runs ORDER BY id DESC",null).use { c ->
        buildList { while(c.moveToNext()) add(Attempt(c.getInt(0),c.getInt(1),c.getInt(2)==1,c.getString(3))) }
    }
    fun start(size:Int):Int {
        require(size==25 || size==50)
        db.beginTransaction()
        try {
            runs().firstOrNull { !it.done }?.let { return it.id }
            val quotas = if(size==25) listOf(17,3,5) else listOf(35,5,10)
            val ids = codes.zip(quotas).flatMap { (code,n) ->
                bank.rawQuery("SELECT id FROM questions_flat WHERE about=? ORDER BY random() LIMIT ?",arrayOf(code,n.toString())).use { c ->
                    buildList<Int> { while(c.moveToNext()) add(c.getInt(0)) }.also { check(it.size==n) }
                }
            }.shuffled()
            db.execSQL("INSERT INTO runs(size,day) VALUES(?,?)",arrayOf(size,LocalDate.now().toString()))
            val id = db.rawQuery("SELECT last_insert_rowid()",null).use { it.moveToFirst(); it.getInt(0) }
            ids.forEachIndexed { pos,q -> db.execSQL("INSERT INTO items(run,pos,q) VALUES(?,?,?)",arrayOf(id,pos,q)) }
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction() }
    }
    fun items(run:Int):List<Item> {
        val picks = linkedMapOf<Int,String>()
        db.rawQuery("SELECT q,pick FROM items WHERE run=? ORDER BY pos",arrayOf(run.toString())).use { c ->
            while(c.moveToNext()) picks[c.getInt(0)] = c.getString(1)
        }
        if(picks.isEmpty()) return emptyList()
        val rows = mutableMapOf<Int,Item>()
        bank.rawQuery("SELECT id,about,category,question_text,option_a,option_b,option_c,option_d,correct_answer FROM questions_flat WHERE id IN ("+picks.keys.joinToString(",")+")",null).use { c ->
            while(c.moveToNext()) { val id=c.getInt(0); rows[id]=Item(id,c.getString(1),c.getString(2),c.getString(3),(4..7).map { c.getString(it) },c.getString(8),picks.getValue(id)) }
        }
        return picks.keys.map(rows::getValue)
    }
    fun position(run:Int):Int = db.rawQuery("SELECT cursor FROM runs WHERE id=?",arrayOf(run.toString())).use { c -> check(c.moveToFirst()); c.getInt(0) }
    fun draft(run:Int,pos:Int):String =
        db.rawQuery("SELECT draft FROM items WHERE run=? AND pos=?",arrayOf(run.toString(),pos.toString())).use {
            if (it.moveToFirst()) it.getString(0) else ""
        }

    private val currentItem = "run=? AND pos=? AND pick='' AND EXISTS (SELECT 1 FROM runs r WHERE r.id=items.run AND r.done=0 AND r.cursor=items.pos)"

    fun select(run:Int,pos:Int,letter:String) {
        require(letter in listOf("A","B","C","D"))
        db.execSQL("UPDATE items SET draft=? WHERE "+currentItem,arrayOf(letter,run,pos))
    }

    fun advance(run:Int,pos:Int):Int {
        db.execSQL("UPDATE runs SET cursor=cursor+1 WHERE id=? AND cursor=? AND done=0 AND cursor+1<size AND EXISTS (SELECT 1 FROM items WHERE run=runs.id AND pos=runs.cursor AND pick<>'')",arrayOf(run,pos))
        return position(run)
    }

    fun answer(run:Int,pos:Int,pick:String) {
        require(pick in listOf("A","B","C","D"))
        db.beginTransaction()
        try {
            db.execSQL("UPDATE items SET pick=draft WHERE "+currentItem+" AND draft=?",arrayOf(run,pos,pick))
            db.execSQL("UPDATE runs SET done=1,day=? WHERE id=? AND done=0 AND NOT EXISTS(SELECT 1 FROM items WHERE run=? AND pick='')",arrayOf(LocalDate.now().toString(),run,run))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun totals():Map<String,Int> = bank.rawQuery("SELECT about,COUNT(*) FROM questions_flat GROUP BY about",null).use { c ->
        buildMap { while(c.moveToNext()) put(c.getString(0),c.getInt(1)) }
    }
    fun close() { bank.close(); db.close() }
}
