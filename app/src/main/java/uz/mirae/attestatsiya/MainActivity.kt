package uz.mirae.attestatsiya

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateContentSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

private val purple=Color(0xff593da0)
private val green=Color(0xff17664e)
private val paper=Color(0xfffaf8ff)
class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var store by remember { mutableStateOf<Store?>(null) }
            var retry by remember { mutableIntStateOf(0) }
            var failed by remember { mutableStateOf(false) }
            LaunchedEffect(retry) {
                failed=false
                try { store=withContext(Dispatchers.IO){ Store(applicationContext) } }
                catch(e:Exception){failed=true}
            }
            DisposableEffect(store){ val opened=store; onDispose { opened?.close() } }
            MaterialTheme(colorScheme=lightColorScheme(primary=purple,background=paper,surface=paper)) {
                Surface(modifier=Modifier.fillMaxSize()){
                    val s=store
                    if(s!=null) App(s) else Box(Modifier.safeDrawingPadding().padding(24.dp)){
                        if(failed) Column {Text("Savollar bazasini ochib bo‘lmadi.");Action("Qayta urinish"){retry++}} else CircularProgressIndicator()
                    }
                }
            }
        }
    }
}
@Composable private fun Heading(s:String){Text(s,style=MaterialTheme.typography.headlineMedium)}
@Composable private fun Action(s:String,enabled:Boolean=true,click:()->Unit){
    Button(onClick=click,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(16.dp)){Text(s,fontSize=16.sp)}
}
@Composable private fun Panel(content:@Composable ColumnScope.()->Unit){
    Surface(color=Color.White,shape=RoundedCornerShape(22.dp),tonalElevation=2.dp){
        Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
    }
}
@Composable private fun Meter(n:Int,total:Int){
    LinearProgressIndicator(progress={if(total==0) 0f else n.toFloat()/total},modifier=Modifier.fillMaxWidth().height(8.dp))
}
private fun accuracy(rows:List<Item>)=if(rows.isEmpty()) 0 else rows.count{it.pick==it.key}*100/rows.size
@Composable private fun App(store:Store){
    var page by rememberSaveable { mutableStateOf("home") }
    var run by rememberSaveable { mutableIntStateOf(0) }
    var pos by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    val totals = remember(store){store.totals()}
    val runs=remember(refresh){store.runs()}
    val history=runs.filter{it.done}
    val active=runs.firstOrNull{!it.done}
    val activeItems=remember(active?.id,refresh){ active?.let { store.items(it.id) }.orEmpty() }
    val cards=remember(run,refresh){store.items(run)}
    val records=remember(history.map{it.id}){history.associate{it.id to store.items(it.id)}}
    val all=remember(records){records.values.flatten()}
    val tabs=listOf("home" to "Bosh sahifa","history" to "Natijalar","stats" to "Statistika")
    fun back(){page=when(page){"review"->"result";"group"->"stats";"result"->"history";else->"home"}}
    BackHandler(page!="home"){back()}
    fun resume(id:Int){
        run=id
        pos=store.position(id)
        selected=store.draft(id,pos);page="test"
    }
    Box(Modifier.fillMaxSize().safeDrawingPadding(),contentAlignment=Alignment.TopCenter){
    Column(Modifier.widthIn(max=720.dp).fillMaxSize().padding(horizontal=16.dp)){
        if(page !in tabs.map{it.first}) TextButton(onClick={back()}){Text(if(page=="test") "‹  Saqlab chiqish" else "‹  Orqaga")}
        key(page,if(page=="test")pos else 0){
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                when(page){
                    "home"->{
                        Text("MIRAE ACADEMY",color=purple,letterSpacing=2.sp);Heading("Attestatsiyaga tayyorgarlik")
                        Surface(color=Color(0xffebe4fc),shape=RoundedCornerShape(24.dp)){ Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                            Text(if(active==null)"Navbatdagi mashq" else "Boshlangan test",color=purple)
                            Text(if(active==null)"Huquq va pedagogika" else "${activeItems.count{it.pick.isNotEmpty()}} / ${active.size} savol bajarildi",style=MaterialTheme.typography.titleLarge)
                            Action(if(active==null)"Test boshlash" else "Davom ettirish"){if(active==null)page="choose" else resume(active.id)}
                        }}
                        val days=history.map{LocalDate.parse(it.day)}.toSet()
                        var day=LocalDate.now();if(day !in days) day=day.minusDays(1)
                        var streak=0;while(day in days){streak++;day=day.minusDays(1)}
                        val points=all.filter{it.pick==it.key}.distinctBy{it.id}.size
                        Panel{ Text("Daraja ${1+points/25}    ·    ${streak} kunlik seriya");Meter(points%25,25);Text("Keyingi darajagacha ${25-points%25} ta yangi to‘g‘ri javob") }
                        history.firstOrNull()?.let{Panel{Text("Oxirgi natija");Text("${accuracy(records.getValue(it.id))}%   ·   ${it.size} savol")}}
                    }
                    "choose"->{Heading("Testni tanlang");listOf(25,50).forEach{n->Panel{
                        Text(if(n==25)"Qisqa mashq" else "To‘liq mashq",color=purple);Heading("$n savol")
                        Text(if(n==25)"17 huquq · 3 kasbiy ta’lim · 5 pedagogika" else "35 huquq · 5 kasbiy ta’lim · 10 pedagogika")
                        Action("Boshlash"){val id=store.start(n);refresh++;resume(id)}
                    }}}
                    "test"->{val q=cards[pos];val locked=q.pick.isNotEmpty()
                        Text("${labels[codes.indexOf(q.group)]}    ·    Savol ${pos+1}/${cards.size}",color=purple)
                        Meter(cards.count{it.pick.isNotEmpty()},cards.size)
                        Text(q.stem,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,lineHeight=30.sp)
                        q.options.forEachIndexed{i,text->
                            val letter="ABCD"[i].toString()
                            val tint=when{locked && letter==q.key->green;locked && letter==q.pick->Color(0xff993d42);!locked && selected==letter->purple;else->Color(0xff706b78)}
                            val mark=when{locked && letter==q.key->"✓ ";locked && letter==q.pick->"× ";else->""}
                            Surface(onClick={store.select(run,pos,letter);selected=letter},modifier=Modifier.fillMaxWidth().semantics { stateDescription=when { locked && letter==q.key -> "To‘g‘ri javob"; locked && letter==q.pick -> "Xato javob"; selected==letter -> "Tanlangan"; else -> "Tanlanmagan" } },enabled=!locked,shape=RoundedCornerShape(16.dp),border=BorderStroke(if(selected==letter||locked&&letter==q.key)2.dp else 1.dp,tint),color=Color.White){
                                Text("$mark$letter.   $text",Modifier.fillMaxWidth().padding(16.dp),color=tint,fontSize=17.sp)
                            }
                        }
                    }
                    "result","review"->{val errors=cards.filter{it.pick!=it.key}
                        if(page=="result"){Heading("Test yakunlandi");Panel{Heading("${cards.size-errors.size} / ${cards.size}");Text("${accuracy(cards)}% to‘g‘ri");Meter(cards.size-errors.size,cards.size)}
                            codes.forEachIndexed{i,c->val a=cards.filter{it.group==c};Text("${labels[i]}   ${a.count{it.pick==it.key}} / ${a.size}")}
                            if(errors.isNotEmpty())Action("Xatolarni ko‘rish (${errors.size})"){page="review"}
                            Action("Bosh sahifaga"){page="home"}
                        }else{Heading("Xatolar tahlili");errors.forEach{q->Panel{Text(q.stem);Text("Siz tanlagan: "+q.options["ABCD".indexOf(q.pick)]);Text("To‘g‘ri javob: "+q.options["ABCD".indexOf(q.key)],color=green)}}}
                    }
                    "history"->{Heading("Natijalar");if(history.isEmpty())Panel{Text("Hali yakunlangan test yo‘q");Action(if(active==null)"Birinchi testni boshlash" else "Testni davom ettirish"){if(active==null)page="choose" else resume(active.id)}}
                        history.forEach{a->OutlinedButton(onClick={run=a.id;page="result"},modifier=Modifier.fillMaxWidth()){Text("${a.day}    ·    ${a.size} savol    ·    ${accuracy(records.getValue(a.id))}%")}}
                    }
                    "stats","group"->{Heading(if(page=="stats")"Statistika" else labels[group])
                        if(all.isEmpty())Text("Statistika birinchi yakunlangan testdan so‘ng hisoblanadi.")
                        if(page=="stats"){Panel{Text("Yakunlangan testlar: ${history.size}");if(all.isNotEmpty())Text("Umumiy aniqlik: ${accuracy(all)}%")}
                            codes.forEachIndexed{i,c->val subset=all.filter{it.group==c};OutlinedButton(onClick={group=i;page="group"},modifier=Modifier.fillMaxWidth()){Text(labels[i]+"   ·   "+if(subset.isEmpty())"Hali ishlanmagan" else "${accuracy(subset)}%")}}
                        }else{val subset=all.filter{it.group==codes[group]};val total=totals.getValue(codes[group]);val seen=subset.distinctBy{it.id}.size
                            Panel{Text("Qamrov: $seen / $total savol");Meter(seen,total)}
                            subset.groupBy{it.topic}.forEach{(topic,a)->Panel{Text(topic);Text("${a.count{it.key==it.pick}} / ${a.size} javob to‘g‘ri");Meter(accuracy(a),100)}}
                        }
                    }
                }
            }
        }
        if(page=="test"){val displayedPos=pos;val q=cards[displayedPos];Column(Modifier.animateContentSize().padding(vertical=6.dp)){
            if(q.pick.isNotEmpty())Text(if(q.pick==q.key)"✓ To‘g‘ri javob" else "Xato javob. To‘g‘ri variant: ${q.key}",modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite },color=if(q.pick==q.key)green else purple)
            Action(if(q.pick=="")"Tekshirish" else if(pos==cards.lastIndex)"Natijani ko‘rish" else "Keyingi savol",q.pick!=""||selected!=""){
                if(q.pick==""){if(selected in listOf("A","B","C","D")) { store.answer(run,displayedPos,selected);refresh++ }}else if(displayedPos==cards.lastIndex){page="result"}else{pos=store.advance(run,displayedPos);selected=store.draft(run,pos)}
            }
        }}
        if(tabs.any{it.first==page})NavigationBar{tabs.forEachIndexed{i,(dest,label)->NavigationBarItem(selected=page==dest,onClick={page=dest},icon={Text(listOf("⌂","≡","▥")[i],fontSize=24.sp)},label={Text(label)})}}
    }
}
}
