package com.businessledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.businessledger.core.Rules
import com.businessledger.data.*
import java.math.BigDecimal
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { LedgerApp() } }
}

private val pages=listOf("Dashboard","Invoices","Contacts","Products","Purchases","Inventory","Payments","Orders","Expenses","Reports","Settings")
private val quick=listOf("Invoice","Purchase","Customer","Supplier","Product","Payment","Estimate","Sales order","Purchase order","Expense","Return","Delivery note")

@Composable private fun LedgerApp() {
    val context=androidx.compose.ui.platform.LocalContext.current
    val db=remember { LedgerDb(context) }
    var version by remember { mutableIntStateOf(0) }
    var page by remember { mutableStateOf("Dashboard") }
    var sheet by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val business=remember(version) { db.business() }
    val dark= androidx.compose.foundation.isSystemInDarkTheme()
    MaterialTheme(colorScheme=if(dark) darkColorScheme(primary=androidx.compose.ui.graphics.Color(0xFF9BCFC5)) else lightColorScheme(primary=androidx.compose.ui.graphics.Color(0xFF12685D),secondary=androidx.compose.ui.graphics.Color(0xFFB36A3B))) {
        if(business==null) { SetupScreen { name,currency,prefix,negative -> try { db.saveBusiness(Business(name,currency,prefix,negative)); version++ } catch(e:Exception) { message=e.message ?: "Could not save" } }; if(message.isNotBlank()) Text(message) }
        else {
            val wide=LocalConfiguration.current.screenWidthDp>=700
            Row {
                if(wide) Column(Modifier.width(150.dp).fillMaxHeight().verticalScroll(rememberScrollState())) { pages.forEach { item -> NavigationRailItem(selected=page==item,onClick={ page=item },icon={ Text(item.take(1)) },label={ Text(item) },alwaysShowLabel=true) } }
                Scaffold(modifier=Modifier.weight(1f),topBar={ Surface(shadowElevation=2.dp) { Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { Column { Text("Business Ledger",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold); Text(business.name,style=MaterialTheme.typography.bodySmall) }; Text(page,style=MaterialTheme.typography.titleMedium) } } },floatingActionButton={ FloatingActionButton(onClick={sheet="Quick create"}) { Text("+",style=MaterialTheme.typography.headlineMedium) } },bottomBar={ if(!wide) NavigationBar { listOf("Dashboard","Invoices","Products","Reports","More").forEach { item -> NavigationBarItem(selected=page==item,onClick={if(item=="More") sheet="Menu" else page=item},icon={ Text(if(item=="More") "☰" else item.take(1)) },label={Text(item)}) } } }) { pad ->
                    Column(Modifier.fillMaxSize().padding(pad)) {
                        key(version,page) {
                            when(page) {
                                "Dashboard" -> DashboardScreen(db,business,onPage={page=it})
                                "Invoices" -> DocumentList(db,business,"invoice",onOpen={sheet="Document:$it"})
                                "Purchases" -> DocumentList(db,business,"purchase",onOpen={sheet="Document:$it"})
                                "Contacts" -> ContactsScreen(db)
                                "Products" -> ProductsScreen(db,business,onOpen={sheet="Product:$it"})
                                "Inventory" -> InventoryScreen(db,business,onOpen={sheet="Product:$it"})
                                "Payments" -> PaymentsScreen(db,business)
                                "Expenses" -> ExpensesScreen(db,business)
                                "Reports" -> ReportsScreen(db,business,onPage={page=it})
                                "Settings" -> SettingsScreen(db,business,onSaved={version++},onError={message=it})
                                "Orders" -> DeferredScreen("Estimates, sales orders, purchase orders, returns and delivery notes are planned for a later release.")
                            }
                        }
                    }
                }
            }
            if(sheet.isNotBlank()) ModalBottomSheet(onDismissRequest={sheet=""}) {
                when {
                    sheet=="Menu" -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) { Text("Navigate",style=MaterialTheme.typography.titleLarge); pages.forEach { item -> ListItem(headlineContent={Text(item)},modifier=Modifier.clickable { page=item; sheet="" }) } }
                    sheet=="Quick create" -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) { Text("Quick create",style=MaterialTheme.typography.titleLarge); quick.forEach { item -> ListItem(headlineContent={Text(item)},supportingContent={ if(item in listOf("Estimate","Sales order","Purchase order","Return","Delivery note")) Text("Planned") else Text("Create locally") },modifier=Modifier.clickable { sheet=if(item in listOf("Estimate","Sales order","Purchase order","Return","Delivery note")) "Planned" else item }) } }
                    sheet=="Planned" -> DeferredScreen("This document workflow is planned and cannot be saved yet.")
                    sheet.startsWith("Document:") -> DocumentDetail(db,business,sheet.substringAfter(":"),onChange={version++;sheet=""},onError={message=it})
                    sheet.startsWith("Product:") -> ProductDetail(db,business,sheet.substringAfter(":"),onChange={version++;sheet=""},onError={message=it})
                    else -> EntryForm(sheet,db,business,onDone={version++;sheet=""},onError={message=it})
                }
                Spacer(Modifier.height(28.dp))
            }
            if(message.isNotBlank()) AlertDialog(onDismissRequest={message=""},confirmButton={TextButton(onClick={message=""}) {Text("OK")}},title={Text("Check this entry")},text={Text(message)})
        }
    }
}

@Composable private fun SetupScreen(onSave:(String,String,String,Boolean)->Unit) {
    var name by remember { mutableStateOf("") }; var currency by remember { mutableStateOf("PKR") }; var prefix by remember { mutableStateOf("INV-") }; var negative by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("Welcome to Business Ledger",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        Text("Set up your business. Everything you enter stays on this device; no account is needed.")
        Field("Business name",name){name=it}; Field("Currency code (ISO 4217)",currency){currency=it.uppercase()}; Field("Invoice prefix",prefix){prefix=it}
        Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(negative,{negative=it}); Text("Allow negative stock") }
        Button(onClick={onSave(name,currency,prefix,negative)},enabled=name.isNotBlank()) { Text("Start business") }
    }
}
@Composable private fun Field(label:String,value:String,onChange:(String)->Unit) { OutlinedTextField(value,onValueChange=onChange,label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=true) }
@Composable private fun Empty(text:String) { Box(Modifier.fillMaxWidth().padding(32.dp),contentAlignment=Alignment.Center) { Text(text,style=MaterialTheme.typography.bodyLarge) } }
@Composable private fun Money(value:BigDecimal,b:Business) { Text("${b.currency} ${value.setScale(b.scale,java.math.RoundingMode.HALF_UP).toPlainString()}") }
@Composable private fun Metric(label:String,value:BigDecimal,b:Business,onClick:()->Unit) { ElevatedCard(onClick=onClick,modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(label,style=MaterialTheme.typography.labelLarge); Money(value,b) } } }
@Composable private fun DashboardScreen(db:LedgerDb,b:Business,onPage:(String)->Unit) {
    var period by remember { mutableStateOf("Month") }; var customFrom by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) }; var customTo by remember { mutableStateOf(LocalDate.now().toString()) }
    val today=LocalDate.now()
    val start=try { if(period=="Custom") LocalDate.parse(customFrom) else when(period) {"Today"->today;"Week"->today.minusDays(today.dayOfWeek.value.toLong()-1L);"Quarter"->today.withMonth(((today.monthValue-1)/3)*3+1).withDayOfMonth(1);"Year"->today.withDayOfYear(1);else->today.withDayOfMonth(1)} } catch(e:Exception) { null }
    val end=try { if(period=="Custom") LocalDate.parse(customTo) else today } catch(e:Exception) { null }
    val summary=if(start!=null && end!=null && !start.isAfter(end)) db.summary(start,end) else null
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("At a glance",style=MaterialTheme.typography.headlineSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("Today","Week","Month","Quarter","Year","Custom").forEach { FilterChip(selected=period==it,onClick={period=it},label={Text(it)}) } }
        if(period=="Custom") { Field("From (YYYY-MM-DD)",customFrom){customFrom=it}; Field("To (YYYY-MM-DD)",customTo){customTo=it} }
        if(summary==null) { Text("Enter a valid date range"); return@Column }
        Text("Activity: $start to $end • ${b.currency}",style=MaterialTheme.typography.labelMedium)
        Metric("Sales",summary.sales,b){onPage("Invoices")}; Metric("Purchases",summary.purchases,b){onPage("Purchases")}; Metric("Received",summary.received,b){onPage("Payments")}; Metric("Paid to suppliers",summary.paid,b){onPage("Payments")}; Metric("Expenses",summary.expenses,b){onPage("Expenses")}
        Text("Balances as of $today • ${b.currency}",style=MaterialTheme.typography.titleMedium)
        Metric("Receivables",summary.receivables,b){onPage("Invoices")}; Metric("Payables",summary.payables,b){onPage("Purchases")}
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { AssistChip(onClick={onPage("Invoices")},label={Text("${summary.overdue} overdue")}); AssistChip(onClick={onPage("Inventory")},label={Text("${summary.lowStock} low stock")}) }
        Text("Profit unavailable until complete cost-of-goods tracking is implemented.",style=MaterialTheme.typography.bodySmall)
        Text("Sync: ${db.pendingChanges()} local changes. Cloud sync requires sign-in and is not available in this MVP.",style=MaterialTheme.typography.bodySmall)
        Text("Recent activity",style=MaterialTheme.typography.titleMedium)
        db.documents().take(5).forEach { d -> ListItem(headlineContent={Text("${d.number ?: "Draft"} · ${d.contactName}")},supportingContent={Text("${d.kind} • ${d.date} • ${d.status}")},trailingContent={Text("${b.currency} ${d.total}")},modifier=Modifier.clickable { onPage(if(d.kind=="invoice") "Invoices" else "Purchases") }) }
    }
}
@Composable private fun DocumentList(db:LedgerDb,b:Business,kind:String,onOpen:(String)->Unit) {
    var search by remember { mutableStateOf("") }; var status by remember { mutableStateOf("All") }
    val data=db.documents(kind).filter { (status=="All" || it.status==status) && (it.contactName.contains(search,true) || (it.number ?: "Draft").contains(search,true)) }
    Column(Modifier.fillMaxSize().padding(16.dp)) { Field("Search ${kind}s",search){search=it}; Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("All","draft",if(kind=="invoice") "issued" else "received","partially paid","paid").forEach { FilterChip(selected=status==it,onClick={status=it},label={Text(it)}) } }; if(data.isEmpty()) Empty("No ${kind}s found. Use + to create one.") else LazyColumn { items(data) { d -> ListItem(headlineContent={Text("${d.number ?: "Draft"} · ${d.contactName}")},supportingContent={Text("${d.date} • ${d.status} • due ${d.dueDate}")},trailingContent={Text("${b.currency} ${d.total}")},modifier=Modifier.clickable { onOpen(d.id) }); HorizontalDivider() } } }
}
@Composable private fun ContactsScreen(db:LedgerDb) { var search by remember { mutableStateOf("") }; var kind by remember { mutableStateOf("All") }; val data=db.contacts().filter { (kind=="All"||it.kind==kind) && (it.name.contains(search,true)||it.phone.contains(search,true)) }; Column(Modifier.fillMaxSize().padding(16.dp)) { Field("Search contacts",search){search=it}; Row { listOf("All","customer","supplier").forEach { FilterChip(kind==it,{kind=it},label={Text(it)}) } }; if(data.isEmpty()) Empty("No contacts found. Use + to add one.") else LazyColumn { items(data) { c -> ListItem(headlineContent={Text(c.name)},supportingContent={Text("${c.kind} · ${c.phone} · ${c.email}")}); HorizontalDivider() } } } }
@Composable private fun ProductsScreen(db:LedgerDb,b:Business,onOpen:(String)->Unit) { var search by remember { mutableStateOf("") }; var category by remember { mutableStateOf("All") }; val catalog=db.products(); val cats=listOf("All")+catalog.map { it.category }.distinct(); val data=catalog.filter { (category=="All"||it.category==category) && it.name.contains(search,true) }; Column(Modifier.fillMaxSize().padding(16.dp)) { Field("Search products",search){search=it}; Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) { cats.forEach { FilterChip(category==it,{category=it},label={Text(it.ifBlank { "Uncategorized" })}) } }; if(data.isEmpty()) Empty("No products found. Use + to add one.") else LazyColumn { items(data) { p -> ListItem(headlineContent={Text(p.name)},supportingContent={Text("${p.category} · ${if(p.stock) "On hand ${db.onHand(p.id)}" else "Service"}")},trailingContent={Text("${b.currency} ${p.price}")},modifier=Modifier.clickable { onOpen(p.id) }); HorizontalDivider() } } } }
@Composable private fun InventoryScreen(db:LedgerDb,b:Business,onOpen:(String)->Unit) { var search by remember { mutableStateOf("") }; val data=db.products().filter { it.stock && it.name.contains(search,true) }; Column(Modifier.fillMaxSize().padding(16.dp)) { Field("Search inventory",search){search=it}; Text("On hand and available are equal until reservations are supported.",style=MaterialTheme.typography.bodySmall); LazyColumn { items(data) { p -> val onHand=db.onHand(p.id); ListItem(headlineContent={Text(p.name)},supportingContent={Text("On hand $onHand · Low stock at ${p.threshold}")},trailingContent={Text(if(onHand<=BigDecimal(p.threshold)) "Low" else "OK")},modifier=Modifier.clickable { onOpen(p.id) }) } } } }
@Composable private fun PaymentsScreen(db:LedgerDb,b:Business) { var search by remember { mutableStateOf("") }; val docs=db.documents().associateBy { it.id }; val data=db.payments().filter { (docs[it.documentId]?.contactName ?: "").contains(search,true) }; Column(Modifier.fillMaxSize().padding(16.dp)) { Field("Search payments",search){search=it}; if(data.isEmpty()) Empty("No payments found. Use + to record one.") else LazyColumn { items(data) { p -> ListItem(headlineContent={Text(docs[p.documentId]?.contactName ?: "Document")},supportingContent={Text("${p.date} · ${p.method} · ${docs[p.documentId]?.number}")},trailingContent={Text("${b.currency} ${p.amount}")}) } } } }
@Composable private fun ExpensesScreen(db:LedgerDb,b:Business) { var search by remember { mutableStateOf("") }; val data=db.expenses().filter { it.category.contains(search,true)||it.payee.contains(search,true) }; Column(Modifier.fillMaxSize().padding(16.dp)) { Field("Search expenses",search){search=it}; if(data.isEmpty()) Empty("No expenses found. Use + to add one.") else LazyColumn { items(data) { e -> ListItem(headlineContent={Text(e.category)},supportingContent={Text("${e.date} · ${e.payee}")},trailingContent={Text("${b.currency} ${e.amount}")}) } } } }
@Composable private fun ReportsScreen(db:LedgerDb,b:Business,onPage:(String)->Unit) { var from by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) }; var to by remember { mutableStateOf(LocalDate.now().toString()) }; val dates=try { LocalDate.parse(from) to LocalDate.parse(to) } catch(e:Exception) { null }; val summary=dates?.takeIf { !it.first.isAfter(it.second) }?.let { db.summary(it.first,it.second) }; Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) { Text("Reports",style=MaterialTheme.typography.headlineSmall); Field("From (YYYY-MM-DD)",from){from=it}; Field("To (YYYY-MM-DD)",to){to=it}; if(summary==null) Text("Enter valid dates") else { Text("Accrual documents and cash payments from $from to $to · ${b.currency}"); Metric("Issued sales",summary.sales,b){onPage("Invoices")}; Metric("Received purchases",summary.purchases,b){onPage("Purchases")}; Metric("Cash received",summary.received,b){onPage("Payments")}; Metric("Cash paid",summary.paid,b){onPage("Payments")}; Metric("Expenses",summary.expenses,b){onPage("Expenses")}; Text("As of ${LocalDate.now()}: receivables ${summary.receivables}, payables ${summary.payables}. Tap a metric to inspect source records."); Text("Profit and valuation are unavailable until a costing method and complete stock history are implemented.") } } }
@Composable private fun SettingsScreen(db:LedgerDb,b:Business,onSaved:()->Unit,onError:(String)->Unit) { var name by remember { mutableStateOf(b.name) }; var currency by remember { mutableStateOf(b.currency) }; var prefix by remember { mutableStateOf(b.prefix) }; var negative by remember { mutableStateOf(b.allowNegative) }; Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) { Text("Business settings",style=MaterialTheme.typography.headlineSmall); Field("Business name",name){name=it}; Field("Currency",currency){currency=it.uppercase()}; Field("Invoice prefix",prefix){prefix=it}; Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(negative,{negative=it}); Text("Allow negative stock") }; Button(onClick={try { db.saveBusiness(Business(name,currency,prefix,negative,b.scale)); onSaved() } catch(e:Exception){onError(e.message ?: "Invalid settings")} }){Text("Save settings")}; Text("All data is local. Cloud sync, roles, app lock and encrypted backup are planned.") } }
@Composable private fun DeferredScreen(text:String) { Empty(text) }

@Composable private fun EntryForm(type:String,db:LedgerDb,b:Business,onDone:()->Unit,onError:(String)->Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("New $type",style=MaterialTheme.typography.headlineSmall)
        when(type) {
            "Customer","Supplier" -> ContactForm(type,db,onDone,onError)
            "Product" -> ProductForm(db,onDone,onError)
            "Invoice","Purchase" -> DocumentForm(type,db,b,onDone,onError)
            "Payment" -> PaymentForm(db,b,onDone,onError)
            "Expense" -> ExpenseForm(db,onDone,onError)
        }
    }
}
@Composable private fun ContactForm(type:String,db:LedgerDb,onDone:()->Unit,onError:(String)->Unit) {
    var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }
    Field("Name",name){name=it}; Field("Phone",phone){phone=it}; Field("Email",email){email=it}
    val matches=db.contacts().filter { it.name.equals(name,true) || (phone.isNotBlank()&&it.phone==phone) }
    if(name.isNotBlank() && matches.isNotEmpty()) Text("Possible duplicate: ${matches.joinToString { it.name }}",color=MaterialTheme.colorScheme.error)
    Button(onClick={try { db.addContact(type.lowercase(),name,phone,email); onDone() } catch(e:Exception){onError(e.message ?: "Could not save contact")} },enabled=name.isNotBlank()) { Text("Save $type") }
}
@Composable private fun ProductForm(db:LedgerDb,onDone:()->Unit,onError:(String)->Unit) {
    var name by remember { mutableStateOf("") }; var category by remember { mutableStateOf("") }; var price by remember { mutableStateOf("") }; var cost by remember { mutableStateOf("") }; var threshold by remember { mutableStateOf("0") }; var stock by remember { mutableStateOf(true) }
    Field("Name",name){name=it}; Field("Category",category){category=it}; Field("Selling price",price){price=it}; Field("Purchase cost",cost){cost=it}
    Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(stock,{stock=it}); Text("Track stock") }
    if(stock) Field("Low stock threshold",threshold){threshold=it}
    Button(onClick={try { db.addProduct(name,category,price,cost,stock,threshold); onDone() } catch(e:Exception){onError(e.message ?: "Could not save product")} },enabled=name.isNotBlank()&&price.isNotBlank()&&cost.isNotBlank()) { Text("Save product") }
}
private data class EditableLine(val productId:String,val quantity:String,val price:String,val discount:String,val tax:String)
@Composable private fun DocumentForm(type:String,db:LedgerDb,b:Business,onDone:()->Unit,onError:(String)->Unit) {
    val kind=type.lowercase(); val contacts=db.contacts(if(kind=="invoice") "customer" else "supplier"); val products=db.products()
    var contactId by remember { mutableStateOf("") }; var due by remember { mutableStateOf(LocalDate.now().plusDays(14).toString()) }
    var lines by remember { mutableStateOf(listOf<EditableLine>()) }; var picker by remember { mutableStateOf(false) }; var search by remember { mutableStateOf("") }; var category by remember { mutableStateOf("All") }
    Text(if(kind=="invoice") "Customer" else "Supplier",style=MaterialTheme.typography.titleMedium)
    if(contacts.isEmpty()) Text("Create a ${if(kind=="invoice") "customer" else "supplier"} first using +.")
    contacts.forEach { c -> Row(verticalAlignment=Alignment.CenterVertically) { RadioButton(contactId==c.id,onClick={contactId=c.id}); Text(c.name,modifier=Modifier.clickable { contactId=c.id }) } }
    HorizontalDivider(); Text("Items",style=MaterialTheme.typography.titleMedium)
    lines.forEachIndexed { index,line ->
        val p=products.firstOrNull { it.id==line.productId }
        Text(p?.name ?: "Product",fontWeight=FontWeight.SemiBold)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(line.quantity,{ value -> lines=lines.toMutableList().also { it[index]=line.copy(quantity=value) } },label={Text("Qty")},modifier=Modifier.weight(1f),singleLine=true)
            OutlinedTextField(line.price,{ value -> lines=lines.toMutableList().also { it[index]=line.copy(price=value) } },label={Text("Price")},modifier=Modifier.weight(1f),singleLine=true)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(line.discount,{ value -> lines=lines.toMutableList().also { it[index]=line.copy(discount=value) } },label={Text("Discount")},modifier=Modifier.weight(1f),singleLine=true)
            OutlinedTextField(line.tax,{ value -> lines=lines.toMutableList().also { it[index]=line.copy(tax=value) } },label={Text("Tax %")},modifier=Modifier.weight(1f),singleLine=true)
        }
        TextButton(onClick={lines=lines.toMutableList().also { it.removeAt(index) }}) {Text("Remove item")}
    }
    OutlinedButton(onClick={picker=!picker}) {Text(if(picker) "Close product picker" else "Add product or service")}
    if(picker) {
        Field("Search catalog",search){search=it}
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) { (listOf("All")+products.map { it.category }.distinct()).forEach { cat -> FilterChip(category==cat,{category=cat},label={Text(cat.ifBlank { "Uncategorized" })}) } }
        products.filter { (category=="All"||it.category==category) && it.name.contains(search,true) }.forEach { p -> ListItem(headlineContent={Text(p.name)},supportingContent={Text("${p.category} · ${b.currency} ${if(kind=="invoice") p.price else p.cost}")},modifier=Modifier.clickable { lines=lines+EditableLine(p.id,"1",if(kind=="invoice") p.price else p.cost,"0","0"); picker=false }) }
        if(products.isEmpty()) Text("Create a product first using +.")
    }
    val total=try { lines.fold(BigDecimal.ZERO) { acc,l -> acc.add(Rules.line(Rules.decimal(l.quantity),Rules.money(l.price,b.scale),Rules.money(l.discount,b.scale),Rules.decimal(l.tax),b.scale).total) } } catch(e:Exception) { null }
    Text("${lines.size} items · ${b.currency} ${total?.toPlainString() ?: "Check amounts"}",style=MaterialTheme.typography.titleMedium)
    Field("Due date (YYYY-MM-DD)",due){due=it}
    val save:(Boolean)->Unit={ final -> try { db.saveDocument(kind,contactId,lines.map { EntryLine(it.productId,it.quantity,it.price,it.discount,it.tax) },final,due); onDone() } catch(e:Exception) {onError(e.message ?: "Could not save document")} }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick={save(false)},enabled=contactId.isNotBlank()&&lines.isNotEmpty()&&total!=null) {Text("Save draft")}; Button(onClick={save(true)},enabled=contactId.isNotBlank()&&lines.isNotEmpty()&&total!=null) {Text(if(kind=="invoice") "Issue invoice" else "Receive purchase") } }
}
@Composable private fun PaymentForm(db:LedgerDb,b:Business,onDone:()->Unit,onError:(String)->Unit) {
    val docs=db.documents().filter { it.status!="draft" && BigDecimal(it.total)>BigDecimal(it.paid) }
    var chosen by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }; var method by remember { mutableStateOf("Cash") }
    if(docs.isEmpty()) Text("No open invoices or purchases.")
    docs.forEach { d -> Row(verticalAlignment=Alignment.CenterVertically) { RadioButton(chosen==d.id,onClick={chosen=d.id}); Column(Modifier.clickable { chosen=d.id }) { Text("${d.number} · ${d.contactName}"); Text("Open ${b.currency} ${BigDecimal(d.total).subtract(BigDecimal(d.paid))}") } } }
    Field("Amount",amount){amount=it}; Text("Method",style=MaterialTheme.typography.titleMedium)
    Row { listOf("Cash","Bank","Card").forEach { FilterChip(method==it,{method=it},label={Text(it)}) } }
    Button(onClick={try { db.addPayment(chosen,amount,method); onDone() } catch(e:Exception){onError(e.message ?: "Could not record payment")} },enabled=chosen.isNotBlank()&&amount.isNotBlank()) {Text("Record payment")}
}
@Composable private fun ExpenseForm(db:LedgerDb,onDone:()->Unit,onError:(String)->Unit) { var category by remember { mutableStateOf("") }; var payee by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }; Field("Category",category){category=it}; Field("Payee",payee){payee=it}; Field("Amount",amount){amount=it}; Button(onClick={try {db.addExpense(category,payee,amount);onDone()}catch(e:Exception){onError(e.message ?: "Could not save expense")}},enabled=category.isNotBlank()&&amount.isNotBlank()) {Text("Save expense")} }
@Composable private fun DocumentDetail(db:LedgerDb,b:Business,id:String,onChange:()->Unit,onError:(String)->Unit) { val d=db.documents().firstOrNull { it.id==id }; if(d==null) {Empty("Document unavailable");return}; Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) { Text("${d.kind.replaceFirstChar { it.uppercase() }} ${d.number ?: "draft"}",style=MaterialTheme.typography.headlineSmall); Text("${d.contactName} · ${d.date} · due ${d.dueDate}"); Text("Status: ${d.status}"); Text("Items",style=MaterialTheme.typography.titleMedium); db.documentLines(id).forEach { line -> Text("${line.productName} · ${line.quantity} × ${b.currency} ${line.unitPrice} · net ${line.net} + tax ${line.tax}") }; Text("Total ${b.currency} ${d.total}"); Text("Paid ${b.currency} ${d.paid}"); if(d.status=="draft") Button(onClick={try {db.finalizeDraft(id);onChange()}catch(e:Exception){onError(e.message ?: "Could not finalize")}}) {Text(if(d.kind=="invoice") "Issue invoice" else "Receive purchase")}; Text("Issued records are preserved; voids and returns will require a dedicated audited workflow.") } }
@Composable private fun ProductDetail(db:LedgerDb,b:Business,id:String,onChange:()->Unit,onError:(String)->Unit) { val p=db.products().firstOrNull { it.id==id }; if(p==null) {Empty("Product unavailable");return}; var delta by remember { mutableStateOf("") }; var reason by remember { mutableStateOf("") }; Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) { Text(p.name,style=MaterialTheme.typography.headlineSmall); Text("${p.category} · ${b.currency} ${p.price}"); if(p.stock) { Text("On hand: ${db.onHand(id)}"); Field("Adjustment (+ or − quantity)",delta){delta=it}; Field("Reason",reason){reason=it}; Button(onClick={try {db.adjustStock(id,delta,reason);onChange()}catch(e:Exception){onError(e.message ?: "Could not adjust stock")}},enabled=delta.isNotBlank()&&reason.isNotBlank()) {Text("Record adjustment")}; Text("Movement history",style=MaterialTheme.typography.titleMedium); db.movements(id).forEach { Text("${it.date} · ${it.quantity} · ${it.reason}") } } else Text("Service: stock is not tracked") } }
